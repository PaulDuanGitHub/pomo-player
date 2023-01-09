package com.paulduan.music_player;

import android.net.Uri;

public class MusicPiece {
    private String title;
    private String duration;
    private String author;
    private String fileName;
    private boolean playing;

    public MusicPiece(String title, String duration, String author, String fileName) {
        this.title = title;
        this.duration = duration;
        this.author = author;
        this.fileName = fileName;
        this.playing = false;
    }

    public MusicPiece(String title, String duration, String author) {
        this.title = title;
        this.duration = duration;
        this.author = author;
        this.playing = false;
    }

    public String getTitle() {
        return title;
    }

    public String getDuration() {
        return duration;
    }

    public String getAuthor() {
        return author;
    }

    public String getFileName() {
        return fileName;
    }

    public boolean isPlaying() {
        return playing;
    }

    public void setPlaying(boolean playing) {
        this.playing = playing;
    }
}
