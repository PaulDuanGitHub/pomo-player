package com.paulduan.music_player;

import android.Manifest;
import android.content.DialogInterface;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Bundle;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.provider.DocumentsContract;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.widget.Toast;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;
import java.util.Date;
import java.util.UUID;

public class MainActivity extends AppCompatActivity implements View.OnClickListener, OnStartDragListener {
    // For choose file
    private ActivityResultLauncher<Intent> mStartForResult = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
            new ActivityResultCallback<ActivityResult>() {
                @Override
                public void onActivityResult(ActivityResult result) {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        Intent intent = result.getData();
                        Uri uri = intent.getData();
                        // Handle the Intent
                        if (null != intent) { // Checking empty selection
                            if (null != intent.getClipData()) { // Checking multiple selection or not
                                for (int i = 0; i < intent.getClipData().getItemCount(); i++) {
                                    uri = intent.getClipData().getItemAt(i).getUri();
                                    addPiece(uri);
                                }
                            } else {
                                uri = intent.getData();
                                addPiece(uri);

                            }
                        }
                        adapter.notifyDataSetChanged();
                        // Save to a json file
                        writeJsonFile();
                    }
                }
            });
    // For choose folder
    private ActivityResultLauncher<Intent> mStartForResult2 = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
            new ActivityResultCallback<ActivityResult>() {
                @Override
                public void onActivityResult(ActivityResult result) {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        Intent intent = result.getData();
                        Uri uri = intent.getData();
                        // Handle the Intent
                        if (null != intent) { // Checking empty selection
                            Uri docUri = DocumentsContract.buildDocumentUriUsingTree(uri,
                                    DocumentsContract.getTreeDocumentId(uri));
                            String path = ASFUriHelper.getPath(MainActivity.this, docUri);
                            Log.i("test", path);
                        }
                    }
                }
            });

    private Intent mIntent;
    private RecyclerView musicList;
    private MusicAdapter adapter;
    private ItemTouchHelper helper;
    public static ArrayList<MusicPiece> pieceList = new ArrayList<>();
    private boolean isFABOpen = false;

    private TextView currentTime, totalTime;
    private MaterialButton play, stop, previous, next;
    private SeekBar seekBar;
    private FloatingActionButton fab;
    private LinearLayout fab2Group, fab3Group;

    private ActivityReceiver activityReceiver;

    // Define intent action
    public static final String CTL_ACTION = "com.paulduan.CTL_ACTION";
    public static final String UPDATE_ACTION = "com.paulduan.UPDATE_ACTION";
    public static final String UPDATE_LIST = "com.paulduan.UPDATE_LIST";
    public static final String SAVE_DATA = "com.paulduan.SAVE_DATA";
    public static final String PLAY_SPECIFIED = "com.paulduan.PLAY_SPECIFIED";

    // Define music player status, 0x11 stop, 0x12 playing, 0x13 paused
    public int status = 0x11;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        RequestPermission();
        // Init components
        play = findViewById(R.id.play);
        stop = findViewById(R.id.stop);
        previous = findViewById(R.id.previous);
        next = findViewById(R.id.next);
        fab = (FloatingActionButton) findViewById(R.id.fab);
        seekBar = findViewById(R.id.seekBar);
        currentTime = findViewById(R.id.currentTime);
        totalTime = findViewById(R.id.totalTime);
        fab2Group = (LinearLayout) findViewById(R.id.fab2_group);
        fab3Group = (LinearLayout) findViewById(R.id.fab3_group);

        // Set onClickListener for buttons, we implement onClick in this class
        play.setOnClickListener(this);
        stop.setOnClickListener(this);
        previous.setOnClickListener(this);
        next.setOnClickListener(this);
        findViewById(R.id.fab).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!isFABOpen) {
                    showFABMenu();
                } else {
                    closeFABMenu();
                }
            }
        });
        findViewById(R.id.fab2).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String randomName = UUID.randomUUID().toString();
                MusicPiece interval = new MusicPiece("INTERVAL", "25:00", "", "INTERVAL" + randomName);
                Log.i("test", "INTERVAL" + randomName);
                pieceList.add(interval);
                adapter.notifyDataSetChanged();
                // Save to a json file
                writeJsonFile();
            }
        });
        findViewById(R.id.fab3).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openFileManager();
            }
        });

        activityReceiver = new ActivityReceiver();
        // Create IntentFilter
        IntentFilter filter = new IntentFilter();
        // Set BroadcastReceiver action filter
        filter.addAction(UPDATE_ACTION);
        // Register BroadcastReceiver
        registerReceiver(activityReceiver, filter);
        // Create intent for MusicService and start
        mIntent = new Intent(MainActivity.this, MusicService.class);
        startService(mIntent);
        // Init RecycleView component
        musicList = findViewById(R.id.music_list);
        musicList.setLayoutManager(new LinearLayoutManager(this));
        adapter = new MusicAdapter(this, new MusicAdapter.ClickListener() {
            @Override
            public void onItemClick(int position, View v) {
                Log.d("test", "onItemClick position: " + position);
                play(position);
            }
        }); // We have implemented OnStartDragListener, so this class is an instance
        musicList.setAdapter(adapter);
        readJsonFile();
        // Init ItemTouchHelper component
        helper = new ItemTouchHelper(new ItemTouchHelper.Callback() {
            @Override
            public int getMovementFlags(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
                // Set movement flags, so that we can move up, down, left and right.
                int dragFlags = ItemTouchHelper.UP | ItemTouchHelper.DOWN;
                int swipeFlags = ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT;
                return makeMovementFlags(dragFlags, swipeFlags);
            }

            @Override
            public float getSwipeThreshold(@NonNull RecyclerView.ViewHolder viewHolder) {
                return 0.5f; // Delete after swipe half of it
            }

            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                int fromPosition = viewHolder.getAdapterPosition(); // Get position of viewHolder
                int toPosition = target.getAdapterPosition(); // Get position of current position
                Collections.swap(pieceList, fromPosition, toPosition); // Swap the data in the list
                writeJsonFile();
                adapter.notifyItemMoved(fromPosition, toPosition);
                return true;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                adapter.deleteItem(viewHolder.getAdapterPosition());
                writeJsonFile();
                return;
            }
        });
        helper.attachToRecyclerView(musicList);

        // Init for seekBar
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) { // When the user drags the progress bar
//                    SimpleDateFormat simpleDateFormat = new SimpleDateFormat("mm:ss", Locale.CHINA);
//                    Date date = new Date(progress);
//                    String formatTime = simpleDateFormat.format(date);
                    currentTime.setText(formatProgress(progress)); // Enhance user experience here
                    Intent intent = new Intent(CTL_ACTION);
                    intent.putExtra("seekBarControl", progress);
                    sendBroadcast(intent); // Tell service to seek progress
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                // It will cause a problem if we send the intent here
            }
        });
    }

    @Override
    protected void onDestroy() {
        Log.i("test", "awsl-activity");
        // Unregister receiver
        unregisterReceiver(activityReceiver);
        // Stop service
        stopService(mIntent);
        super.onDestroy();
    }

    @Override
    protected void onPause() {
        Intent intent = new Intent(SAVE_DATA);
        sendBroadcast(intent);
        super.onPause();
    }

    /**
     * Implement OnStartDragListener's onStartDrag method, it pass a viewHolder from adapter
     *
     * @param viewHolder The holder of the view to drag.
     */
    @Override
    public void onStartDrag(RecyclerView.ViewHolder viewHolder) {
        helper.startDrag(viewHolder);
    }

    /**
     * Request permission of read and write
     */
    public void RequestPermission() {
        ActivityCompat.requestPermissions(MainActivity.this, new String[]{
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
        }, 1);
    }

    /**
     * Inner class for ActivityReceiver
     */
    public class ActivityReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
//            Log.i("test","Receive");
            int update = intent.getIntExtra("update", -1); // For the update of btns
            int current = intent.getIntExtra("current", -1); // For the update of playing view

            int now = intent.getIntExtra("currentTime", -1); // For the update of time
            int total = intent.getIntExtra("totalTime", -1); // For the update of time
//            Log.i("test","now: " + Integer.toString(now) + ", total: " + Integer.toString(total));

            if (current >= 0) { // Make sure current is valid
                for (int i = 0; i < pieceList.size(); i++) { // Only set current to green text
                    if (i == current) {
                        pieceList.get(i).setPlaying(true);
                    } else {
                        pieceList.get(i).setPlaying(false);
                    }
                }
                adapter.notifyDataSetChanged();
                musicList.smoothScrollToPosition(current);
            }

            if (total >= 0) { // Make sure total is valid
                seekBar.setMax(total);
                totalTime.setText(formatProgress(total));
            }
            if (now >= 0) { // Make sure now is valid
                seekBar.setProgress(now);
//                Log.i("test",Integer.toString(now));
                currentTime.setText(formatProgress(now));
            }

            // Update buttons
            switch (update) {
                case 0x11: // Not playing, click to play
                    play.setIconResource(R.drawable.ic_play);
                    status = 0x11;
//                    Log.i("test","Play2");
                    break;
                case 0x12: // Playing now, click to pause
                    play.setIconResource(R.drawable.ic_pause);
                    status = 0x12;
//                    Log.i("test","Play3");
                    break;

                case 0x13: // Paused now, click to play
                    play.setIconResource(R.drawable.ic_play);
                    status = 0x13;
//                    Log.i("test","Play4");
                    break;
            }
        }
    }

    /**
     * Override onCreateOptionsMenu to set menu inflate
     * @param menu
     * @return
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case R.id.about:
                TextView view  = new TextView(this);
                view.setMovementMethod(LinkMovementMethod.getInstance());
                view.setPadding(20,20,20,20);
                view.setTextSize(18);
                view.setText(Html.fromHtml("This is an open source project:<br/><a href=\"https://github.com/PaulDuanGitHub/pomo-player\">Github Link</a>", Html.FROM_HTML_MODE_COMPACT));
                AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                builder.setCancelable(true);
                builder.setView(view);
                AlertDialog alert = builder.create();
                alert.show();
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * Implement View.OnClickListener interface so that we can use MainActivity Class for button listener
     *
     * @param source
     */
    @Override
    public void onClick(View source) {
        Intent intent = new Intent(CTL_ACTION);
        switch (source.getId()) {
            // play/pause
            case R.id.play:
                intent.putExtra("control", 1);
                break;
            // stop
            case R.id.stop:
                intent.putExtra("control", 2);
                break;
            // prev
            case R.id.previous:
                intent.putExtra("control", 3);
                break;
            // next
            case R.id.next:
                intent.putExtra("control", 4);
                break;
        }
        sendBroadcast(intent);
    }

    private void play(int index) {
        Intent intent = new Intent(PLAY_SPECIFIED);
        intent.putExtra("index", index);
        sendBroadcast(intent);
    }

    private String formatProgress(int progress) {
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("mm:ss", Locale.CHINA);
        Date date = new Date(progress);
        return simpleDateFormat.format(date);
    }

    private void closeFABMenu() {
        isFABOpen = false;
        findViewById(R.id.sub_menu).setVisibility(View.INVISIBLE);
        fab2Group.animate().translationY(0);
        fab3Group.animate().translationY(0);
        fab.animate().rotation(0);
    }

    private void showFABMenu() {
        isFABOpen = true;
        findViewById(R.id.sub_menu).setVisibility(View.VISIBLE);
        fab2Group.animate().translationY(-getResources().getDimension(R.dimen.standard_55));
        fab3Group.animate().translationY(-getResources().getDimension(R.dimen.standard_105));
        fab.animate().rotation(45);
    }

    private void openFileManager() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
//        intent.setDataAndType(Uri.parse(getResources().getString(R.string.default_path)), "audio/*");
        intent.setType("audio/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        mStartForResult.launch(intent);
    }

    private void addPiece(Uri uri) {
        String fileName;
//        fileName = fileName.substring(fileName.lastIndexOf('/') + 1);
        fileName = ASFUriHelper.getPath(MainActivity.this,uri);
        Log.i("test",fileName);
        String title;
        String artist;
        String duration;
        try {
            MediaMetadataRetriever mmr = new MediaMetadataRetriever();
            mmr.setDataSource(MainActivity.this, Uri.parse(fileName));
            title = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
            artist = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
            duration = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
        } catch (RuntimeException e) {
            e.printStackTrace();
            return;
        }

        duration = formatProgress(Integer.parseInt(duration));
        MusicPiece selected = new MusicPiece(title, duration, artist, fileName);
//                        closeFABMenu();
        // make sure there is no repeated music in the list
        if (!pieceList.contains(selected)) {
            pieceList.add(selected);
        } else {
            Toast.makeText(getApplicationContext(), "Already exist " + selected.getFileName(), Toast.LENGTH_SHORT).show();
        }
    }

    private void writeJsonFile() {
        Gson gson = new Gson();
        String json = gson.toJson(pieceList);
//        File file = new File(getResources().getString(R.string.default_path) + ".data.json");
        File file = new File(getFilesDir() + "/.data.json");
        try {
            if (!file.exists()) {
                file.createNewFile();
            }
            BufferedWriter bw = new BufferedWriter(new FileWriter(file));
            bw.write(json);
            bw.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        Intent intent = new Intent(UPDATE_LIST);
        sendBroadcast(intent);
    }

    private void readJsonFile() {
        Gson gson = new Gson();
//        File file = new File(getResources().getString(R.string.default_path) + ".data.json");
        File file = new File(getFilesDir() + "/.data.json");
        try {
            if (!file.exists()) {
                file.createNewFile();
                BufferedWriter bw = new BufferedWriter(new FileWriter(file));
                bw.write("[]");
                bw.close();
            }
            BufferedReader br = new BufferedReader(new FileReader(file));
            pieceList.addAll(gson.fromJson(br, new TypeToken<ArrayList<MusicPiece>>() {
            }.getType()));
            pieceList.forEach(piece -> {
                piece.setPlaying(false);
            });
            adapter.notifyDataSetChanged();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}