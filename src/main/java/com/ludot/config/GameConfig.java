package com.ludot.config;

import com.ludot.exception.GameConfigurationException;

// Stores the fixed game settings used by the LUDO-T rules and board logic.
public final class GameConfig {

    // These constants define the main path length and each player's home path
    // length.
    public static final int STANDARD_PATH_SIZE = 52;
    public static final int HOME_PATH_SIZE = 5;
    // These constants define the number of players and pieces per player.
    public static final int PIECES_PER_PLAYER = 4;
    public static final int PLAYER_COUNT = 4;
    // These values define the dice range and the rules that trigger special
    // movement behaviour.
    public static final int DICE_MIN = 1;
    public static final int DICE_MAX = 6;
    public static final int DICE_SIDES = 6;
    public static final int ROLL_TO_ENTER = 6;
    public static final int BONUS_ROLL_VALUE = 6;
    public static final int CONSECUTIVE_SIX_LIMIT = 3;
    // This limit prevents unbounded simulation runs from continuing forever.
    public static final int MAX_ROUNDS = 10000;
    // These values control when mystery cells appear and how long they remain
    // active.
    public static final int MYSTERY_CELL_APPEARS_AFTER_ROUNDS = 2;
    public static final int MYSTERY_CELL_LASTS_ROUNDS = 4;
    public static final int ACTIVE_MYSTERY_CELL_COUNT = 1;
    // These offsets place the Alpha, Beta, and Gamma special squares relative to a
    // known path index.
    public static final int ALPHA_OFFSET_FROM_YELLOW_APPROACH = 9;
    public static final int BETA_OFFSET_FROM_YELLOW_APPROACH = 27;
    public static final int GAMMA_OFFSET_FROM_YELLOW_APPROACH = 46;
    // These values define the duration and limits used by special rule effects.
    public static final int ALPHA_EFFECT_DURATION_ROUNDS = 4;
    public static final int BETA_BRIEFING_DURATION_ROUNDS = 4;
    public static final int BETA_RESTRICTED_ROLL = 3;
    public static final int BETA_RESTRICTED_ROLL_LIMIT = 3;
    // These multipliers and divisors change movement speed for special piece
    // states.
    public static final int ENERGIZED_MOVEMENT_MULTIPLIER = 2;
    public static final int SICK_MOVEMENT_DIVISOR = 2;
    // The singleton keeps one shared copy of the configuration values.
    private static final GameConfig INSTANCE = new GameConfig();

    private GameConfig() {
    }

    public static GameConfig getInstance() {
        return INSTANCE;
    }

    // Validation runs at startup so bad configuration values fail early and
    // clearly.
    public void validate() {
        validatePositive("STANDARD_PATH_SIZE", STANDARD_PATH_SIZE);
        validatePositive("HOME_PATH_SIZE", HOME_PATH_SIZE);
        validatePositive("PIECES_PER_PLAYER", PIECES_PER_PLAYER);
        validatePositive("PLAYER_COUNT", PLAYER_COUNT);
        validatePositive("CONSECUTIVE_SIX_LIMIT", CONSECUTIVE_SIX_LIMIT);
        validatePositive("MAX_ROUNDS", MAX_ROUNDS);
        validatePositive("ACTIVE_MYSTERY_CELL_COUNT", ACTIVE_MYSTERY_CELL_COUNT);
        validatePositive("ENERGIZED_MOVEMENT_MULTIPLIER", ENERGIZED_MOVEMENT_MULTIPLIER);
        validatePositive("SICK_MOVEMENT_DIVISOR", SICK_MOVEMENT_DIVISOR);
        validateDiceRange(DICE_MIN, DICE_MAX, DICE_SIDES);
        validateValueInRange("ROLL_TO_ENTER", ROLL_TO_ENTER, DICE_MIN, DICE_MAX);
    }

    // This helper ensures key settings stay positive and usable.
    private void validatePositive(String name, int value) {
        if (value <= 0) {
            throw new GameConfigurationException(name + " must be positive, got " + value);
        }
    }

    // This helper verifies that the dice constants are internally consistent.
    private void validateDiceRange(int min, int max, int sides) {
        if (min < 1 || max < min || sides != (max - min + 1)) {
            String msg = String.format(
                    "Dice constants are inconsistent: MIN=%d MAX=%d SIDES=%d",
                    min, max, sides);
            throw new GameConfigurationException(msg);
        }
    }

    // This helper confirms that a value stays within the expected range.
    private void validateValueInRange(String name, int value, int min, int max) {
        if (value < min || value > max) {
            throw new GameConfigurationException(
                    name + " must be within range " + min + " to " + max + ", got " + value);
        }
    }
}