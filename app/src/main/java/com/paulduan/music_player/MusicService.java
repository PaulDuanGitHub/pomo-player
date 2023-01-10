package com.paulduan.music_player;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.media.MediaPlayer;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

public class MusicService extends Service {
    private long intervalStartTime = 0;
    private long intervalCheckPoint = 0;
    private boolean isIntervalPaused = false;
    private boolean isInterval = false;
    private String currentFileName;
    private boolean isInit = true;
    public MusicService() {
    }

    private MyReceiver serviceReceiver;
    private Thread processThread;
    private MediaPlayer mPlayer;
    // Not playing: 0x11, playing: 0x12, paused: 0x13
    public int status = 0x11;
    // Current
    public int current = 0;

    private PowerManager powerManager;
    private WakeLock wakeLock;
    private SharedPreferences sharedPreferences;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        // Init and add Wake Lock
        /**
         * https://stackoverflow.com/questions/39954822/battery-optimizations-wakelocks-on-huawei-emui-4-0
         * Please read more for Huawei deceive
         */
        powerManager = (PowerManager) MusicService.this.getSystemService(Context.POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                "PomoPlayer::musicService"); // Could set to LocationManagerService
        wakeLock.acquire();

        // Init SharedPreferences
        sharedPreferences = MusicService.this.getSharedPreferences("SP", MODE_PRIVATE);

        // Create BroadcastReceiver
        serviceReceiver = new MyReceiver();
        // Create IntentFilter
        IntentFilter filter = new IntentFilter();
        filter.addAction(MainActivity.CTL_ACTION);
        filter.addAction(MainActivity.UPDATE_LIST);
        filter.addAction(MainActivity.SAVE_DATA);
        registerReceiver(serviceReceiver, filter);

        mPlayer = new MediaPlayer();
        // Bind onCompletionLister for MediaPlayer
        mPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
//                Log.d("MusicService", "歌曲播放完毕");
                current++;
                if (current >= MainActivity.pieceList.size()) {
                    current = 0;
                }
                if (MainActivity.pieceList.isEmpty()) {
                    return;
                }

                Intent sendIntent = new Intent(MainActivity.UPDATE_ACTION);
                sendIntent.putExtra("current", current);
                sendBroadcast(sendIntent);
                prepareAndPlay(MainActivity.pieceList.get(current).getFileName(), "OnCompletion");
            }
        });

        /**
         * For MediaPlayer if get wrong, it will call onCompletion if onError return false;
         */
        mPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener() {
            @Override
            public boolean onError(MediaPlayer mp, int what, int extra) {
                Intent sendIntent = new Intent(MainActivity.UPDATE_ACTION);
                sendIntent.putExtra("currentTime", -1);
                sendIntent.putExtra("totalTime", -1);
                sendBroadcast(sendIntent);
                return true;
            }
        });

        // Load current and progress from SharedPreferences
        current = sharedPreferences.getInt("current",0);
        isInterval = sharedPreferences.getBoolean("isInterval", false);
        int savedProgress = sharedPreferences.getInt("progress",-1);
        if (isInterval && savedProgress != -1) { // Is interval
            isIntervalPaused = true;
            intervalStartTime = (int) (System.currentTimeMillis() - savedProgress);
            intervalCheckPoint = System.currentTimeMillis() - intervalStartTime;
            status = 0x13;
            Intent sendIntent = new Intent(MainActivity.UPDATE_ACTION);
            sendIntent.putExtra("current", current);
            sendBroadcast(sendIntent);
        } else if (!isInterval && savedProgress != -1) {
            String savedFileName = MainActivity.pieceList.get(current).getFileName();
            String musicPath = new File(getResources().getString(R.string.default_path), savedFileName).getPath();
            Log.i("test",musicPath);
            Log.i("test",Integer.toString(savedProgress));
            try {
                mPlayer.setOnPreparedListener(new MyOnPreparedListener(savedProgress));
                mPlayer.setDataSource(musicPath);
                mPlayer.prepareAsync();
            } catch (IOException e) {
                e.printStackTrace();
            }

            status = 0x13;
        }

        // TimeTask is not as flexible as Thread with while loop.
        processThread = new Thread(() -> {
            while (true) {
                try {
                    if (status == 0x12 || isInterval) { // Is playing or isInterval
                        if (isInterval) { // Is interval
                            if (isIntervalPaused) { // Is interval and paused
                                Intent sendIntent = new Intent(MainActivity.UPDATE_ACTION);
                                // Keeping send the check point to achieve paused
                                sendIntent.putExtra("currentTime", (int) intervalCheckPoint);
                                sendIntent.putExtra("totalTime", 1500000);
                                sendBroadcast(sendIntent);
                            } else { // Is interval and not paused
                                if (intervalStartTime == 0) { // first loop
                                    intervalStartTime = System.currentTimeMillis();
//                                    Log.i("test", "Init interval");
                                    Intent sendIntent = new Intent(MainActivity.UPDATE_ACTION);
                                    // Progress is current time - start time
                                    int progress = (int) (System.currentTimeMillis() - intervalStartTime);
                                    sendIntent.putExtra("currentTime", progress);
                                    // total time is 25 mins
                                    sendIntent.putExtra("totalTime", 1500000);
                                    sendBroadcast(sendIntent);
                                } else { // not first loop
                                    // Sleep for 500 mills
                                    Thread.sleep(500);
                                    if (!isInterval) { // After wake up, if have change piece
                                        continue; // Next loop
                                    }
                                    // After wake up, still interval
                                    Intent sendIntent1 = new Intent(MainActivity.UPDATE_ACTION);
                                    // 当前进度
                                    int progress = (int) (System.currentTimeMillis() - intervalStartTime);
//                                    Log.i("test", "Interval: " + Integer.toString(progress));
                                    if (progress >= 1500000) { // Finish 25 mins
                                        if (current + 1 >= MainActivity.pieceList.size()) {
                                            current = 0;
                                        } else {
                                            current++;
                                        }
                                        isInterval = false;
                                        intervalStartTime = 0;
                                        prepareAndPlay(MainActivity.pieceList.get(current).getFileName(), "Interval done");
                                        status = 0x12;
                                        Intent sendIntent = new Intent(MainActivity.UPDATE_ACTION);
                                        sendIntent.putExtra("current", current);
                                        sendBroadcast(sendIntent);
                                    }
                                    // Not finish yet
                                    sendIntent1.putExtra("currentTime", progress);
                                    sendIntent1.putExtra("totalTime", 1500000);
                                    sendBroadcast(sendIntent1);
                                }
                            }
                        } else { // Is not a interval
                            Thread.sleep(500);
                            // getDuration can not be called in MediaPlay state 0 and 4
                            if (!mPlayer.isPlaying()) {
                                continue;
                            }
                            Intent sendIntent1 = new Intent(MainActivity.UPDATE_ACTION);
                            sendIntent1.putExtra("currentTime", mPlayer.getCurrentPosition());
//                            Log.i("test",Integer.toString(mPlayer.getCurrentPosition()));
                            sendIntent1.putExtra("totalTime", mPlayer.getDuration());
                            sendBroadcast(sendIntent1);
                        }
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });
        processThread.start();


    }

    @Override
    public void onDestroy() {
        unregisterReceiver(serviceReceiver);
        wakeLock.release();
        Log.i("test","awsl-service");
        mPlayer.release();
        super.onDestroy();
    }
    private class MyReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(final Context context, Intent intent) {
            if (intent.getAction() == MainActivity.SAVE_DATA) {
                // Save current and progress on destroy
                Editor editor = sharedPreferences.edit();
                editor.putInt("current", current);
                if(isInterval) { // Is interval
                    editor.putInt("progress", (int) (System.currentTimeMillis() - intervalStartTime));
                    editor.putBoolean("isInterval", true);
                }else { // Not interval
                    editor.putInt("progress", mPlayer.getCurrentPosition());
                    editor.putBoolean("isInterval", false);
                }
                editor.commit();
                Log.i("test","Data Saved");
            }else if (intent.getAction() == MainActivity.UPDATE_LIST) { // Update list action
                if (MainActivity.pieceList.isEmpty()) { // Is Empty after update
                    mPlayer.reset();
                    currentFileName = null;
                    current = 0;
                    status = 0x11;
                    isInterval = false;
                    intervalStartTime = 0;
                    isIntervalPaused = false;
                    intervalCheckPoint = 0;
                    Intent sendIntent = new Intent(MainActivity.UPDATE_ACTION);
                    sendIntent.putExtra("update", status);
                    sendIntent.putExtra("current", current);
                    sendBroadcast(sendIntent);
                } else { // Not Empty after update
                    boolean exist = false;
                    for (MusicPiece piece : MainActivity.pieceList) {
                        if (piece.getFileName() == currentFileName) {
                            exist = true;
                        }
                    }
                    if (currentFileName != null && !exist) { // After update the one playing is gone
                        isInterval = false;
                        intervalStartTime = 0;
                        isIntervalPaused = false;
                        intervalCheckPoint = 0;
                        if (current >= MainActivity.pieceList.size()) { // After update the one playing is gone and current is out of bound
                            current = 0;
                            if (status == 0x12) { // Is playing
                                mPlayer.reset();
                                prepareAndPlay(MainActivity.pieceList.get(current).getFileName(), "Piece removed");
                            }
                            if (status == 0x13) { // Is paused
                                mPlayer.reset();
                                status = 0x11;
                            }
                            Intent sendIntent = new Intent(MainActivity.UPDATE_ACTION);
                            sendIntent.putExtra("update", status);
                            sendIntent.putExtra("current", current);
                            sendBroadcast(sendIntent);
                        } else { // After update, the one playing is gone and the index was not out of bound
                            if (status == 0x12) {
                                mPlayer.reset();
                                prepareAndPlay(MainActivity.pieceList.get(current).getFileName(), "Piece removed");
                            }
                            if (status == 0x13) {
                                mPlayer.reset();
                                status = 0x11;
                            }
                            Intent sendIntent = new Intent(MainActivity.UPDATE_ACTION);
                            sendIntent.putExtra("update", status);
                            sendIntent.putExtra("current", current);
                            sendBroadcast(sendIntent);
                        }
                    } else { // The one removed is not the one playing
                        boolean modified = false;
                        if (status == 0x12 || status == 0x13) {
                            for (int i = 0; i < MainActivity.pieceList.size(); i++) {
                                if (MainActivity.pieceList.get(i).getFileName() == currentFileName) {
                                    modified = current == i ? false : true;
                                    current = i;
                                    break;
                                }
                            }
                            if (modified) { // Only send intent if the current is modified
                                Intent sendIntent = new Intent(MainActivity.UPDATE_ACTION);
                                sendIntent.putExtra("update", status);
                                sendIntent.putExtra("current", current);
                                sendBroadcast(sendIntent);
                            }
                        }

                    }
                }

            } else if (intent.getAction() == MainActivity.CTL_ACTION) { // Action for control
                int control = intent.getIntExtra("control", -1);
                int progress = intent.getIntExtra("seekBarControl", -1);
                if (progress != -1) { // Set progress
                    if (isInterval) { // Interval
//                        Log.i("test", "startTime: " + Long.toString(intervalStartTime) + "progress: " + Integer.toString(progress));
                        intervalStartTime = System.currentTimeMillis() - progress;
                    } else { // Not interval
                        mPlayer.seekTo(progress);
                    }
                }
                switch (control) {
                    // Play/Pause button
                    case 1:
                        // Not Play
                        if (status == 0x11) {
                            // Play
                            try {
                                if (isInterval) {
                                    isIntervalPaused = false;
                                    intervalStartTime = 0;
                                    intervalCheckPoint = 0;
                                }
                                prepareAndPlay(MainActivity.pieceList.get(current).getFileName(), "Play first piece");
                            } catch (IndexOutOfBoundsException e) {
                                Toast.makeText(getApplicationContext(), "No Music", Toast.LENGTH_SHORT).show();
                                return;
                            }
//                            Log.i("test", "原来处于没有播放状态");
//                            Log.i("test", MainActivity.pieceList.get(current).getFileName());
                            status = 0x12;
                        }
                        // Playing
                        else if ((status == 0x12)) {
                            if (isInterval) { // Is interval
                                isIntervalPaused = true;
                                intervalCheckPoint = System.currentTimeMillis() - intervalStartTime;
                            } else {
                                // Pause
                                mPlayer.pause();
                            }
//                        Log.i("test","PAUSED: " + Integer.toString(mPlayer.getCurrentPosition()));
                            // Set state
                            status = 0x13;
                        }
                        // Paused
                        else if ((status == 0x13)) {
                            // Play
                            if (isInterval) { // Is interval
                                isIntervalPaused = false;
                                intervalStartTime = System.currentTimeMillis() - intervalCheckPoint;
                                intervalCheckPoint = 0;
                            } else {
                                mPlayer.start();
                            }
                            // Set state
                            status = 0x12;
                        }
                        break;
                    // Stop button
                    case 2:
                        if ((status == 0x12 || status == 0x13)) {
                            if (isInterval) {
                                isIntervalPaused = true;
                                intervalCheckPoint = System.currentTimeMillis() - intervalStartTime;
                            } else {
                                mPlayer.stop();
                            }
                            status = 0x11;
                        }
                        break;
                    // Prev
                    case 3:
                        if ((status == 0x12 || status == 0x13)) {
                            if (!isInterval) {
                                mPlayer.stop();
                            }
                            if (current - 1 < 0) {
                                current = MainActivity.pieceList.size() - 1;
                            } else {
                                current--;
                            }
                            prepareAndPlay(MainActivity.pieceList.get(current).getFileName(), "SYS");
                            status = 0x12;
                        }
                        break;
                    // Next
                    case 4:
                        if ((status == 0x12 || status == 0x13)) {
                            if (!isInterval) {
                                mPlayer.stop();
                            }
                            if (current + 1 >= MainActivity.pieceList.size()) {
                                current = 0;
                            } else {
                                current++;
                            }
                            prepareAndPlay(MainActivity.pieceList.get(current).getFileName(), "XYS");
                            status = 0x12;
                        }
                        break;
                }

                Intent sendIntent = new Intent(MainActivity.UPDATE_ACTION);
                sendIntent.putExtra("update", status);
                sendIntent.putExtra("current", current);
                sendBroadcast(sendIntent);
            }

        }
    }

    private class MyOnPreparedListener implements MediaPlayer.OnPreparedListener {
        int progress;
        public MyOnPreparedListener(int progress) {
            this.progress = progress;
        }

        @Override
        public void onPrepared(MediaPlayer mp) {
            mPlayer.start();
            if (isInit) {
                mPlayer.pause();
                mPlayer.seekTo(progress);
                Intent sendIntent = new Intent(MainActivity.UPDATE_ACTION);
                sendIntent.putExtra("current", current);
                sendIntent.putExtra("currentTime", mPlayer.getCurrentPosition());
                sendIntent.putExtra("totalTime", mPlayer.getDuration());
                sendBroadcast(sendIntent);
                isInit = false;
            }
        }
    }

    private void prepareAndPlay(String music, String who) {
        try {
            if (music.startsWith("INTERVAL") && music.length() == 36 + 8) {
                isInterval = true;
                currentFileName = MainActivity.pieceList.get(current).getFileName();
                Log.i("test", who);
                return;
            }
            isInterval = false;
            intervalStartTime = 0;
            isIntervalPaused = false;
            intervalCheckPoint = 0;

            mPlayer.reset();
//            Log.i("test",inputStream);

            String musicPath = new File(getResources().getString(R.string.default_path), music).getPath();
            currentFileName = MainActivity.pieceList.get(current).getFileName();
//            Log.i("test", "current File Name: " + currentFileName);
//            Log.i("test", musicPath);
//            Log.i("test", who);
            mPlayer.setDataSource(musicPath);

//            Log.i("test", musicPath);
            mPlayer.prepareAsync();
        } catch (IOException e) {
            e.printStackTrace();
//            Log.i("test", e.toString());

        }
    }

}
