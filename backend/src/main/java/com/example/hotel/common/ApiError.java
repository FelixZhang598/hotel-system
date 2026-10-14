package com.example.hotel.common;

public record ApiError(String code, String message, String requestId) {}
