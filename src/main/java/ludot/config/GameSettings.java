// Provides shared game constants and the board locations assigned to each colour.
package ludot.config;

import ludot.model.Colour;

public final class GameSettings {
    public static final int ALPHA_DESTINATION = 0;
    public static final int BETA_DESTINATION = 1;
    public static final int GAMMA_DESTINATION = 2;
    public static final int BASE_DESTINATION = 3;
    public static final int START_DESTINATION = 4;
    public static final int APPROACH_DESTINATION = 5;

    private static final GameSettings INSTANCE = new GameSettings();

    private final int playerCount = 4;
    private final int piecesPerPlayer = 4;
    private final int standardCells = 52;
    private final int homeStraightCells = 5;
    private final int diceFaces = 6;
    private final int maximumConsecutiveSixes = 3;
    private final int maximumRounds = 10_000;
    private final int roundsPerUpdate = 100;
    private final int effectRounds = 4;
    private final int mysteryDelay = 2;
    private final int mysteryRounds = 4;
    private final int base = -1;
    private final int briefingRoll = 3;
    private final int consecutiveBriefingRolls = 2;
    private final int mysteryDestinations = 6;
    private final int coinSides = 2;
    private final int speedFactor = 2;
    private final int requiredApproachVisits = 2;
    private final int requiredCaptures = 1;

    private GameSettings() {
    }

    public static GameSettings getInstance() {
        return INSTANCE;
    }

    public int getPlayerCount() {
        return playerCount;
    }

    public int getPiecesPerPlayer() {
        return piecesPerPlayer;
    }

    public int getStandardCells() {
        return standardCells;
    }

    public int getHomeStraightCells() {
        return homeStraightCells;
    }

    public int getHome() {
        return standardCells + homeStraightCells;
    }

    public int getBase() {
        return base;
    }

    public int getDiceFaces() {
        return diceFaces;
    }

    public int getMaximumConsecutiveSixes() {
        return maximumConsecutiveSixes;
    }

    public int getMaximumRounds() {
        return maximumRounds;
    }

    public int getRoundsPerUpdate() {
        return roundsPerUpdate;
    }

    public int getEffectRounds() {
        return effectRounds;
    }

    public int getMysteryDelay() {
        return mysteryDelay;
    }

    public int getMysteryRounds() {
        return mysteryRounds;
    }

    public int getBriefingRoll() {
        return briefingRoll;
    }

    public int getConsecutiveBriefingRolls() {
        return consecutiveBriefingRolls;
    }

    public int getMysteryDestinations() {
        return mysteryDestinations;
    }

    public int getCoinSides() {
        return coinSides;
    }

    public int getSpeedFactor() {
        return speedFactor;
    }

    public int getRequiredApproachVisits() {
        return requiredApproachVisits;
    }

    public int getRequiredCaptures() {
        return requiredCaptures;
    }

    public int getStartingCell(Colour colour) {
        switch (colour) {
            case YELLOW:
                return 0;
            case BLUE:
                return 13;
            case RED:
                return 26;
            case GREEN:
                return 39;
            default:
                throw new IllegalArgumentException("Unknown player colour.");
        }
    }

    public int getApproachCell(Colour colour) {
        return wrapCell(getStartingCell(colour) - 2);
    }

    public int getAlphaCell() {
        return wrapCell(getApproachCell(Colour.YELLOW) + 9);
    }

    public int getBetaCell() {
        return wrapCell(getApproachCell(Colour.YELLOW) + 27);
    }

    public int getGammaCell() {
        return wrapCell(getApproachCell(Colour.YELLOW) + 46);
    }

    public int wrapCell(int cell) {
        return Math.floorMod(cell, standardCells);
    }
}
