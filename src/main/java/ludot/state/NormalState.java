// Allows movement by the unmodified dice value.
package ludot.state;

public final class NormalState implements PieceState {
    public int movementFor(int roll) {
        return roll;
    }

    public boolean canMove() {
        return true;
    }
}
