package com.ludot.output;

import com.ludot.model.Colour;
import com.ludot.model.Direction;
import com.ludot.model.Location;
import com.ludot.model.Piece;

import java.util.List;

// Prints formatted game updates to the console.
public class GameLogger {

    // The formatter generates the text for each log entry.
    private final GameMessageFormatter formatter;

    // This constructor injects a custom formatter.
    public GameLogger(GameMessageFormatter formatter) {
        if (formatter == null) {
            throw new IllegalArgumentException("GameMessageFormatter must not be null.");
        }
        this.formatter = formatter;
    }

    // This constructor uses the default formatter.
    public GameLogger() {
        this(new GameMessageFormatter());
    }

    // This is the only method that writes directly to the console.
    public void log(String message) {
        if (message == null) {
            throw new IllegalArgumentException("Log message must not be null.");
        }
        System.out.println(message);
    }

    // These helpers delegate to the formatter and then print the result.
    public void logPlayerPiecesIntro(Colour colour) {
        log(formatter.formatPlayerPiecesIntro(colour));
    }

    public void logPlayersIntro() {
        for (Colour colour : Colour.values()) {
            logPlayerPiecesIntro(colour);
        }
    }

    public void logSimulationTitle() {
        log(formatter.formatSimulationTitle());
    }

    public void logRoundHeader(int roundNumber) {
        log(formatter.formatRoundHeader(roundNumber));
    }

    public void logInitialRoll(Colour colour, int value) {
        log(formatter.formatInitialRoll(colour, value));
    }

    public void logHighestRoll(Colour colour) {
        log(formatter.formatHighestRoll(colour));
    }

    public void logRoundOrder(List<Colour> order) {
        log(formatter.formatRoundOrder(order));
    }

    public void logPlayerRolled(Colour colour, int value) {
        log(formatter.formatPlayerRolled(colour, value));
    }

    public void logBonusRollForDice(Colour colour) {
        log(formatter.formatBonusRollForDice(colour));
    }

    public void logThirdConsecutiveSixIgnored(Colour colour) {
        log(formatter.formatThirdConsecutiveSixIgnored(colour));
    }

    public void logThirdConsecutiveSixBlockBreak(Colour colour, Piece piece, int units,
                                                 Direction direction) {
        log(formatter.formatThirdConsecutiveSixBlockBreak(colour, piece, units, direction));
    }

    public void logBonusRollForCapture(Piece capturingPiece, Piece capturedPiece) {
        log(formatter.formatBonusRollForCapture(capturingPiece, capturedPiece));
    }

    public void logMoveToStartingPoint(Colour colour, Piece piece) {
        log(formatter.formatMoveToStartingPoint(colour, piece));
    }

    public void logBoardBaseCount(Colour colour, int onBoardCount, int baseCount) {
        log(formatter.formatBoardBaseCount(colour, onBoardCount, baseCount));
    }

    public void logMove(Colour colour, Piece piece, Location from, Location to,
                        int value, Direction direction) {
        log(formatter.formatMove(colour, piece, from, to, value, direction));
    }

    public void logBlocked(Piece blockedPiece, Location from, Location to,
                           Colour blockingColour, Piece blockingPiece) {
        log(formatter.formatBlocked(blockedPiece, from, to, blockingColour, blockingPiece));
    }

    public void logNoAlternativeMove(Colour colour) {
        log(formatter.formatNoAlternativeMove(colour));
    }

    public void logIgnoredThrow() {
        log(formatter.formatIgnoredThrow());
    }

    public void logMovedBeforeBlock(Colour colour, Location location) {
        log(formatter.formatMovedBeforeBlock(colour, location));
    }

    public void logCapture(Piece capturingPiece, Location location, Piece capturedPiece) {
        log(formatter.formatCapture(capturingPiece, location, capturedPiece));
    }

    public void logBlockadeCapture(List<Piece> capturingPieces, Location location,
                                   List<Piece> capturedPieces) {
        log(formatter.formatBlockadeCapture(capturingPieces, location, capturedPieces));
    }

    public void logStatusHeader(Colour colour) {
        log(formatter.formatStatusHeader(colour));
    }

    public void logPieceLocation(Piece piece) {
        log(formatter.formatPieceLocation(piece));
    }

    public void logMysteryCellStatus(Location location, int remainingRounds) {
        log(formatter.formatMysteryCellStatus(location, remainingRounds));
    }

    public void logMysteryCellSpawned(Location location) {
        log(formatter.formatMysteryCellSpawned(location));
    }

    public void logMysteryTeleport(Colour colour, Location destination) {
        log(formatter.formatMysteryTeleport(colour, destination));
    }

    public void logTeleportedTo(Piece piece, String destination) {
        log(formatter.formatTeleportedTo(piece, destination));
    }

    public void logEnergized(Piece piece) {
        log(formatter.formatEnergized(piece));
    }

    public void logSick(Piece piece) {
        log(formatter.formatSick(piece));
    }

    public void logBriefing(Piece piece) {
        log(formatter.formatBriefing(piece));
    }

    public void logBriefingRestrictedTeleport(Piece piece) {
        log(formatter.formatBriefingRestrictedTeleport(piece));
    }

    public void logGammaClockwiseChanged(Piece piece) {
        log(formatter.formatGammaClockwiseChanged(piece));
    }

    public void logGammaCounterClockwiseToBeta(Piece piece) {
        log(formatter.formatGammaCounterClockwiseToBeta(piece));
    }

    public void logWinner(Colour colour) {
        log(formatter.formatWinner(colour));
    }

    // This message reports that the simulation stopped because the round limit was
    // reached.
    public void logMaxRoundsReached(int maxRounds) {
        log(formatter.formatMaxRoundsReached(maxRounds));
    }
}