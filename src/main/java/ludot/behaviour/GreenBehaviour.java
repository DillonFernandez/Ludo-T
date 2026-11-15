// Selects Green's moves to preserve blocks, form new blocks and deploy pieces.
package ludot.behaviour;

import java.util.ArrayList;
import java.util.List;
import ludot.model.Board;
import ludot.model.Player;
import ludot.movement.MovePlan;

public final class GreenBehaviour extends PlayerBehaviour {
    public GreenBehaviour(Player player, Board board) {
        super(player, board);
    }

    protected MovePlan selectMove(List<MovePlan> moves) {
        List<MovePlan> withoutBreaking = new ArrayList<>();
        for (MovePlan move : moves) {
            if (move.pieces.size() > 1
                    || board.companions(move.pieces.get(0)).size() < 2) {
                withoutBreaking.add(move);
            }
        }
        if (!withoutBreaking.isEmpty()) {
            moves = withoutBreaking;
        }
        for (MovePlan move : moves) {
            if (move.formsBlock && !move.entry) {
                return move;
            }
        }
        for (MovePlan move : moves) {
            if (move.entry) {
                return move;
            }
        }
        return preferNecessaryCaptures(moves);
    }

    private MovePlan preferNecessaryCaptures(List<MovePlan> moves) {
        List<MovePlan> peacefulMoves = new ArrayList<>();
        List<MovePlan> blockMoves = new ArrayList<>();
        for (MovePlan move : moves) {
            if (!move.capturesOpponent || needsCapture(move)) {
                peacefulMoves.add(move);
                if (move.pieces.size() > 1) {
                    blockMoves.add(move);
                }
            }
        }
        if (!blockMoves.isEmpty()) {
            return closestToHome(blockMoves);
        }
        if (!peacefulMoves.isEmpty()) {
            return closestToHome(peacefulMoves);
        }
        return closestToHome(moves);
    }
}
