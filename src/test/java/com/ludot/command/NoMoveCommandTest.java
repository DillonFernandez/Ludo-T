package com.ludot.command;

import com.ludot.board.BoardBuilder;
import com.ludot.model.Colour;
import com.ludot.rules.MovementRule;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

// These tests confirm that the no-move command remains safe to execute.
class NoMoveCommandTest {

    // Verifies that executing this fallback command does not fail.
    @Test
    void executeDoesNotThrowAndHasNoSideEffects() {
        for (Colour colour : Colour.values()) {
            var board = new BoardBuilder().build();
            var piece = board.getHomeArea(colour).pieces().get(0);
            var movement = new MovementRule(board);
            for (int roll = 1; roll < 6; roll++) {
                Command command = movement.commandFromBaseIfAllowed(piece, roll, () -> {
                    throw new AssertionError("An ignored roll must not toss the coin");
                });
                assertInstanceOf(NoMoveCommand.class, command, colour + " roll " + roll);
                command.execute();
                assertTrue(piece.isInBase(), colour + " roll " + roll);
                assertEquals(4, board.getHomeArea(colour).countPieces(), colour + " roll " + roll);
                assertNull(piece.getDirection(), colour + " roll " + roll);
            }
        }
    }
}
