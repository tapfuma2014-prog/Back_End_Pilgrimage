package com.pilgrimage.backend.util;

import com.pilgrimage.backend.model.User;
import com.pilgrimage.backend.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.Collection;

public final class EntityAuthorizationHelper {

    private static final Set<String> SENSITIVE_USER_FIELDS = Set.of(
        "password",
        "reset_token",
        "reset_token_expiry",
        "verification_token",
        "verification_token_expiry"
    );

    // Catalogue pages that can be read without authentication.
    private static final Set<String> PUBLIC_READ_ENTITIES = Set.of(
        "Artwork", "Exhibition", "Event", "Class53Event", "Artist",
        "Merchandise", "Auction", "ArtworkCategory", "Collection",
        "SiteBranding", "Workshop", "SiteContent",
        "Page", "Gallery", "Room", "Class53Course", "Class53Session",
        "ArtRoverTour", "Discussion", "DiscussionComment"
    );

    // Per-user entities that must never be publicly readable.
    private static final Set<String> USER_OWNED_ENTITIES = Set.of(
        "Cart", "Order", "MerchOrder", "AuctionBid", "AuctionWatchlist",
        "Wishlist", "EventBooking", "WorkshopBooking", "GardenBooking",
        "PaintingStationBooking", "Class53Booking", "ArtistRequest",
        "AffiliateReferral", "Class53Affiliate", "AffiliateMedia",
        "Notification", "NotificationPreference", "CollectionLike",
        "ArtworkVote", "Vote", "UserGallery", "PortraitCommission",
        // Additional per-user records - reads/writes are scoped to the owner.
        "ArtRoverBooking", "GiftVoucher", "AuctionWinner", "TicketPurchase",
        "EventTicket", "SavedAddress", "SavedPostcard", "AiArtPurchase",
        "PremiumSubscription", "GeneratedArt", "MerchReview", "WorkshopWaitlist"
    );

    // Always admin-only, even for reads.
    private static final Set<String> ADMIN_ONLY_ENTITIES = Set.of(
        "User", "CRMClient", "CRMInteraction", "CRMCampaign",
        "CRMSegment", "CRMTask", "CRMWorkflow",
        // Financial / internal metrics - never readable by regular users.
        "ArtistRoyalty", "ArtistScore", "Class53Newsletter"
    );

    // Create/update/delete restricted to admins; reads may remain public.
    private static final Set<String> ADMIN_WRITE_ENTITIES = Set.of(
        "SiteBranding", "SiteContent", "Page", "Gallery", "Room",
        "Event", "Exhibition", "Merchandise", "Auction", "Workshop",
        "WorkshopSession", "Class53Event", "Class53Course", "Class53Session",
        "ArtRoverTour", "ArtRoverRoute", "Garden", "PaintingStation",
        "Award", "ArtworkCategory",
        // Readable by authenticated users (needed for checkout validation)
        // but only admins may create or modify them.
        "PromoCode", "GiftCard"
    );

    private EntityAuthorizationHelper() {
    }

    public static String currentUserEmail() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
            || "anonymousUser".equalsIgnoreCase(authentication.getName())) {
            return null;
        }
        return authentication.getName().toLowerCase().trim();
    }

    public static boolean isAuthenticated() {
        return currentUserEmail() != null;
    }

    private static boolean isAdmin(String email, UserRepository userRepository) {
        if (email == null) {
            return false;
        }
        Optional<User> user = userRepository.findByEmail(email);
        return user.isPresent() && "admin".equalsIgnoreCase(user.get().getRole());
    }

    public static boolean isAdmin(UserRepository userRepository) {
        return isAdmin(currentUserEmail(), userRepository);
    }

    public static boolean isPublicRead(String entity) {
        return entity != null && PUBLIC_READ_ENTITIES.contains(entity);
    }

    public static boolean isUserOwned(String entity) {
        return entity != null && USER_OWNED_ENTITIES.contains(entity);
    }

    public static boolean requiresAdminRead(String entity) {
        return entity != null && ADMIN_ONLY_ENTITIES.contains(entity);
    }

    public static boolean requiresAdminWrite(String entity) {
        return entity != null && ADMIN_WRITE_ENTITIES.contains(entity);
    }

    public static void ensureArtworkQueryAccess(
        String entity,
        Map<String, Object> filters,
        UserRepository userRepository
    ) {
        if (!"Artwork".equals(entity) || isAdmin(userRepository)) {
            return;
        }
        if (filters == null || filters.isEmpty()) {
            return;
        }
        Object statusValue = filters.get("status");
        if (statusValue == null) {
            return;
        }
        if (isApprovedModerationStatus(statusValue)) {
            return;
        }
        String email = currentUserEmail();
        if (email == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        Object createdBy = filters.get("created_by");
        if (createdBy == null || !email.equalsIgnoreCase(String.valueOf(createdBy).trim())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden");
        }
    }

    private static boolean isApprovedModerationStatus(Object statusValue) {
        if (statusValue instanceof Collection<?> collection) {
            if (collection.isEmpty()) {
                return true;
            }
            return collection.stream().allMatch(EntityAuthorizationHelper::isApprovedModerationStatus);
        }
        return "approved".equalsIgnoreCase(String.valueOf(statusValue).trim());
    }

    public static void ensureReadAccess(String entity, UserRepository userRepository) {
        if (isPublicRead(entity)) {
            return;
        }
        String email = currentUserEmail();
        if (email == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        if (requiresAdminRead(entity) && !isAdmin(email, userRepository)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access required");
        }
    }

    public static void ensureWriteAccess(String entity, UserRepository userRepository) {
        String email = currentUserEmail();
        if (email == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        if (requiresAdminWrite(entity) && !isAdmin(email, userRepository)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access required");
        }
        if (requiresAdminRead(entity) && !isAdmin(email, userRepository)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access required");
        }
    }

    public static void ensureAdmin(UserRepository userRepository) {
        String email = currentUserEmail();
        if (email == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        if (!isAdmin(email, userRepository)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access required");
        }
    }

    public static Map<String, Object> sanitizeUserRow(String entity, Map<String, Object> row) {
        if (!"User".equals(entity) && !"users".equals(entity)) {
            return row;
        }
        Map<String, Object> sanitized = new LinkedHashMap<>(row);
        for (String field : SENSITIVE_USER_FIELDS) {
            sanitized.remove(field);
        }
        return sanitized;
    }

    public static Map<String, Object> sanitizePublicCatalogRow(Map<String, Object> row) {
        Map<String, Object> sanitized = new LinkedHashMap<>(row);
        for (String field : SENSITIVE_USER_FIELDS) {
            sanitized.remove(field);
        }
        sanitized.remove("created_by");
        sanitized.remove("updated_by");
        return sanitized;
    }
}
