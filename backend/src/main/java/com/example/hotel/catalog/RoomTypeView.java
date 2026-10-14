package com.example.hotel.catalog;

public record RoomTypeView(
    long id,
    String name,
    String description,
    int capacity,
    long nightlyPriceCents,
    int availableRooms) {}
