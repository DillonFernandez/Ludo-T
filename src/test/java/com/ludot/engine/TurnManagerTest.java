package com.ludot.engine;

import com.ludot.board.Board;
import com.ludot.board.BoardBuilder;
import com.ludot.factory.PlayerFactory;
import com.ludot.model.Colour;
import com.ludot.output.GameLogger;
import com.ludot.player.AbstractPlayer;
import com.ludot.random.CoinFace;
import com.ludot.random.Dice;
import com.ludot.random.StandardMysteryCellSpawner;
import com.ludot.random.StandardMysteryEffectSelector;
import com.ludot.rules.*;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

// These tests confirm the starting player and the clockwise wrap-around order for turns.
class TurnManagerTest {

    // Creates only the rule objects needed to build a minimal player setup.
    private static RuleEngine createMinimalRuleEngine(Board board) {
        return new RuleEngine(new MovementRule(board), new CaptureRule(board), new BlockRule(board),
                new BonusRollRule(), new MysteryRule(board, new StandardMysteryEffectSelector(1L),
                new StandardMysteryCellSpawner(1L), new NoOpLogger()),
                new WinCondition());
    }

    // Confirms the round order starts from the current player and wraps clockwise.
    @Test
    void roundOrderStartsFromCurrentPlayerAndWrapsClockwise() {
        Board board = new BoardBuilder().build();
        PlayerFactory playerFactory = new PlayerFactory(board, createMinimalRuleEngine(board), new FixedDice(),
                () -> CoinFace.HEADS, new NoOpLogger());
        List<AbstractPlayer> players = new ArrayList<>();
        for (Colour colour : List.of(Colour.RED, Colour.GREEN, Colour.YELLOW, Colour.BLUE)) {
            players.add(playerFactory.createPlayer(colour, new ArrayList<>(board.getHomeArea(colour).pieces())));
        }

        TurnManager turnManager = new TurnManager(players);
        turnManager.moveToPlayer(players.get(2));

        assertEquals(List.of(Colour.YELLOW, Colour.BLUE, Colour.RED, Colour.GREEN),
                turnManager.getRoundOrderColours());
    }

    // Returns a fixed value so the test outcome stays predictable.
    private static final class FixedDice implements Dice {
        @Override
        public int roll() {
            return 1;
        }
    }

    // Suppresses log output during the turn-order check.
    private static final class NoOpLogger extends GameLogger {
        @Override
        public void log(String message) {
        }
    }
}
