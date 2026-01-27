package com.pilgrimage.backend.service;

public class ImageProxyResponse {
    private final int statusCode;
    private final String contentType;
    private final byte[] body;

    public ImageProxyResponse(int statusCode, String contentType, byte[] body) {
        this.statusCode = statusCode;
        this.contentType = contentType;
        this.body = body;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getContentType() {
        return contentType;
    }

    public byte[] getBody() {
        return body;
    }
}
