// Executes a chosen move plan and records whether it captured an opponent.
package ludot.movement;

public final class PlannedMove implements MoveCommand {
    private final MovePlan plan;
    private final MoveExecutor executor;
    private boolean captured;

    public PlannedMove(MovePlan plan, MoveExecutor executor) {
        this.plan = plan;
        this.executor = executor;
    }

    public void execute() {
        captured = executor.execute(plan);
    }

    public boolean capturedOpponent() {
        return captured;
    }
}
