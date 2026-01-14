package com.pilgrimage.backend.dto;

public class ExhibitionSearchResult {
    private final String id;
    private final String title;
    private final String status;
    private final String imageUrl;

    public ExhibitionSearchResult(String id, String title, String status, String imageUrl) {
        this.id = id;
        this.title = title;
        this.status = status;
        this.imageUrl = imageUrl;
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getStatus() {
        return status;
    }

    public String getImageUrl() {
        return imageUrl;
    }
}
