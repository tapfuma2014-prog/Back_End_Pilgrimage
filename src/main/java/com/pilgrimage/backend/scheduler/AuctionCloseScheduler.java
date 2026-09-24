package com.pilgrimage.backend.scheduler;

import com.pilgrimage.backend.service.AuctionCloseService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AuctionCloseScheduler {

    private final AuctionCloseService auctionCloseService;

    public AuctionCloseScheduler(AuctionCloseService auctionCloseService) {
        this.auctionCloseService = auctionCloseService;
    }

    @Scheduled(fixedDelayString = "${auction.close.poll-interval-ms:60000}", initialDelayString = "${auction.close.initial-delay-ms:15000}")
    public void closeExpiredAuctions() {
        auctionCloseService.closeExpiredAuctions();
    }
}
