package com.ludot.model;

import com.ludot.config.GameConfig;
import com.ludot.exception.InvalidLocationException;

import java.util.Objects;

// Represents the different places a piece can occupy on the board.
public final class Location {

    // Each location stores its role, owner colour, and index when that value
    // matters.
    private final LocationType type;
    private final Colour colour;
    private final int index;

    private Location(LocationType type, Colour colour, int index) {
        this.type = type;
        this.colour = colour;
        this.index = index;
    }

    // These factory methods build the standard location types used by the board.
    public static Location base(Colour colour) {
        requireColour(colour, "base");
        return new Location(LocationType.BASE, colour, -1);
    }

    public static Location standardPath(int index) {
        if (index < 0 || index >= GameConfig.STANDARD_PATH_SIZE) {
            throw new InvalidLocationException(
                    "Standard path index must be 0 to "
                            + (GameConfig.STANDARD_PATH_SIZE - 1) + ", got " + index);
        }
        return new Location(LocationType.STANDARD_PATH, null, index);
    }

    public static Location startingSquare(Colour colour, int index) {
        requireColour(colour, "startingSquare");
        requireStandardPathIndex(index, "startingSquare");
        return new Location(LocationType.STARTING_SQUARE, colour, index);
    }

    public static Location approach(Colour colour, int index) {
        requireColour(colour, "approach");
        requireStandardPathIndex(index, "approach");
        return new Location(LocationType.APPROACH, colour, index);
    }

    public static Location homePath(Colour colour, int index) {
        requireColour(colour, "homePath");
        if (index < 0 || index >= GameConfig.HOME_PATH_SIZE) {
            throw new InvalidLocationException(
                    "Home path index must be 0 to "
                            + (GameConfig.HOME_PATH_SIZE - 1) + ", got " + index);
        }
        return new Location(LocationType.HOME_PATH, colour, index);
    }

    public static Location home(Colour colour) {
        requireColour(colour, "home");
        return new Location(LocationType.HOME, colour, -1);
    }

    public static Location alpha(int index) {
        requireStandardPathIndex(index, "alpha");
        return new Location(LocationType.ALPHA, null, index);
    }

    public static Location beta(int index) {
        requireStandardPathIndex(index, "beta");
        return new Location(LocationType.BETA, null, index);
    }

    public static Location gamma(int index) {
        requireStandardPathIndex(index, "gamma");
        return new Location(LocationType.GAMMA, null, index);
    }

    // These helpers keep argument checks and display-name formatting in one place.
    private static void requireColour(Colour colour, String factoryName) {
        if (colour == null) {
            throw new InvalidLocationException(
                    "Colour must not be null for location type: " + factoryName);
        }
    }

    private static void requireStandardPathIndex(int index, String factoryName) {
        if (index < 0 || index >= GameConfig.STANDARD_PATH_SIZE) {
            throw new InvalidLocationException(
                    "Index for " + factoryName + " must be 0 to "
                            + (GameConfig.STANDARD_PATH_SIZE - 1) + ", got " + index);
        }
    }

    // These accessors expose the location's identity and metadata.
    public LocationType getType() {
        return type;
    }

    public Colour getColour() {
        return colour;
    }

    public int getIndex() {
        return index;
    }

    // These helpers make common location checks easy for callers.
    public boolean isBase() {
        return type == LocationType.BASE;
    }

    public boolean isHome() {
        return type == LocationType.HOME;
    }

    public boolean isStandardPath() {
        return type == LocationType.STANDARD_PATH;
    }

    public boolean isHomePath() {
        return type == LocationType.HOME_PATH;
    }

    public boolean isStartingSquare() {
        return type == LocationType.STARTING_SQUARE;
    }

    public boolean isApproach() {
        return type == LocationType.APPROACH;
    }

    public boolean isAlpha() {
        return type == LocationType.ALPHA;
    }

    public boolean isBeta() {
        return type == LocationType.BETA;
    }

    public boolean isGamma() {
        return type == LocationType.GAMMA;
    }

    public boolean isOnStandardRing() {
        return switch (type) {
            case STANDARD_PATH, STARTING_SQUARE, APPROACH, ALPHA, BETA, GAMMA -> true;
            default -> false;
        };
    }

    // The display name gives log output a readable location label.
    public String getDisplayName() {
        return switch (type) {
            case BASE -> "Base";
            case HOME -> "Home";
            case STANDARD_PATH -> String.valueOf(index);
            case STARTING_SQUARE -> String.valueOf(index);
            case APPROACH -> String.valueOf(index);
            case HOME_PATH -> colourPrefix() + "homepath" + index;
            case ALPHA -> String.valueOf(index);
            case BETA -> String.valueOf(index);
            case GAMMA -> String.valueOf(index);
        };
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof Location other))
            return false;
        return index == other.index
                && type == other.type
                && Objects.equals(colour, other.colour);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, colour, index);
    }

    @Override
    public String toString() {
        return "Location{" + getDisplayName() + "}";
    }

    private String colourPrefix() {
        if (colour == null) {
            return "";
        }
        return colour.name().toLowerCase();
    }
}