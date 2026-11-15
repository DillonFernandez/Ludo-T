// Stops a piece from moving while its briefing restriction is active.
package ludot.state;

public final class BriefingState implements PieceState {
    public int movementFor(int roll) {
        return 0;
    }

    public boolean canMove() {
        return false;
    }
}
