// Defines whether a piece can move and how far it moves for a dice roll.
package ludot.state;

public interface PieceState {
    int movementFor(int roll);

    boolean canMove();
}
