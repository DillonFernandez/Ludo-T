package com.ludot.engine;

import com.ludot.board.Board;
import com.ludot.command.*;
import com.ludot.config.GameConfig;
import com.ludot.model.*;
import com.ludot.output.GameLogger;
import com.ludot.player.AbstractPlayer;
import com.ludot.rules.RuleEngine;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Runs the full game loop, applies rules, and records progress until a winner appears or the round limit is reached.
public class GameEngine {
    private final RuleEngine ruleEngine;
    private final TurnManager turnManager;
    private final GameLogger logger;
    private final int maxRounds;

    private int completedRoundCount;
    private boolean finished;
    private Colour winner;

    public GameEngine(Board board, RuleEngine ruleEngine,
                      TurnManager turnManager, GameLogger logger) {
        this(board, ruleEngine, turnManager, logger, GameConfig.MAX_ROUNDS);
    }

    GameEngine(Board board, RuleEngine ruleEngine,
               TurnManager turnManager, GameLogger logger, int maxRounds) {
        if (board == null)
            throw new IllegalArgumentException("Board must not be null.");
        if (ruleEngine == null)
            throw new IllegalArgumentException("RuleEngine must not be null.");
        if (turnManager == null)
            throw new IllegalArgumentException("TurnManager must not be null.");
        if (logger == null)
            throw new IllegalArgumentException("GameLogger must not be null.");
        if (maxRounds <= 0)
            throw new IllegalArgumentException("maxRounds must be positive.");

        this.ruleEngine = ruleEngine;
        this.turnManager = turnManager;
        this.logger = logger;
        this.maxRounds = maxRounds;
        this.completedRoundCount = 0;
        this.finished = false;
        this.winner = null;
    }

    // These accessors expose the game state that other parts may need to inspect.

    public boolean isFinished() {
        return finished;
    }

    public Colour getWinner() {
        return winner;
    }

    public int getCompletedRoundCount() {
        return completedRoundCount;
    }

    // The main loop advances turns in order until a winner is found or the round
    // cap is reached.
    public void run() {
        logger.logSimulationTitle();
        logger.logPlayersIntro();

        selectStartingPlayer();

        logger.logRoundOrder(turnManager.getRoundOrderColours());
        logger.logRoundHeader(completedRoundCount + 1);

        int startingPlayerIndex = turnManager.getCurrentPlayerIndex();

        while (!finished && completedRoundCount < maxRounds) {
            AbstractPlayer currentPlayer = turnManager.getCurrentPlayer();

            boolean bonusTurn;
            do {
                bonusTurn = executeCurrentPlayerTurn(currentPlayer);
            } while (bonusTurn && !finished);

            if (finished) {
                break;
            }

            // Advance the turn order to the next player.
            int indexBeforeAdvance = turnManager.getCurrentPlayerIndex();
            turnManager.moveToNextPlayer();

            // When the order wraps back to the starting player, one full round has
            // finished.
            if (turnManager.getCurrentPlayerIndex() == startingPlayerIndex
                    && indexBeforeAdvance != startingPlayerIndex) {
                completedRoundCount++;
                tickAlphaEffectsForCompletedRound();
                ruleEngine.mysteryRule().updateMysteryCellForNewRound();

                logAfterRoundStatus();

                if (!finished && completedRoundCount < maxRounds) {
                    logger.logRoundHeader(completedRoundCount + 1);
                }
            }
        }

        // If no winner appears before the round cap, the game ends with a limit
        // message.
        if (!finished) {
            finished = true;
            logger.logMaxRoundsReached(maxRounds);
        }
    }

    // These helpers split the turn flow into smaller, focused responsibilities.

    // A single turn asks the player for a move, applies the resulting effects, and
    // reports whether another roll is earned.
    private boolean executeCurrentPlayerTurn(AbstractPlayer currentPlayer) {
        List<Piece> preTurnBriefedPieces = collectPreTurnBriefedPieces(currentPlayer);

        Command command = currentPlayer.takeTurn();
        Piece startingPointPiece = pieceMovedFromBaseToStartingPoint(command);
        StandardPathMove standardPathMove = standardPathMoveIfNeeded(command, currentPlayer);
        BlockadeCaptureCommand blockadeCaptureCommand = blockadeCaptureCommandIfNeeded(command);
        List<Piece> piecesToSkipForNormalCapture = piecesToSkipForNormalCapture(command);
        command.execute();

        // Log the move and its consequences before checking for bonus turns or
        // captures.
        handleCommandLogging(command, standardPathMove, startingPointPiece, currentPlayer);

        checkMysteryEffects(currentPlayer);
        CaptureBonusContext bonusCaptureContext = null;

        if (blockadeCaptureCommand != null) {
            bonusCaptureContext = handleBlockadeCapture(blockadeCaptureCommand, currentPlayer);
        }

        CaptureBonusContext normalCaptureContext = checkCaptures(currentPlayer, piecesToSkipForNormalCapture);
        if (normalCaptureContext != null) {
            bonusCaptureContext = normalCaptureContext;
        }

        // Apply any briefing restrictions that were already active before this turn
        // began.
        processBriefingRestrictedRolls(preTurnBriefedPieces, currentPlayer.getLastDiceValue());

        if (currentPlayer.hasWon()) {
            finished = true;
            winner = currentPlayer.getColour();
            logger.logWinner(winner);
            return false;
        }

        if (currentPlayer.wasLastRollIgnoredDueToThirdConsecutiveSix()) {
            return false;
        }

        if (currentPlayer.wasLastRollForcedBlockBreak()) {
            return false;
        }

        boolean bonusFromDice = ruleEngine.grantsBonusForDice(currentPlayer.getLastDiceValue());
        boolean bonusFromCapture = ruleEngine.grantsBonusForCapture(bonusCaptureContext != null);

        logBonusRolls(bonusFromDice, bonusFromCapture, bonusCaptureContext, currentPlayer);

        return bonusFromDice || bonusFromCapture;
    }

    // After each full round, the log shows the current piece positions and active
    // mystery state.
    private void logAfterRoundStatus() {
        for (Colour colour : turnManager.getRoundOrderColours()) {
            AbstractPlayer player = findPlayer(colour);
            logger.logBoardBaseCount(colour, player.countPiecesOnBoard(), player.countPiecesInBase());
            logger.logStatusHeader(colour);
            for (Piece piece : player.getPieces()) {
                logger.logPieceLocation(piece);
            }
        }

        if (ruleEngine.mysteryRule().hasActiveMysteryCell()) {
            logger.logMysteryCellStatus(
                    ruleEngine.mysteryRule().getActiveMysteryLocation(),
                    ruleEngine.mysteryRule().getActiveMysteryRemainingRounds());
        }
    }

    // This advances temporary alpha and briefing effects as the round ends.
    private void tickAlphaEffectsForCompletedRound() {
        for (AbstractPlayer player : turnManager.getPlayers()) {
            for (Piece piece : player.getPieces()) {
                if (piece.isInBase() || piece.isHome() || !piece.hasAlphaEffect()) {
                    continue;
                }

                if (piece.getAlphaMovementState() != null) {
                    piece.getAlphaMovementState().onRoundPassed(piece);
                    if (piece.getAlphaEffectRemainingRounds() <= 0) {
                        piece.clearAlphaMovementState();
                    }
                }
            }
        }
        // Also tick briefing rounds for pieces under the Beta briefing effect
        for (AbstractPlayer player : turnManager.getPlayers()) {
            for (Piece piece : player.getPieces()) {
                if (piece.isInBase() || piece.isHome()) {
                    continue;
                }
                ruleEngine.mysteryRule().onRoundPassedForPiece(piece);
            }
        }
    }

    // This helper finds a player by colour and fails fast if the setup is
    // inconsistent.
    private AbstractPlayer findPlayer(Colour colour) {
        for (AbstractPlayer player : turnManager.getPlayers()) {
            if (player.getColour() == colour) {
                return player;
            }
        }
        throw new IllegalStateException("No player found for colour " + colour);
    }

    // This collects pieces that were already under briefing before the current turn
    // started.
    private List<Piece> collectPreTurnBriefedPieces(AbstractPlayer currentPlayer) {
        List<Piece> preTurnBriefedPieces = new ArrayList<>();
        for (Piece p : currentPlayer.getPieces()) {
            if (p.isInBriefing()) {
                preTurnBriefedPieces.add(p);
            }
        }
        return preTurnBriefedPieces;
    }

    // This logs the move outcome, including blocked, special, and standard path
    // events.
    private void handleCommandLogging(Command command, StandardPathMove standardPathMove,
                                      Piece startingPointPiece, AbstractPlayer currentPlayer) {
        if (command instanceof MoveBeforeBlockCommand beforeBlockCommand) {
            logger.logNoAlternativeMove(beforeBlockCommand.getPiece().getColour());
            logger.logMovedBeforeBlock(
                    beforeBlockCommand.getPiece().getColour(),
                    beforeBlockCommand.getDestination());
        } else if (command instanceof BlockedMoveCommand blockedMoveCommand) {
            Piece blockedPiece = blockedMoveCommand.piece();
            logger.logBlocked(
                    blockedPiece,
                    blockedPiece.getLocation(),
                    blockedMoveCommand.blockingLocation(),
                    blockedMoveCommand.blockingPiece().getColour(),
                    blockedMoveCommand.blockingPiece());
            logger.logNoAlternativeMove(blockedPiece.getColour());
            logger.logIgnoredThrow();
        }

        if (standardPathMove != null) {
            logger.logMove(
                    standardPathMove.piece().getColour(),
                    standardPathMove.piece(),
                    standardPathMove.from(),
                    standardPathMove.piece().getLocation(),
                    standardPathMove.value(),
                    standardPathMove.direction());
        }

        if (startingPointPiece != null) {
            logger.logMoveToStartingPoint(startingPointPiece.getColour(), startingPointPiece);
            logger.logBoardBaseCount(startingPointPiece.getColour(),
                    currentPlayer.countPiecesOnBoard(), currentPlayer.countPiecesInBase());
            ruleEngine.mysteryRule().noteStandardPathEntry();
        }
    }

    // This applies a blockade capture and returns the involved pieces for
    // bonus-roll handling.
    private CaptureBonusContext handleBlockadeCapture(BlockadeCaptureCommand bcc, AbstractPlayer currentPlayer) {
        bcc.execute();
        logger.logBlockadeCapture(
                bcc.getCapturingPieces(),
                bcc.getCaptureLocation(),
                bcc.getCapturedPieces());
        logger.logBoardBaseCount(currentPlayer.getColour(),
                currentPlayer.countPiecesOnBoard(), currentPlayer.countPiecesInBase());
        for (Piece capturedPiece : bcc.getCapturedPieces()) {
            AbstractPlayer capturedPlayer = findPlayer(capturedPiece.getColour());
            logger.logBoardBaseCount(capturedPiece.getColour(),
                    capturedPlayer.countPiecesOnBoard(), capturedPlayer.countPiecesInBase());
        }
        return new CaptureBonusContext(bcc.getCapturingPieces().get(0), bcc.getCapturedPieces().get(0));
    }

    // This applies briefing restrictions using the player's last dice roll.
    private void processBriefingRestrictedRolls(List<Piece> preTurnBriefedPieces, int lastRoll) {
        for (Piece briefed : preTurnBriefedPieces) {
            ruleEngine.mysteryRule().handleBriefingRestrictedRoll(briefed, lastRoll);
        }
    }

    // This reports why the player receives a bonus turn after the move.
    private void logBonusRolls(boolean bonusFromDice, boolean bonusFromCapture,
                               CaptureBonusContext bonusCaptureContext, AbstractPlayer currentPlayer) {
        if (bonusFromDice) {
            logger.logBonusRollForDice(currentPlayer.getColour());
        }
        if (bonusFromCapture && bonusCaptureContext != null) {
            logger.logBonusRollForCapture(
                    bonusCaptureContext.capturingPiece(),
                    bonusCaptureContext.capturedPiece());
        }
    }

    AbstractPlayer selectStartingPlayer() {
        List<AbstractPlayer> contenders = new ArrayList<>(turnManager.getPlayers());

        while (true) {
            Map<AbstractPlayer, Integer> rolls = new LinkedHashMap<>();
            int highestRoll = Integer.MIN_VALUE;

            for (AbstractPlayer player : contenders) {
                int roll = player.rollForStartingOrder();
                logger.logInitialRoll(player.getColour(), roll);
                rolls.put(player, roll);
                if (roll > highestRoll) {
                    highestRoll = roll;
                }
            }

            List<AbstractPlayer> highestPlayers = new ArrayList<>();
            for (Map.Entry<AbstractPlayer, Integer> entry : rolls.entrySet()) {
                if (entry.getValue() == highestRoll) {
                    highestPlayers.add(entry.getKey());
                }
            }

            if (highestPlayers.size() == 1) {
                AbstractPlayer startingPlayer = highestPlayers.get(0);
                turnManager.moveToPlayer(startingPlayer);
                logger.logHighestRoll(startingPlayer.getColour());
                return startingPlayer;
            }

            contenders = highestPlayers;
        }
    }

    private void checkMysteryEffects(AbstractPlayer player) {
        for (Piece piece : player.getPieces()) {
            if (piece.isInBase() || piece.isHome())
                continue;

            if (ruleEngine.mysteryRule().hasPieceLandedOnMysteryCell(piece)) {
                Command mysteryCommand = ruleEngine.mysteryRule().commandForMysteryEffect(piece);

                // If the mystery cell triggers a teleport, execute it and then apply its
                // follow-up effect.
                if (mysteryCommand instanceof TeleportCommand tc) {
                    tc.execute();
                    ruleEngine.mysteryRule().applyPostTeleportEffect(piece, tc.getEffect());
                }
            }
        }
    }

    private CaptureBonusContext checkCaptures(AbstractPlayer player, List<Piece> skipPieces) {
        CaptureBonusContext captureBonusContext = null;
        for (Piece piece : player.getPieces()) {
            if (piece.isInBase() || piece.isHome())
                continue;
            if (skipPieces != null && skipPieces.contains(piece))
                continue;

            Command captureCommand = ruleEngine.captureRule()
                    .commandIfCaptureAvailable(piece, piece.getLocation());

            // CaptureCommand holds the captured piece and location for accurate logging
            if (captureCommand instanceof CaptureCommand cc) {
                cc.execute();
                logger.logCapture(
                        cc.getCapturingPiece(),
                        cc.getCaptureLocation(),
                        cc.getCapturedPiece());
                logger.logBoardBaseCount(player.getColour(),
                        player.countPiecesOnBoard(), player.countPiecesInBase());
                AbstractPlayer capturedPlayer = findPlayer(cc.getCapturedPiece().getColour());
                logger.logBoardBaseCount(cc.getCapturedPiece().getColour(),
                        capturedPlayer.countPiecesOnBoard(), capturedPlayer.countPiecesInBase());
                captureBonusContext = new CaptureBonusContext(cc.getCapturingPiece(), cc.getCapturedPiece());
            }
        }
        return captureBonusContext;
    }

    private BlockadeCaptureCommand blockadeCaptureCommandIfNeeded(Command command) {
        if (!(command instanceof BlockMoveCommand blockMoveCommand)) {
            return null;
        }

        List<Piece> capturedBlockadePieces = ruleEngine.blockRule()
                .getBlockadePieces(blockMoveCommand.getDestination());
        if (capturedBlockadePieces.isEmpty()) {
            return null;
        }

        Command blockadeCapture = ruleEngine.captureRule()
                .commandIfBlockadeCaptureAvailable(blockMoveCommand.getPieces(),
                        capturedBlockadePieces, blockMoveCommand.getDestination());

        if (blockadeCapture instanceof BlockadeCaptureCommand bcc) {
            return bcc;
        }
        return null;
    }

    private List<Piece> piecesToSkipForNormalCapture(Command command) {
        if (!(command instanceof BlockMoveCommand blockMoveCommand)) {
            return List.of();
        }
        if (ruleEngine.blockRule().getBlockadePieces(blockMoveCommand.getDestination()).isEmpty()) {
            return List.of();
        }
        return blockMoveCommand.getPieces();
    }

    private Piece pieceMovedFromBaseToStartingPoint(Command command) {
        if (command instanceof BaseToStartingSquareCommand baseToStartingSquareCommand) {
            return baseToStartingSquareCommand.getPiece();
        }

        if (command instanceof MoveCommand moveCommand) {
            if (moveCommand.isStartingSquareMove() && moveCommand.getPiece().isInBase()) {
                return moveCommand.getPiece();
            }
        }

        if (command instanceof TeleportCommand teleportCommand) {
            if (teleportCommand.getEffect() == MysteryEffect.STARTING_SQUARE
                    && teleportCommand.getPiece().isInBase()) {
                return teleportCommand.getPiece();
            }
        }

        return null;
    }

    private StandardPathMove standardPathMoveIfNeeded(Command command, AbstractPlayer currentPlayer) {
        if (!(command instanceof MoveCommand moveCommand) || !moveCommand.isStandardPathMove()) {
            return null;
        }

        Piece piece = moveCommand.getPiece();
        Location from = piece.getLocation();
        Direction direction = piece.getDirection();
        int value = ruleEngine.movementRule()
                .adjustMovementForPiece(piece, currentPlayer.getLastDiceValue());
        return new StandardPathMove(piece, from, direction, value);
    }

    private record StandardPathMove(Piece piece, Location from, Direction direction, int value) {
    }

    private record CaptureBonusContext(Piece capturingPiece, Piece capturedPiece) {
    }
}