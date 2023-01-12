package com.paulduan.music_player;

import android.net.Uri;

import java.util.Objects;

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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MusicPiece piece = (MusicPiece) o;
        return Objects.equals(title, piece.title) && Objects.equals(duration, piece.duration) && Objects.equals(author, piece.author) && Objects.equals(fileName, piece.fileName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(title, duration, author, fileName);
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
