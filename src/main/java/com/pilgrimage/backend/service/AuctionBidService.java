package com.pilgrimage.backend.service;

import com.pilgrimage.backend.dto.PlaceBidRequest;

import java.util.List;
import java.util.Map;

public interface AuctionBidService {
    List<Map<String, Object>> placeBid(PlaceBidRequest request);
}
