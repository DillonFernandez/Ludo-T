package com.ludot.exception;

// Signals that the game configuration is invalid or internally inconsistent.
public class GameConfigurationException extends RuntimeException {

    // These constructors report configuration problems with or without an
    // underlying cause.
    public GameConfigurationException(String message) {
        super(message);
    }

    // The cause-based constructor preserves the original exception for debugging.
    public GameConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}