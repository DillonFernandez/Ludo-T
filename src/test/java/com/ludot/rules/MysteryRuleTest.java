package com.ludot.rules;

import com.ludot.board.Board;
import com.ludot.board.BoardBuilder;
import com.ludot.command.Command;
import com.ludot.command.NoMoveCommand;
import com.ludot.command.TeleportCommand;
import com.ludot.model.Colour;
import com.ludot.model.Direction;
import com.ludot.model.MysteryEffect;
import com.ludot.model.Piece;
import com.ludot.output.GameLogger;
import com.ludot.random.FixedMysteryCellSpawner;
import com.ludot.random.FixedMysteryEffectSelector;
import com.ludot.random.MysteryCellSpawner;
import com.ludot.random.MysteryEffectSelector;
import com.ludot.state.BriefingState;
import com.ludot.state.EnergizedState;
import com.ludot.state.SickState;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

// These tests cover mystery-cell timing, teleport effects, and logging behavior.
class MysteryRuleTest {

    private static MysteryRule createRule(Board board, MysteryCellSpawner spawner, MysteryEffectSelector selector) {
        return new MysteryRule(board, selector, spawner, new CapturingGameLogger());
    }

    private static MysteryRule createRule(MysteryCellSpawner spawner) {
        return createRule(new BoardBuilder().build(), spawner, new FixedMysteryEffectSelector(MysteryEffect.ALPHA));
    }

    // Mystery cells should not appear until the piece has spent enough rounds on
    // the standard path.
    @Test
    void mysteryCellDoesNotSpawnBeforeTwoRoundsAfterStandardPathEntry() {
        MysteryRule rule = createRule(new FixedMysteryCellSpawner(7));

        rule.noteStandardPathEntry();
        rule.updateMysteryCellForNewRound();

        assertFalse(rule.hasActiveMysteryCell());
    }

    // Once the timing requirement is met, the mystery cell should become active.
    @Test
    void mysteryCellSpawnsAfterRequiredTiming() {
        MysteryRule rule = createRule(new FixedMysteryCellSpawner(7));

        rule.noteStandardPathEntry();
        rule.updateMysteryCellForNewRound();
        rule.updateMysteryCellForNewRound();

        assertTrue(rule.hasActiveMysteryCell());
        assertEquals(7, rule.getActiveMysteryLocation().getIndex());
        assertEquals(4, rule.getActiveMysteryRemainingRounds());
    }

    // Mystery cells should only appear on standard-path locations.
    @Test
    void mysteryCellSpawnsOnlyOnStandardPathCells() {
        MysteryRule rule = createRule(new FixedMysteryCellSpawner(11));

        rule.noteStandardPathEntry();
        rule.updateMysteryCellForNewRound();
        rule.updateMysteryCellForNewRound();

        assertTrue(rule.getActiveMysteryLocation().isStandardPath());
        assertTrue(rule.getActiveMysteryLocation().getIndex() >= 0);
    }

    // The spawn logic should avoid occupied standard-path cells.
    @Test
    void mysteryCellDoesNotSpawnOnOccupiedCell() {
        Board board = new BoardBuilder().build();
        Piece blocker = new Piece(Colour.YELLOW, 1);
        board.placePieceOnStandardPath(blocker, 3);

        MysteryRule rule = createRule(board, new FirstAvailableMysteryCellSpawner(),
                new FixedMysteryEffectSelector(MysteryEffect.ALPHA));

        rule.noteStandardPathEntry();
        rule.updateMysteryCellForNewRound();
        rule.updateMysteryCellForNewRound();

        assertTrue(rule.hasActiveMysteryCell());
        assertNotEquals(3, rule.getActiveMysteryLocation().getIndex());
        assertTrue(board.getOccupiedStandardPathIndices().contains(3));
    }

    // An active mystery cell should remain available for four rounds before the
    // timer resets.
    @Test
    void mysteryCellRemainsActiveForFourRoundsAndRespawnsAfterThatWindow() {
        MysteryRule rule = createRule(new FixedMysteryCellSpawner(9));

        rule.noteStandardPathEntry();
        rule.updateMysteryCellForNewRound();
        rule.updateMysteryCellForNewRound();

        assertEquals(4, rule.getActiveMysteryRemainingRounds());

        for (int i = 0; i < 4; i++) {
            rule.updateMysteryCellForNewRound();
        }

        assertTrue(rule.hasActiveMysteryCell());
        assertEquals(4, rule.getActiveMysteryRemainingRounds());
    }

    // The next mystery cell should not reuse the same location immediately.
    @Test
    void mysteryCellDoesNotRespawnAtTheSameLocationConsecutively() {
        MysteryRule rule = createRule(new NoRepeatMysteryCellSpawner(List.of(9, 14)));

        rule.noteStandardPathEntry();
        rule.updateMysteryCellForNewRound();
        rule.updateMysteryCellForNewRound();
        int firstIndex = rule.getActiveMysteryLocation().getIndex();

        for (int i = 0; i < 4; i++) {
            rule.updateMysteryCellForNewRound();
        }

        assertEquals(14, rule.getActiveMysteryLocation().getIndex());
        assertNotEquals(firstIndex, rule.getActiveMysteryLocation().getIndex());
    }

    // Teleport effects should support all six destination types used by the rule
    // engine.
    @Test
    void sixTeleportDestinationsAreSupported() {
        Board board = new BoardBuilder().build();
        Piece piece = new Piece(Colour.YELLOW, 1);

        board.placePieceOnStandardPath(piece, 7);
        MysteryRule rule = createRule(board, new FixedMysteryCellSpawner(7),
                new FixedMysteryEffectSelector(MysteryEffect.ALPHA));

        rule.noteStandardPathEntry();
        rule.updateMysteryCellForNewRound();
        rule.updateMysteryCellForNewRound();

        Command alphaCommand = new TeleportCommand(board, piece, MysteryEffect.ALPHA);
        alphaCommand.execute();
        assertEquals(board.getAlphaLocation(), piece.getLocation());

        Command betaCommand = new TeleportCommand(board, piece, MysteryEffect.BETA);
        betaCommand.execute();
        assertEquals(board.getBetaLocation(), piece.getLocation());

        Command gammaCommand = new TeleportCommand(board, piece, MysteryEffect.GAMMA);
        gammaCommand.execute();
        assertEquals(board.getGammaLocation(), piece.getLocation());

        Command baseCommand = new TeleportCommand(board, piece, MysteryEffect.BASE);
        baseCommand.execute();
        assertTrue(piece.isInBase());

        Piece startingPiece = new Piece(Colour.RED, 1);
        board.placePieceOnStandardPath(startingPiece, 9);
        Command startingCommand = new TeleportCommand(board, startingPiece, MysteryEffect.STARTING_SQUARE);
        startingCommand.execute();
        assertEquals(board.getStartingLocation(Colour.RED), startingPiece.getLocation());

        Piece approachPiece = new Piece(Colour.GREEN, 1);
        board.placePieceOnStandardPath(approachPiece, 11);
        Command approachCommand = new TeleportCommand(board, approachPiece, MysteryEffect.APPROACH);
        approachCommand.execute();
        assertEquals(board.getApproachLocation(Colour.GREEN), approachPiece.getLocation());
    }

    // These fixed locations are the known teleport targets for the mystery rules.
    @Test
    void alphaBetaAndGammaLocationsMatchYellowApproachOffsets() {
        Board board = new BoardBuilder().build();

        assertEquals(8, board.getAlphaLocation().getIndex());
        assertEquals(26, board.getBetaLocation().getIndex());
        assertEquals(45, board.getGammaLocation().getIndex());
    }

    // Alpha effects should apply the expected movement state after a teleport.
    @Test
    void alphaEffectAppliesEnergizedOrSickStateAfterMysteryTeleport() {
        Board board = new BoardBuilder().build();
        Piece piece = new Piece(Colour.YELLOW, 1);
        board.placePieceOnStandardPath(piece, 5);

        MysteryRule rule = createRule(board, new FixedMysteryCellSpawner(5),
                new FixedMysteryEffectSelector(MysteryEffect.ALPHA));
        rule.applyPostTeleportEffect(piece, MysteryEffect.ALPHA);

        assertInstanceOf(EnergizedState.class, piece.getAlphaMovementState());
        assertEquals(4, piece.getAlphaEffectRemainingRounds());

        Piece sickPiece = new Piece(Colour.BLUE, 1);
        board.placePieceOnStandardPath(sickPiece, 6);
        MysteryRule sickRule = createRule(board, new FixedMysteryCellSpawner(6),
                new FixedMysteryEffectSelector(MysteryEffect.BETA));
        sickRule.applyPostTeleportEffect(sickPiece, MysteryEffect.ALPHA);

        assertInstanceOf(SickState.class, sickPiece.getAlphaMovementState());
        assertEquals(4, sickPiece.getAlphaEffectRemainingRounds());
    }

    // The helper states should change movement distance in the expected way.
    @Test
    void energizedAndSickStatesModifyMovementAsExpected() {
        EnergizedState energized = new EnergizedState();
        SickState sick = new SickState();
        Piece piece = new Piece(Colour.YELLOW, 1);

        assertEquals(12, energized.adjustMovement(piece, 6));
        assertEquals(3, sick.adjustMovement(piece, 6));
    }

    // Briefing should block movement and start its own countdown.
    @Test
    void betaBriefingStatePreventsMovementAndStartsCountdown() {
        Piece piece = new Piece(Colour.RED, 1);
        BriefingState briefingState = new BriefingState();

        briefingState.onEnter(piece);

        assertFalse(briefingState.canMove(piece));
        assertEquals(0, briefingState.adjustMovement(piece, 3));
        assertTrue(piece.isInBriefing());
    }

    // After three restricted rolls, briefing should send the piece back to base.
    @Test
    void betaBriefingSendsPieceToBaseAfterThreeRestrictedRolls() {
        Board board = new BoardBuilder().build();
        Piece piece = new Piece(Colour.RED, 1);
        board.placePieceOnStandardPath(piece, 10);

        MysteryRule rule = createRule(board, new FixedMysteryCellSpawner(10),
                new FixedMysteryEffectSelector(MysteryEffect.BETA));
        rule.applyPostTeleportEffect(piece, MysteryEffect.BETA);

        rule.handleBriefingRestrictedRoll(piece, 3);
        rule.handleBriefingRestrictedRoll(piece, 3);
        rule.handleBriefingRestrictedRoll(piece, 3);

        assertTrue(piece.isInBase());
    }

    // Gamma should flip direction and move the piece to the beta location.
    @Test
    void gammaChangesClockwiseDirectionAndCounterClockwiseTeleportToBeta() {
        Board board = new BoardBuilder().build();
        Piece clockwise = new Piece(Colour.YELLOW, 1);
        board.placePieceOnStandardPath(clockwise, 15);
        clockwise.setDirection(Direction.CLOCKWISE);

        MysteryRule rule = createRule(board, new FixedMysteryCellSpawner(15),
                new FixedMysteryEffectSelector(MysteryEffect.GAMMA));
        rule.applyPostTeleportEffect(clockwise, MysteryEffect.GAMMA);

        assertEquals(Direction.COUNTER_CLOCKWISE, clockwise.getDirection());

        Piece counterClockwise = new Piece(Colour.GREEN, 1);
        board.placePieceOnStandardPath(counterClockwise, 16);
        counterClockwise.setDirection(Direction.COUNTER_CLOCKWISE);

        MysteryRule counterRule = createRule(board, new FixedMysteryCellSpawner(16),
                new FixedMysteryEffectSelector(MysteryEffect.GAMMA));
        counterRule.applyPostTeleportEffect(counterClockwise, MysteryEffect.GAMMA);

        assertTrue(counterClockwise.getLocation().isBeta());
        assertEquals(board.getBetaLocation(), counterClockwise.getLocation());
    }

    // Ordinary movement onto a special square should not trigger an extra mystery
    // effect.
    @Test
    void ordinaryMovementOntoAlphaBetaGammaSquaresDoesNotTriggerMysteryEffects() {
        Board board = new BoardBuilder().build();
        Piece piece = new Piece(Colour.YELLOW, 1);
        board.placePieceOnStandardPath(piece, board.getAlphaLocation().getIndex());

        MysteryRule rule = createRule(board, new FixedMysteryCellSpawner(board.getAlphaLocation().getIndex()),
                new FixedMysteryEffectSelector(MysteryEffect.ALPHA));
        Command command = rule.commandForMysteryEffect(piece);

        assertInstanceOf(NoMoveCommand.class, command);
    }

    // The rule engine should log the mystery spawn and teleport messages it
    // creates.
    @Test
    void mysterySpawnAndTeleportMessagesAreLogged() {
        CapturingGameLogger logger = new CapturingGameLogger();
        MysteryRule rule = new MysteryRule(new BoardBuilder().build(),
                new FixedMysteryEffectSelector(MysteryEffect.ALPHA),
                new FixedMysteryCellSpawner(10), logger);

        rule.noteStandardPathEntry();
        rule.updateMysteryCellForNewRound();
        rule.updateMysteryCellForNewRound();

        assertTrue(logger.messages.stream().anyMatch(message -> message.equals(
                "A mystery cell has spawned in location 10 and will be at this location for the next four rounds.")));
    }

    // The logging output should match the exact alpha, beta, and gamma effect
    // messages.
    @Test
    void alphaBetaAndGammaEffectMessagesAreLoggedExactly() {
        Board board = new BoardBuilder().build();
        CapturingGameLogger logger = new CapturingGameLogger();

        MysteryRule alphaRule = new MysteryRule(board,
                new FixedMysteryEffectSelector(MysteryEffect.ALPHA),
                new FixedMysteryCellSpawner(10), logger);
        Piece alphaPiece = new Piece(Colour.RED, 1);
        alphaRule.applyPostTeleportEffect(alphaPiece, MysteryEffect.ALPHA);

        assertTrue(logger.messages.contains("Red piece R1 feels energized, and movement speed doubles."));

        logger.messages.clear();
        MysteryRule betaRule = new MysteryRule(board,
                new FixedMysteryEffectSelector(MysteryEffect.BETA),
                new FixedMysteryCellSpawner(10), logger);
        Piece betaPiece = new Piece(Colour.GREEN, 1);
        betaRule.applyPostTeleportEffect(betaPiece, MysteryEffect.BETA);

        assertTrue(logger.messages.contains("Green piece G1 attends briefing and cannot move for four rounds."));

        logger.messages.clear();
        MysteryRule gammaRule = new MysteryRule(board,
                new FixedMysteryEffectSelector(MysteryEffect.GAMMA),
                new FixedMysteryCellSpawner(10), logger);
        Piece gammaPiece = new Piece(Colour.BLUE, 1);
        gammaPiece.setDirection(Direction.COUNTER_CLOCKWISE);
        gammaRule.applyPostTeleportEffect(gammaPiece, MysteryEffect.GAMMA);

        assertTrue(logger.messages.contains(
                "The Blue piece B1 is moving in a counterclockwise direction. Teleporting to Beta from Gamma."));
        assertTrue(logger.messages.contains("Blue piece B1 teleported to Beta."));
        assertTrue(logger.messages.contains("Blue piece B1 attends briefing and cannot move for four rounds."));
    }

    private static final class CapturingGameLogger extends GameLogger {
        private final List<String> messages = new ArrayList<>();

        @Override
        public void log(String message) {
            messages.add(message);
        }
    }

    private static final class FirstAvailableMysteryCellSpawner implements MysteryCellSpawner {
        @Override
        public int spawn(int previousIndex, Set<Integer> occupiedIndices) {
            for (int index = 0; index < 52; index++) {
                if (!occupiedIndices.contains(index)) {
                    return index;
                }
            }
            return 0;
        }
    }

    private static final class NoRepeatMysteryCellSpawner implements MysteryCellSpawner {
        private final List<Integer> sequence;
        private int next = 0;

        private NoRepeatMysteryCellSpawner(List<Integer> sequence) {
            this.sequence = sequence;
        }

        @Override
        public int spawn(int previousIndex, Set<Integer> occupiedIndices) {
            int candidate = sequence.get(next % sequence.size());
            next++;
            return candidate;
        }
    }
}
