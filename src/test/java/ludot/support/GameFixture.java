// Supplies a fresh board and connected game components for controlled rule tests.
package ludot.support;

import ludot.game.TurnRunner;
import ludot.model.Board;
import ludot.model.Colour;
import ludot.model.Direction;
import ludot.model.Piece;
import ludot.model.Player;
import ludot.movement.BlockBreaker;
import ludot.movement.MoveExecutor;
import ludot.movement.MoveRules;
import ludot.mystery.MysteryCell;
import ludot.output.GameMessages;

public final class GameFixture {
    public final Player red = new Player(Colour.RED);
    public final Player green = new Player(Colour.GREEN);
    public final Player yellow = new Player(Colour.YELLOW);
    public final Player blue = new Player(Colour.BLUE);
    public final Board board = new Board(new Player[] { red, green, yellow, blue });
    public final ScriptedRandom random = new ScriptedRandom();
    public final RecordedOutput output = new RecordedOutput();
    public final GameMessages messages = new GameMessages(output);
    public final MysteryCell mystery = new MysteryCell(board, random, messages);
    public final MoveRules rules = new MoveRules(board);
    public final MoveExecutor executor = new MoveExecutor(board, random, messages, mystery);
    public final BlockBreaker breaker = new BlockBreaker(board, rules, executor);
    public final TurnRunner turns = new TurnRunner(random, rules, executor, breaker);

    public Piece place(Player player, int number, int cell, Direction direction) {
        Piece piece = player.getPieces()[number - 1];
        piece.enterBoard(direction);
        piece.moveTo(cell, 0);
        return piece;
    }

    public void spawnMystery() {
        mystery.finishRound(1);
        mystery.finishRound(2);
        mystery.finishRound(3);
    }
}
