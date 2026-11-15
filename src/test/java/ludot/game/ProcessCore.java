// Runs game-completion and capture checks with GUI, network and persistence classes excluded.
package ludot.game;

import ludot.model.Direction;
import ludot.model.Piece;
import ludot.movement.MoveExecutor;
import ludot.movement.MoveRules;
import ludot.mystery.MysteryCell;
import ludot.output.GameMessages;
import ludot.random.RandomNumbers;

public final class ProcessCore {
    public static void main(String[] args) throws Exception {
        for (String name : new String[] { "ludot.gui.GameWindow", "ludot.client.GameClient",
                "ludot.persistence.JdbcGameRepository", "org.h2.Driver" }) {
            try {
                Class.forName(name);
                throw new AssertionError("The isolated core includes tier code: " + name);
            } catch (ClassNotFoundException expected) {
            }
        }
        RandomNumbers random = new RandomNumbers(1234);
        LudoGame game = new LudoGameFactory().createGame(random, message -> {
        });
        if (args[0].equals("game")) {
            game.play();
            if (!game.isFinished() || game.getCompletedRounds() < 1 || game.getCompletedRounds() > 10_000) {
                throw new AssertionError("The isolated game did not finish correctly.");
            }
            System.out.println("CORE_GAME_OK " + game.getCompletedRounds());
        } else {
            Piece red = game.getBoard().getPlayers()[0].getPieces()[0];
            Piece blue = game.getBoard().getPlayers()[3].getPieces()[0];
            red.enterBoard(Direction.CLOCKWISE);
            red.moveTo(0, 0);
            blue.enterBoard(Direction.CLOCKWISE);
            blue.moveTo(2, 0);
            GameMessages messages = new GameMessages(message -> {
            });
            MoveExecutor executor = new MoveExecutor(game.getBoard(), random, messages,
                    new MysteryCell(game.getBoard(), random, messages));
            boolean captured = executor.execute(new MoveRules(game.getBoard()).planPiece(red, 2));
            if (!captured || !blue.isAtBase() || red.getCaptures() != 1 || red.getLocation() != 2) {
                throw new AssertionError("The isolated capture rule did not run correctly.");
            }
            System.out.println("CORE_RULES_OK");
        }
    }
}
