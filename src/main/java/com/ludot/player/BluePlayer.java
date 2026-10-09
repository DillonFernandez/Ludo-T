package com.ludot.player;

import com.ludot.board.Board;
import com.ludot.command.BaseToStartingSquareCommand;
import com.ludot.command.Command;
import com.ludot.command.NoMoveCommand;
import com.ludot.config.GameConfig;
import com.ludot.model.*;
import com.ludot.output.GameLogger;
import com.ludot.random.CoinToss;
import com.ludot.random.Dice;
import com.ludot.rules.RuleEngine;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

// Chooses Blue's next move by scanning pieces in a rotating order.
public class BluePlayer extends AbstractPlayer {

    private final Random random;
    // Tracks the next starting position for the rotating scan.
    private int nextPieceIndex;

    // The constructor initializes the rotating search and random choice helper.
    public BluePlayer(Colour colour, List<Piece> pieces, Board board,
                      RuleEngine ruleEngine, Dice dice,
                      CoinToss coinToss, GameLogger logger) {
        this(colour, pieces, board, ruleEngine, dice, coinToss, logger, new Random());
    }

    public BluePlayer(Colour colour, List<Piece> pieces, Board board,
                      RuleEngine ruleEngine, Dice dice,
                      CoinToss coinToss, GameLogger logger, Random random) {
        super(colour, pieces, board, ruleEngine, dice, coinToss, logger);
        this.nextPieceIndex = 0;
        this.random = random != null ? random : new Random();
    }

    // This turn logic scans pieces in rotation and picks the first legal move.
    @Override
    protected Command selectMove(int diceValue) {
        List<Piece> cyclicPieces = getPieces();
        if (cyclicPieces.isEmpty()) {
            return new NoMoveCommand();
        }

        if (nextPieceIndex >= cyclicPieces.size()) {
            nextPieceIndex = 0;
        }

        int startIndex = nextPieceIndex;
        int size = cyclicPieces.size();

        List<CandidateMove> currentCandidates = candidateMovesForPiece(cyclicPieces.get(startIndex), diceValue,
                startIndex);
        if (!currentCandidates.isEmpty()) {
            CandidateMove chosen = chooseRandomCandidate(currentCandidates);
            nextPieceIndex = (chosen.index() + 1) % size;
            return chosen.command();
        }

        List<CandidateMove> fallbackCandidates = new ArrayList<>();
        for (int offset = 1; offset < size; offset++) {
            int index = (startIndex + offset) % size;
            fallbackCandidates.addAll(candidateMovesForPiece(cyclicPieces.get(index), diceValue, index));
        }

        if (!fallbackCandidates.isEmpty()) {
            CandidateMove chosen = chooseRandomCandidate(fallbackCandidates);
            nextPieceIndex = (chosen.index() + 1) % size;
            return chosen.command();
        }

        return new NoMoveCommand();
    }

    private List<CandidateMove> candidateMovesForPiece(Piece piece, int diceValue, int index) {
        List<CandidateMove> candidates = new ArrayList<>();
        if (piece == null || piece.isHome()) {
            return candidates;
        }

        Command command;
        boolean landsOnMysteryCell = false;

        if (piece.isInBase()) {
            command = tryMovePieceFromBase(piece, diceValue);
        } else if (piece.getLocation().getType() == LocationType.HOME_PATH) {
            command = tryHomePathMove(piece, diceValue);
        } else if (piece.getDirection() == null) {
            return candidates;
        } else {
            if (piece.getLocation().isOnStandardRing()) {
                Location activeMysteryLocation = ruleEngine.mysteryRule().getActiveMysteryLocation();
                if (activeMysteryLocation != null) {
                    try {
                        Location destination = ruleEngine.movementRule()
                                .calculateStandardDestination(piece, diceValue);
                        landsOnMysteryCell = destination.getIndex() == activeMysteryLocation.getIndex();
                    } catch (IllegalArgumentException e) {
                        String msg = "Could not calculate destination for piece "
                                + piece + ": " + e.getMessage();
                        logger.log(msg);
                        return candidates;
                    }
                }
            }
            command = tryStandardMove(piece, diceValue);
        }

        if (isCommandAvailable(command)) {
            candidates.add(new CandidateMove(command, index, landsOnMysteryCell));
        }
        return filterByMysteryPreference(piece, candidates);
    }

    private List<CandidateMove> filterByMysteryPreference(Piece piece,
                                                          List<CandidateMove> candidates) {
        if (piece == null || piece.getDirection() == null || candidates.isEmpty()) {
            return candidates;
        }

        if (ruleEngine.mysteryRule().getActiveMysteryLocation() == null) {
            return candidates;
        }

        boolean hasMysteryCandidate = candidates.stream()
                .anyMatch(CandidateMove::landsOnMysteryCell);
        if (!hasMysteryCandidate) {
            return candidates;
        }

        if (piece.getDirection() == Direction.COUNTER_CLOCKWISE) {
            return candidates.stream()
                    .filter(CandidateMove::landsOnMysteryCell)
                    .toList();
        }

        if (piece.getDirection() == Direction.CLOCKWISE) {
            List<CandidateMove> nonMysteryCandidates = candidates.stream()
                    .filter(candidate -> !candidate.landsOnMysteryCell)
                    .toList();
            if (!nonMysteryCandidates.isEmpty()) {
                return nonMysteryCandidates;
            }
        }

        return candidates;
    }

    private CandidateMove chooseRandomCandidate(List<CandidateMove> candidates) {
        if (candidates.isEmpty()) {
            throw new IllegalArgumentException("No candidate moves available.");
        }

        return candidates.get(random.nextInt(candidates.size()));
    }

    // A piece in base can leave only on the special six roll and only when the
    // rules allow it.
    private Command tryMovePieceFromBase(Piece piece, int diceValue) {
        if (piece == null || !piece.isInBase()) {
            return new NoMoveCommand();
        }
        if (diceValue != GameConfig.BONUS_ROLL_VALUE) {
            return new NoMoveCommand();
        }
        if (!ruleEngine.canMoveFromBase(piece, diceValue)) {
            return new NoMoveCommand();
        }
        return new BaseToStartingSquareCommand(board, piece, coinToss);
    }

    private record CandidateMove(Command command, int index, boolean landsOnMysteryCell) {
    }
}
