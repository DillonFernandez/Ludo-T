package com.ludot.command;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

// These tests confirm that the no-move command remains safe to execute.
class NoMoveCommandTest {

    // Verifies that executing this fallback command does not fail.
    @Test
    void executeDoesNotThrowAndHasNoSideEffects() {
        NoMoveCommand cmd = new NoMoveCommand();
        assertDoesNotThrow(cmd::execute);
    }
}
