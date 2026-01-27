package com.pilgrimage.backend.service;

public record ImageRequestParams(
    String prompt,
    String encodedPrompt,
    Integer width,
    Integer height,
    String model
) {}
