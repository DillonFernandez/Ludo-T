// Calculates halved movement, rounded down, for a piece affected by Alpha's sickness.
package ludot.state;

import ludot.config.GameSettings;

public final class SickState implements PieceState {
    public int movementFor(int roll) {
        return roll / GameSettings.getInstance().getSpeedFactor();
    }

    public boolean canMove() {
        return true;
    }
}
