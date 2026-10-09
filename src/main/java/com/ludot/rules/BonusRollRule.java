package com.ludot.rules;

import com.ludot.config.GameConfig;

// Applies the extra-turn and three-sixes rules used by LUDO-T.
public class BonusRollRule {

    // Ensures the dice value stays within the configured play range.
    private static void requireDiceValue(int diceValue) {
        if (diceValue < GameConfig.DICE_MIN || diceValue > GameConfig.DICE_MAX) {
            throw new IllegalArgumentException(
                    "Dice value must be between " + GameConfig.DICE_MIN
                            + " and " + GameConfig.DICE_MAX + ", got " + diceValue);
        }
    }

    // Returns true when this dice value grants an extra turn.
    public boolean grantsBonusForDice(int diceValue) {
        requireDiceValue(diceValue);
        return diceValue == GameConfig.BONUS_ROLL_VALUE;
    }

    // A capture also triggers the bonus-turn rule.
    public boolean grantsBonusForCapture(boolean captureHappened) {
        return captureHappened;
    }

    // The third six in a row is ignored, so the turn ends immediately.
    public boolean thirdConsecutiveSixShouldBeIgnored(int consecutiveSixCount) {
        if (consecutiveSixCount < 0)
            throw new IllegalArgumentException(
                    "Consecutive six count must not be negative, got " + consecutiveSixCount);
        return consecutiveSixCount >= GameConfig.CONSECUTIVE_SIX_LIMIT;
    }
}