package com.ludot.model;

// Names the board-position categories used by the game logic.
public enum LocationType {

    // Waiting area for pieces before they enter the shared track.
    BASE,

    // Shared numbered square on the main circuit.
    STANDARD_PATH,

    // Private path a player uses before reaching home.
    HOME_PATH,

    // Final destination once a piece has finished its route.
    HOME,

    // Final shared square before a player's private path begins.
    APPROACH,

    // First square a piece reaches after leaving base.
    STARTING_SQUARE,

    // Special square with an effect that depends on the rule set.
    ALPHA,

    // Special square used for a short-lived rule effect.
    BETA,

    // Special square that can alter movement behaviour.
    GAMMA
}