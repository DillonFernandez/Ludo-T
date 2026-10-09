package com.ludot.exception;

// Signals that a board location reference is invalid or out of range.
public class InvalidLocationException extends RuntimeException {

    // This constructor reports the invalid location with a clear message.
    public InvalidLocationException(String message) {
        super(message);
    }

    // This constructor preserves the original cause for debugging the bad location
    // lookup.
    public InvalidLocationException(String message, Throwable cause) {
        super(message, cause);
    }
}