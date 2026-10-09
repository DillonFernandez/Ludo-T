package com.ludot.factory;

import com.ludot.board.Board;
import com.ludot.board.BoardBuilder;
import com.ludot.model.Colour;
import com.ludot.model.Piece;
import com.ludot.output.GameLogger;
import com.ludot.player.*;
import com.ludot.random.FixedCoinToss;
import com.ludot.random.FixedDice;
import com.ludot.random.FixedMysteryCellSpawner;
import com.ludot.random.FixedMysteryEffectSelector;
import com.ludot.rules.*;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

// These tests confirm the factory returns the correct player type for each colour.
class PlayerFactoryTest {

    // Verifies each colour maps to its matching player subclass.
    @Test
    void createPlayerReturnsCorrectSubclassForEachColour() {
        Board board = new BoardBuilder().build();
        RuleEngine ruleEngine = new RuleEngine(
                new MovementRule(board),
                new CaptureRule(board),
                new BlockRule(board),
                new BonusRollRule(),
                new MysteryRule(board, new FixedMysteryEffectSelector(null), new FixedMysteryCellSpawner(0),
                        new SilentLogger()),
                new WinCondition());
        PlayerFactory factory = new PlayerFactory(board, ruleEngine,
                new FixedDice(1), new FixedCoinToss(com.ludot.random.CoinFace.HEADS), new SilentLogger());

        for (Colour colour : Colour.values()) {
            List<Piece> pieces = new ArrayList<>(board.getHomeArea(colour).pieces());
            AbstractPlayer player = factory.createPlayer(colour, pieces);
            switch (colour) {
                case RED -> assertInstanceOf(RedPlayer.class, player);
                case GREEN -> assertInstanceOf(GreenPlayer.class, player);
                case YELLOW -> assertInstanceOf(YellowPlayer.class, player);
                case BLUE -> assertInstanceOf(BluePlayer.class, player);
            }

            assertEquals(4, player.getPieces().size());
            assertTrue(player.getPieces().stream().allMatch(piece -> piece.getColour() == colour));
            assertTrue(player.getPieces().stream().allMatch(Piece::isInBase));
        }
    }

    // Suppresses test output while the factory is being checked.
    private static final class SilentLogger extends GameLogger {
        @Override
        public void log(String message) {
        }
    }
}
