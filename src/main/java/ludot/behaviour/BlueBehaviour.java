// Selects Blue's moves by cycling pieces and preferring counter-clockwise mystery landings.
package ludot.behaviour;

import java.util.List;
import ludot.model.Board;
import ludot.model.Direction;
import ludot.model.Player;
import ludot.movement.MovePlan;
import ludot.mystery.MysteryCell;

public final class BlueBehaviour extends PlayerBehaviour {
    private final MysteryCell mystery;
    private int nextPiece = 1;

    public BlueBehaviour(Player player, Board board, MysteryCell mystery) {
        super(player, board);
        this.mystery = mystery;
    }

    protected MovePlan selectMove(List<MovePlan> moves) {
        MovePlan fallback = moves.get(0);
        MovePlan safeMove = fallback;
        boolean safeFound = false;
        boolean fallbackFound = false;
        for (int offset = 0; offset < player.getPieces().length; offset++) {
            int number = (nextPiece - 1 + offset) % player.getPieces().length + 1;
            for (MovePlan move : moves) {
                if (move.pieces.get(0).getNumber() != number) {
                    continue;
                }
                if (!fallbackFound) {
                    fallback = move;
                    fallbackFound = true;
                }
                boolean landsOnMystery = mystery.isActive()
                        && move.destination == mystery.getLocation();
                if (landsOnMystery
                        && move.direction == Direction.COUNTERCLOCKWISE) {
                    return move;
                }
                if (!safeFound && (!landsOnMystery
                        || move.direction == Direction.COUNTERCLOCKWISE)) {
                    safeMove = move;
                    safeFound = true;
                }
            }
        }
        return safeFound ? safeMove : fallback;
    }

    protected void afterChoosing(MovePlan chosen) {
        nextPiece = chosen.pieces.get(0).getNumber()
                % player.getPieces().length + 1;
    }
}
