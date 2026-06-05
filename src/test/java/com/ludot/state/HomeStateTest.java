package com.ludot.state;

import com.ludot.board.BoardBuilder;
import com.ludot.command.Command;
import com.ludot.command.NoMoveCommand;
import com.ludot.model.Colour;
import com.ludot.model.Location;
import com.ludot.model.Piece;
import com.ludot.rules.MovementRule;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

// These tests confirm that finished pieces stay home and cannot move again.
class HomeStateTest {

    // A finished piece should not be able to move again.
    @Test
    void homeStatePreventsMovement() {
        HomeState homeState = new HomeState();
        Piece piece = new Piece(Colour.GREEN, 1);
        piece.setLocation(Location.home(Colour.GREEN));

        assertFalse(homeState.canMove(piece));
        assertEquals(0, homeState.adjustMovement(piece, 6));
    }

    // Home-state logic should block the piece before it can be treated as a normal
    // mover.
    @Test
    void homePieceIsNotTreatedAsNormalMovablePieceAgain() {
        MovementRule movementRule = new MovementRule(new BoardBuilder().build());
        Piece piece = new Piece(Colour.GREEN, 1);
        piece.setLocation(Location.home(Colour.GREEN));

        Command command = movementRule.commandForHomePathMove(piece, 1);

        assertTrue(piece.isHome());
        assertFalse(new HomeState().canMove(piece));
        assertInstanceOf(NoMoveCommand.class, command);
    }
}