package com.paulduan.music_player;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import java.util.List;

// RecyclerView.Adapter<RecyclerView.ViewHolder> has to have RecyclerView.ViewHolder as generic for multi ViewHolder
public class MusicAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    // Instance of OnStartDragListener
    private final OnStartDragListener mDragStartListener;
    private final ClickListener mClickListener;
    // Constructor
    public MusicAdapter(OnStartDragListener dragStartListener, ClickListener mClickListener) {
        this.mClickListener = mClickListener;
        this.mDragStartListener = dragStartListener;
    }

    @Override
    public int getItemViewType(int position) {
        boolean isInterval = MainActivity.pieceList.get(position).getFileName().startsWith("INTERVAL");
        isInterval = isInterval && (MainActivity.pieceList.get(position).getFileName().length() == 36 + 8);
        if (isInterval) { // Is interval
            if (MainActivity.pieceList.get(position).isPlaying()) { // Playing
                return 11;
            }
            return 10; // Interval but not playing
        }

        if (MainActivity.pieceList.get(position).isPlaying()) { // Not an interval, and playing
            return 1;
        }
        return 0; // Not an interval, and not playing
    }

    @Override
    /**
     * Create holder for all pieces and set different viewType
     */
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        RecyclerView.ViewHolder holder;
        int layout;
        switch (viewType) {
            case 11:
                layout = R.layout.playing_interval_item;
                holder = new MyViewHolder1(LayoutInflater.from(
                        parent.getContext()).inflate(layout, parent, false));
                break;
            case 10:
                layout = R.layout.interval_item;
                holder = new MyViewHolder1(LayoutInflater.from(
                        parent.getContext()).inflate(layout, parent, false));
                break;
            case 0:
                layout = R.layout.music_item;
                holder = new MyViewHolder(LayoutInflater.from(
                        parent.getContext()).inflate(layout, parent, false));
                break;
            case 1:
                layout = R.layout.playing_music_item;
                holder = new MyViewHolder(LayoutInflater.from(
                        parent.getContext()).inflate(layout, parent, false));
                break;
            default:
                throw new IllegalStateException("Unexpected value: " + viewType);
        }
        return holder;
    }

    /**
     * Bind ViewHolder with text
     *
     * @param holder
     * @param position
     */
    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        // Interval has no author
        if (holder.getItemViewType() == 10 || holder.getItemViewType() == 11) {
            ((MyViewHolder1) holder).pieceTitle.setText(MainActivity.pieceList.get(position).getTitle());
            ((MyViewHolder1) holder).pieceDuration.setText(MainActivity.pieceList.get(position).getDuration());
            // If on touch, pass the viewHolder
            ((MyViewHolder1) holder).handler.setOnTouchListener(new View.OnTouchListener() {
                public boolean onTouch(View v, MotionEvent event) {
                    if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                        mDragStartListener.onStartDrag(holder);
                    }
                    return false;
                }
            });
            return;
        }
        ((MyViewHolder) holder).pieceTitle.setText(MainActivity.pieceList.get(position).getTitle());
        ((MyViewHolder) holder).pieceAuthor.setText(MainActivity.pieceList.get(position).getAuthor());
        ((MyViewHolder) holder).pieceDuration.setText(MainActivity.pieceList.get(position).getDuration());
        ((MyViewHolder) holder).handler.setOnTouchListener(new View.OnTouchListener() {
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    mDragStartListener.onStartDrag(holder);
                }
                return false;
            }
        });
    }

    @Override
    public int getItemCount() {
        return MainActivity.pieceList.size();
    }

    public void deleteItem(int position) {
        MainActivity.pieceList.remove(position);
        notifyItemRemoved(position);
    }

    // Inner class
    public class MyViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener{
        TextView pieceTitle;
        TextView pieceAuthor;
        TextView pieceDuration;
        MaterialButton handler;

        public MyViewHolder(View view) {
            super(view);
            pieceTitle = view.findViewById(R.id.music_title);
            pieceAuthor = view.findViewById(R.id.music_author);
            pieceDuration = view.findViewById(R.id.music_duration);
            handler = view.findViewById(R.id.handler);
            view.setOnClickListener(this);
        }

        @Override
        public void onClick(View v) {
            int position = getAdapterPosition();
            if (position >= 0) {
                mClickListener.onItemClick(position, v);
            }
        }
    }

    public class MyViewHolder1 extends RecyclerView.ViewHolder implements View.OnClickListener {
        TextView pieceTitle;
        TextView pieceDuration;
        MaterialButton handler;

        public MyViewHolder1(View view) {
            super(view);
            pieceTitle = view.findViewById(R.id.music_title);
            pieceDuration = view.findViewById(R.id.music_duration);
            handler = view.findViewById(R.id.handler);
            view.setOnClickListener(this);
        }

        @Override
        public void onClick(View v) {
            int position = getAdapterPosition();
            if (position >= 0) {
                mClickListener.onItemClick(position, v);
            }
        }
    }

    public interface ClickListener {
        void onItemClick(int position, View v);
    }
}
