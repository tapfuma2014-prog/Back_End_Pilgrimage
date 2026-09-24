package com.pilgrimage.backend.service;

import com.stripe.Stripe;
import com.stripe.model.PaymentIntent;
import com.stripe.param.PaymentIntentCreateParams;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@Service
public class StripeService {

    @Value("${stripe.secret-key:}")
    private String secretKey;

    public PaymentIntent createAuctionPaymentIntent(long amountCents, Map<String, String> metadata) {
        return createPaymentIntent(amountCents, metadata);
    }

    public PaymentIntent createPaymentIntent(long amountCents, Map<String, String> metadata) {
        if (secretKey == null || secretKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Stripe is not configured");
        }

        Stripe.apiKey = secretKey;

        PaymentIntentCreateParams.Builder paramsBuilder = PaymentIntentCreateParams.builder()
            .setAmount(amountCents)
            .setCurrency("aud")
            .setAutomaticPaymentMethods(
                PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                    .setEnabled(true)
                    .build()
            );

        if (metadata != null) {
            paramsBuilder.putAllMetadata(metadata);
        }

        try {
            return PaymentIntent.create(paramsBuilder.build());
        } catch (Exception e) {
            throw new ResponseStatusException(
                HttpStatus.BAD_GATEWAY,
                "Unable to create payment intent"
            );
        }
    }

    public PaymentIntent retrievePaymentIntent(String paymentIntentId) {
        if (secretKey == null || secretKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Stripe is not configured");
        }
        if (paymentIntentId == null || paymentIntentId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payment intent ID is required");
        }

        Stripe.apiKey = secretKey;
        try {
            return PaymentIntent.retrieve(paymentIntentId);
        } catch (Exception e) {
            throw new ResponseStatusException(
                HttpStatus.BAD_GATEWAY,
                "Unable to retrieve payment intent"
            );
        }
    }
}
