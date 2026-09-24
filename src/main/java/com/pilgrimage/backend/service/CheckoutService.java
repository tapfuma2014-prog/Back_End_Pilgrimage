package com.pilgrimage.backend.service;

import com.pilgrimage.backend.dto.OrderCaptureRequest;
import com.pilgrimage.backend.dto.OrderCheckoutRequest;

import java.util.Map;

public interface CheckoutService {
    Map<String, String> prepareOrderPaymentIntent(OrderCheckoutRequest request);

    Map<String, Object> captureOrderPayment(OrderCaptureRequest request);
}
