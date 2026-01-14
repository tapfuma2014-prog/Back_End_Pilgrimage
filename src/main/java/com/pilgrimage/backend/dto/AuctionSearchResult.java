package com.pilgrimage.backend.dto;

public class AuctionSearchResult {
    private final String id;
    private final String title;
    private final String status;

    public AuctionSearchResult(String id, String title, String status) {
        this.id = id;
        this.title = title;
        this.status = status;
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
}
