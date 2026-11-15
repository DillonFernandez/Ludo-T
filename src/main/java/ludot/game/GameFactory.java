// Assembles a game from players, a board, randomness and an output destination.
package ludot.game;

import ludot.behaviour.PlayerBehaviour;
import ludot.model.Board;
import ludot.model.Colour;
import ludot.model.Player;
import ludot.mystery.MysteryCell;
import ludot.output.GameMessages;
import ludot.output.GameOutput;
import ludot.random.RandomSource;

public abstract class GameFactory {
    public final LudoGame createGame(RandomSource random, GameOutput output) {
        if (random == null || output == null) {
            throw new IllegalArgumentException("Randomness and output are required.");
        }
        Colour[] colours = Colour.values();
        Player[] players = new Player[colours.length];
        for (int index = 0; index < players.length; index++) {
            players[index] = new Player(colours[index]);
        }
        return new LudoGame(new Board(players), random,
                new GameMessages(output), this);
    }

    protected abstract PlayerBehaviour createBehaviour(Player player,
            Board board,
            MysteryCell mystery);
}
