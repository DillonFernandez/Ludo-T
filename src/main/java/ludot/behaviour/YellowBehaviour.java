// Selects Yellow's moves to favour deployment, required captures and progress towards Home.
package ludot.behaviour;

import java.util.ArrayList;
import java.util.List;
import ludot.model.Board;
import ludot.model.Player;
import ludot.movement.MovePlan;

public final class YellowBehaviour extends PlayerBehaviour {
    public YellowBehaviour(Player player, Board board) {
        super(player, board);
    }

    protected MovePlan selectMove(List<MovePlan> moves) {
        List<MovePlan> necessaryCaptures = new ArrayList<>();
        for (MovePlan move : moves) {
            if (move.entry) {
                return move;
            }
            if (move.capturesOpponent && needsCapture(move)) {
                necessaryCaptures.add(move);
            }
        }
        if (!necessaryCaptures.isEmpty()) {
            return closestToHome(necessaryCaptures);
        }
        return closestToHome(moves);
    }
}
