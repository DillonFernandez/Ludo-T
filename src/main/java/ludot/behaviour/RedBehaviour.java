// Selects Red's moves to favour captures and avoid forming new blocks.
package ludot.behaviour;

import java.util.ArrayList;
import java.util.List;
import ludot.model.Board;
import ludot.model.Player;
import ludot.movement.MovePlan;

public final class RedBehaviour extends PlayerBehaviour {
    public RedBehaviour(Player player, Board board) {
        super(player, board);
    }

    protected MovePlan selectMove(List<MovePlan> moves) {
        MovePlan bestCapture = moves.get(0);
        boolean captureFound = false;
        for (MovePlan move : moves) {
            if (!move.entry && move.capturesOpponent && (!captureFound
                    || move.capturedDistance < bestCapture.capturedDistance)) {
                bestCapture = move;
                captureFound = true;
            }
        }
        if (captureFound) {
            return bestCapture;
        }
        for (MovePlan move : moves) {
            if (move.entry && !move.formsBlock) {
                return move;
            }
        }
        List<MovePlan> movesWithoutNewBlocks = new ArrayList<>();
        for (MovePlan move : moves) {
            if (!move.formsBlock) {
                movesWithoutNewBlocks.add(move);
            }
        }
        if (!movesWithoutNewBlocks.isEmpty()) {
            return closestToHome(movesWithoutNewBlocks);
        }
        return closestToHome(moves);
    }
}
