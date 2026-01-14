package com.pilgrimage.backend.dto;

public class ArtworkSearchResult {
    private final String id;
    private final String title;
    private final String artist;
    private final String imageUrl;
    private final String artStyle;

    public ArtworkSearchResult(String id, String title, String artist, String imageUrl, String artStyle) {
        this.id = id;
        this.title = title;
        this.artist = artist;
        this.imageUrl = imageUrl;
        this.artStyle = artStyle;
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getArtist() {
        return artist;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public String getArtStyle() {
        return artStyle;
    }
}
