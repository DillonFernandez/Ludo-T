// Holds a proposed move, its destinations and the results of movement-rule checks.
package ludot.movement;

import java.util.ArrayList;
import java.util.List;
import ludot.model.Direction;
import ludot.model.Piece;

public final class MovePlan {
    public final List<Piece> pieces;
    public final int from;
    public final int[] approachVisits;
    public final List<Piece> blockers = new ArrayList<>();
    public Direction direction;
    public int destination;
    public int intendedDestination;
    public int steps;
    public boolean entry;
    public boolean legal;
    public boolean blocked;
    public boolean formsBlock;
    public boolean capturesOpponent;
    public int capturedDistance = Integer.MAX_VALUE;

    public MovePlan(List<Piece> pieces) {
        this.pieces = new ArrayList<>(pieces);
        from = pieces.get(0).getLocation();
        destination = from;
        intendedDestination = from;
        direction = pieces.get(0).getDirection();
        approachVisits = new int[pieces.size()];
        for (int index = 0; index < pieces.size(); index++) {
            approachVisits[index] = pieces.get(index).getApproachVisits();
        }
    }
}
