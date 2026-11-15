// Assigns the appropriate move-selection strategy to each player colour.
package ludot.game;

import ludot.behaviour.BlueBehaviour;
import ludot.behaviour.GreenBehaviour;
import ludot.behaviour.PlayerBehaviour;
import ludot.behaviour.RedBehaviour;
import ludot.behaviour.YellowBehaviour;
import ludot.model.Board;
import ludot.model.Player;
import ludot.mystery.MysteryCell;

public final class LudoGameFactory extends GameFactory {
    protected PlayerBehaviour createBehaviour(Player player, Board board,
            MysteryCell mystery) {
        switch (player.getColour()) {
            case RED:
                return new RedBehaviour(player, board);
            case GREEN:
                return new GreenBehaviour(player, board);
            case YELLOW:
                return new YellowBehaviour(player, board);
            case BLUE:
                return new BlueBehaviour(player, board, mystery);
            default:
                throw new IllegalArgumentException("Unknown player colour.");
        }
    }
}
