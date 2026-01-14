package com.pilgrimage.backend.dto;

import java.util.List;

public class SearchResponse {
    private final List<ArtworkSearchResult> artworks;
    private final List<ExhibitionSearchResult> exhibitions;
    private final List<AuctionSearchResult> auctions;

    public SearchResponse(List<ArtworkSearchResult> artworks,
                          List<ExhibitionSearchResult> exhibitions,
                          List<AuctionSearchResult> auctions) {
        this.artworks = artworks;
        this.exhibitions = exhibitions;
        this.auctions = auctions;
    }

    public static SearchResponse empty() {
        return new SearchResponse(List.of(), List.of(), List.of());
    }

    public List<ArtworkSearchResult> getArtworks() {
        return artworks;
    }

    public List<ExhibitionSearchResult> getExhibitions() {
        return exhibitions;
    }

    public List<AuctionSearchResult> getAuctions() {
        return auctions;
    }
}
