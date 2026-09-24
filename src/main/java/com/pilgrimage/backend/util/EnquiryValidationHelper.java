package com.pilgrimage.backend.util;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

public final class EnquiryValidationHelper {

    private EnquiryValidationHelper() {
    }

    public static void validateSendEmailPayload(Map<String, Object> payload) {
        String to = getString(payload, "to");
        String subject = getString(payload, "subject");
        String body = getString(payload, "body");

        if (to.isBlank() || subject.isBlank() || body.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email to, subject, and body are required");
        }
        if (!to.contains("@")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid recipient email address");
        }
    }

    public static void validateGardenBookingPayload(Map<String, Object> payload) {
        if (payload == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Booking data is required");
        }

        String purpose = getString(payload, "purpose");
        String date = getString(payload, "date");
        String startTime = getString(payload, "start_time");
        String bookerName = getString(payload, "booker_name");
        String bookerEmail = getString(payload, "booker_email");

        if (purpose.isBlank() || date.isBlank() || startTime.isBlank() || bookerName.isBlank() || bookerEmail.isBlank()) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Event type, date, start time, name, and email are required"
            );
        }
        if (!bookerEmail.contains("@")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid email address");
        }

        Object guestCount = payload.get("guest_count");
        if (guestCount instanceof Number number && number.intValue() < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Guest count must be at least 1");
        }
    }

    private static String getString(Map<String, Object> payload, String key) {
        if (payload == null || !payload.containsKey(key) || payload.get(key) == null) {
            return "";
        }
        return payload.get(key).toString().trim();
    }
}
