package com.ludot.rules;

import com.ludot.model.Location;
import com.ludot.model.Piece;

import java.util.List;

// Central entry point for the main LUDO-T rules used during a turn.
public record RuleEngine(MovementRule movementRule, CaptureRule captureRule, BlockRule blockRule,
                         BonusRollRule bonusRollRule, MysteryRule mysteryRule, WinCondition winCondition) {

    public RuleEngine {
        if (movementRule == null)
            throw new IllegalArgumentException("MovementRule must not be null.");
        if (captureRule == null)
            throw new IllegalArgumentException("CaptureRule must not be null.");
        if (blockRule == null)
            throw new IllegalArgumentException("BlockRule must not be null.");
        if (bonusRollRule == null)
            throw new IllegalArgumentException("BonusRollRule must not be null.");
        if (mysteryRule == null)
            throw new IllegalArgumentException("MysteryRule must not be null.");
        if (winCondition == null)
            throw new IllegalArgumentException("WinCondition must not be null.");

    }

    // Exposes the rule helpers used by the rest of the game.

    // Provides the turn-time checks used by the game logic.

    public boolean canMoveFromBase(Piece piece, int diceValue) {
        return movementRule.canMoveFromBase(piece, diceValue);
    }

    public boolean isBlocked(Piece piece, int steps) {
        return blockRule.isBlockedOnPath(
                piece,
                movementRule.adjustMovementForPiece(piece, steps));
    }

    public boolean canCapture(Piece piece, Location location) {
        return captureRule.canCapture(piece, location);
    }

    public boolean hasWon(List<Piece> pieces) {
        return winCondition.hasPlayerWon(pieces);
    }

    public boolean grantsBonusForDice(int diceValue) {
        return bonusRollRule.grantsBonusForDice(diceValue);
    }

    public boolean grantsBonusForCapture(boolean captureHappened) {
        return bonusRollRule.grantsBonusForCapture(captureHappened);
    }

    public boolean thirdConsecutiveSixShouldBeIgnored(int consecutiveSixCount) {
        return bonusRollRule.thirdConsecutiveSixShouldBeIgnored(consecutiveSixCount);
    }
}