package com.pilgrimage.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

public class PlaceBidRequest {
    @JsonProperty("auction_id")
    private String auctionId;

    @JsonProperty("artwork_id")
    private String artworkId;

    @JsonProperty("bidder_id")
    private String bidderId;

    @JsonProperty("bidder_name")
    private String bidderName;

    @JsonProperty("bid_amount")
    private BigDecimal bidAmount;

    @JsonProperty("max_proxy_bid")
    private BigDecimal maxProxyBid;

    public String getAuctionId() {
        return auctionId;
    }

    public void setAuctionId(String auctionId) {
        this.auctionId = auctionId;
    }

    public String getArtworkId() {
        return artworkId;
    }

    public void setArtworkId(String artworkId) {
        this.artworkId = artworkId;
    }

    public String getBidderId() {
        return bidderId;
    }

    public void setBidderId(String bidderId) {
        this.bidderId = bidderId;
    }

    public String getBidderName() {
        return bidderName;
    }

    public void setBidderName(String bidderName) {
        this.bidderName = bidderName;
    }

    public BigDecimal getBidAmount() {
        return bidAmount;
    }

    public void setBidAmount(BigDecimal bidAmount) {
        this.bidAmount = bidAmount;
    }

    public BigDecimal getMaxProxyBid() {
        return maxProxyBid;
    }

    public void setMaxProxyBid(BigDecimal maxProxyBid) {
        this.maxProxyBid = maxProxyBid;
    }
}
