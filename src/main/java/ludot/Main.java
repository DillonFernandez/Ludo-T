// Starts a complete LUDO-T game using random moves and console output.
package ludot;

import ludot.game.LudoGame;
import ludot.game.LudoGameFactory;
import ludot.output.ConsoleOutput;
import ludot.random.RandomNumbers;

public final class Main {
    private Main() {
    }

    public static void main(String[] args) {
        LudoGame game = new LudoGameFactory().createGame(
                new RandomNumbers(), new ConsoleOutput());
        game.play();
    }
}
