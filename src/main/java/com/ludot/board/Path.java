package com.ludot.board;

import com.ludot.config.GameConfig;
import com.ludot.exception.InvalidLocationException;
import com.ludot.model.Colour;

// Provides the fixed indexes used by the LUDO-T track: entry points, approach points, and special squares.
public class Path {

    // This guard ensures the colour argument is never null.
    private static void requireColour(Colour colour) {
        if (colour == null) {
            throw new IllegalArgumentException("Colour must not be null.");
        }
    }

    // Each colour enters the main ring from its own starting index.
    public int getStartingIndex(Colour colour) {
        requireColour(colour);
        return switch (colour) {
            case YELLOW -> 0;
            case BLUE -> 13;
            case RED -> 26;
            case GREEN -> 39;
        };
    }

    // The approach index marks the last standard-path square before a piece moves
    // into its home path.
    public int getApproachIndex(Colour colour) {
        requireColour(colour);
        return switch (colour) {
            case YELLOW -> 51;
            case BLUE -> 12;
            case RED -> 25;
            case GREEN -> 38;
        };
    }

    // Alpha, Beta, and Gamma are named special squares used by rule effects.
    public int getAlphaIndex() {
        return (51 + GameConfig.ALPHA_OFFSET_FROM_YELLOW_APPROACH) % GameConfig.STANDARD_PATH_SIZE;
    }

    public int getBetaIndex() {
        return (51 + GameConfig.BETA_OFFSET_FROM_YELLOW_APPROACH) % GameConfig.STANDARD_PATH_SIZE;
    }

    public int getGammaIndex() {
        return (51 + GameConfig.GAMMA_OFFSET_FROM_YELLOW_APPROACH) % GameConfig.STANDARD_PATH_SIZE;
    }

    // These helpers wrap movement around the circular track so indexes always stay
    // valid.
    public int getNextClockwiseIndex(int index) {
        validateStandardIndex(index);
        return (index + 1) % GameConfig.STANDARD_PATH_SIZE;
    }

    public int getNextCounterClockwiseIndex(int index) {
        validateStandardIndex(index);
        int size = GameConfig.STANDARD_PATH_SIZE;
        return (index - 1 + size) % size;
    }

    // These helpers advance a piece by multiple steps, including wrap-around on the
    // ring.
    public int moveClockwise(int startIndex, int steps) {
        validateStandardIndex(startIndex);
        if (steps < 0) {
            throw new IllegalArgumentException("Steps must not be negative, got " + steps);
        }
        return (startIndex + steps) % GameConfig.STANDARD_PATH_SIZE;
    }

    public int moveCounterClockwise(int startIndex, int steps) {
        validateStandardIndex(startIndex);
        if (steps < 0) {
            throw new IllegalArgumentException("Steps must not be negative, got " + steps);
        }
        int size = GameConfig.STANDARD_PATH_SIZE;
        return (startIndex - (steps % size) + size) % size;
    }

    // This guard prevents invalid board positions from being used in movement and
    // lookup logic.
    public void validateStandardIndex(int index) {
        if (index < 0 || index >= GameConfig.STANDARD_PATH_SIZE) {
            throw new InvalidLocationException(
                    "Standard path index must be 0 to "
                            + (GameConfig.STANDARD_PATH_SIZE - 1) + ", got " + index);
        }
    }
}