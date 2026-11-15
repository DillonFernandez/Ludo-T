// Calculates doubled movement for a piece affected by Alpha's energised state.
package ludot.state;

import ludot.config.GameSettings;

public final class EnergisedState implements PieceState {
    public int movementFor(int roll) {
        return roll * GameSettings.getInstance().getSpeedFactor();
    }

    public boolean canMove() {
        return true;
    }
}
