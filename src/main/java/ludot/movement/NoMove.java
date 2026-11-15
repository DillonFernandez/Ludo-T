// Reports a blockade when the player has no selected legal move.
package ludot.movement;

import java.util.List;
import ludot.output.GameMessages;

public final class NoMove implements MoveCommand {
    private final List<MovePlan> plans;
    private final GameMessages messages;

    public NoMove(List<MovePlan> plans, GameMessages messages) {
        this.plans = plans;
        this.messages = messages;
    }

    public void execute() {
        for (MovePlan plan : plans) {
            if (plan.blocked && !plan.blockers.isEmpty()) {
                messages.blocked(plan, false);
                return;
            }
        }
    }

    public boolean capturedOpponent() {
        return false;
    }
}
