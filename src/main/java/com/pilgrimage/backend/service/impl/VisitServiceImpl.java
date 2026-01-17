package com.pilgrimage.backend.service.impl;

import com.pilgrimage.backend.dto.VisitPageDto;
import com.pilgrimage.backend.service.VisitService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class VisitServiceImpl implements VisitService {

    @Override
    public List<VisitPageDto> getVisitPages() {
        return List.of(
            new VisitPageDto("Home", "Home", "Public Pages"),
            new VisitPageDto("Explore", "Explore", "Public Pages"),
            new VisitPageDto("Pilgrimage", "Pilgrimage", "Public Pages"),
            new VisitPageDto("Virtual Tour", "VirtualTour", "Public Pages"),
            new VisitPageDto("Virtual Pilmirage", "VirtualPilmirage", "Public Pages"),
            new VisitPageDto("Exhibitions", "Exhibitions", "Public Pages"),
            new VisitPageDto("Buy Art", "BuyArt", "Public Pages"),
            new VisitPageDto("Artwork Detail", "ArtworkDetail", "Public Pages"),
            new VisitPageDto("Portrait Corner", "PortraitCorner", "Public Pages"),
            new VisitPageDto("Gardens", "Gardens", "Public Pages"),
            new VisitPageDto("Painting Station", "PaintingStation", "Public Pages"),
            new VisitPageDto("Gift Vouchers", "GiftVouchers", "Public Pages"),
            new VisitPageDto("Gift Cards", "GiftCards", "Public Pages"),
            new VisitPageDto("Art Rover Tours", "ArtRoverTours", "Public Pages"),
            new VisitPageDto("Art Rover Map", "ArtRoverMap", "Public Pages"),
            new VisitPageDto("Events", "Events", "Public Pages"),
            new VisitPageDto("Event Detail", "EventDetail", "Public Pages"),
            new VisitPageDto("Auctions", "Auctions", "Public Pages"),
            new VisitPageDto("Auction Detail", "AuctionDetail", "Public Pages"),
            new VisitPageDto("Awards", "Awards", "Public Pages"),
            new VisitPageDto("Leaderboard", "Leaderboard", "Public Pages"),
            new VisitPageDto("Merch Store", "MerchStore", "Public Pages"),
            new VisitPageDto("Merch Product", "MerchProduct", "Public Pages"),
            new VisitPageDto("Community", "Community", "Public Pages"),
            new VisitPageDto("Artist Profile", "ArtistProfile", "Public Pages"),
            new VisitPageDto("Artist Profile Page", "ArtistProfilePage", "Public Pages"),
            new VisitPageDto("Art Styles", "ArtStyles", "Public Pages"),
            new VisitPageDto("Studio", "Studio", "Public Pages"),
            new VisitPageDto("Trails", "Trails", "Public Pages"),
            new VisitPageDto("Class 53", "Class53", "Public Pages"),
            new VisitPageDto("Class 53 Event", "Class53Event", "Public Pages"),
            new VisitPageDto("Class 53 Host Enquiry", "Class53HostEnquiry", "Public Pages"),
            new VisitPageDto("Workshops Classes", "WorkshopsClasses", "Public Pages"),
            new VisitPageDto("AI Art Generator", "AIArtGenerator", "Public Pages"),
            new VisitPageDto("Collaborate", "Collaborate", "Public Pages"),
            new VisitPageDto("Project Editor", "ProjectEditor", "Public Pages"),
            new VisitPageDto("Curated Collections", "CuratedCollections", "Public Pages"),
            new VisitPageDto("Collection View", "CollectionView", "Public Pages"),
            new VisitPageDto("New Build Commissions", "NewBuildCommissions", "Public Pages"),
            new VisitPageDto("Artist Request", "ArtistRequest", "Public Pages"),

            new VisitPageDto("Profile", "Profile", "User & Auth"),
            new VisitPageDto("User Settings", "UserSettings", "User & Auth"),
            new VisitPageDto("Notification Settings", "NotificationSettings", "User & Auth"),
            new VisitPageDto("My Vouchers", "MyVouchers", "User & Auth"),
            new VisitPageDto("My Auction Wins", "MyAuctionWins", "User & Auth"),
            new VisitPageDto("My Tickets", "MyTickets", "User & Auth"),
            new VisitPageDto("Cart", "Cart", "User & Auth"),
            new VisitPageDto("Checkout", "Checkout", "User & Auth"),
            new VisitPageDto("Order Confirmation", "OrderConfirmation", "User & Auth"),
            new VisitPageDto("Merch Cart", "MerchCart", "User & Auth"),
            new VisitPageDto("Merch Checkout", "MerchCheckout", "User & Auth"),
            new VisitPageDto("Merch Wishlist", "MerchWishlist", "User & Auth"),
            new VisitPageDto("Advanced Artwork Search", "AdvancedArtworkSearch", "User & Auth"),
            new VisitPageDto("Advanced Auction Search", "AdvancedAuctionSearch", "User & Auth"),

            new VisitPageDto("Events Admin", "EventsAdmin", "Admin"),
            new VisitPageDto("Event Dashboard", "EventDashboard", "Admin"),
            new VisitPageDto("Orders Admin", "OrdersAdmin", "Admin"),
            new VisitPageDto("Reviews Admin", "ReviewsAdmin", "Admin"),
            new VisitPageDto("Promo Code Admin", "PromoCodeAdmin", "Admin"),
            new VisitPageDto("Workshops Admin", "WorkshopsAdmin", "Admin"),
            new VisitPageDto("Workshops Dashboard", "WorkshopsDashboard", "Admin"),
            new VisitPageDto("Art Rover Admin", "ArtRoverAdmin", "Admin"),
            new VisitPageDto("CRM Dashboard", "CRMDashboard", "Admin"),
            new VisitPageDto("CRM Reports", "CRMReports", "Admin"),

            new VisitPageDto("Affiliate Dashboard", "AffiliateDashboard", "Affiliate"),
            new VisitPageDto("Class 53 Affiliate", "Class53Affiliate", "Affiliate")
        );
    }
}
