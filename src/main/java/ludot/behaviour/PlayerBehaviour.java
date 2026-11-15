// Filters move plans and supplies selection helpers for each player's strategy.
package ludot.behaviour;

import java.util.ArrayList;
import java.util.List;
import ludot.model.Board;
import ludot.model.Piece;
import ludot.model.Player;
import ludot.movement.MoveCommand;
import ludot.movement.MoveExecutor;
import ludot.movement.MovePlan;
import ludot.movement.NoMove;
import ludot.movement.PlannedMove;

public abstract class PlayerBehaviour {
    protected final Player player;
    protected final Board board;

    protected PlayerBehaviour(Player player, Board board) {
        this.player = player;
        this.board = board;
    }

    public final MoveCommand chooseMove(List<MovePlan> plans,
            MoveExecutor executor) {
        List<MovePlan> legalMoves = new ArrayList<>();
        List<MovePlan> completeMoves = new ArrayList<>();
        for (MovePlan plan : plans) {
            if (plan.legal) {
                legalMoves.add(plan);
                if (!plan.blocked) {
                    completeMoves.add(plan);
                }
            }
        }
        if (!completeMoves.isEmpty()) {
            legalMoves = completeMoves;
        }
        if (legalMoves.isEmpty()) {
            return new NoMove(plans, executor.getMessages());
        }
        MovePlan chosen = selectMove(legalMoves);
        afterChoosing(chosen);
        return new PlannedMove(chosen, executor);
    }

    protected abstract MovePlan selectMove(List<MovePlan> moves);

    protected void afterChoosing(MovePlan chosen) {
    }

    protected MovePlan closestToHome(List<MovePlan> moves) {
        MovePlan closest = moves.get(0);
        int shortest = Integer.MAX_VALUE;
        for (MovePlan move : moves) {
            Piece piece = move.pieces.get(0);
            int distance = board.distanceFromHome(piece);
            if (piece.isOnStandardPath()) {
                distance = board.distanceFromHome(piece, move.direction);
            }
            if (distance < shortest) {
                shortest = distance;
                closest = move;
            }
        }
        return closest;
    }

    protected boolean needsCapture(MovePlan move) {
        for (Piece piece : move.pieces) {
            if (piece.getCaptures() == 0) {
                return true;
            }
        }
        return false;
    }
}
