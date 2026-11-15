// Defines an executable turn action and its capture result.
package ludot.movement;

public interface MoveCommand {
    void execute();

    boolean capturedOpponent();
}
