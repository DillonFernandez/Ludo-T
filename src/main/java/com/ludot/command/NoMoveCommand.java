package com.ludot.command;

// Represents a turn that cannot move any piece, while still keeping the game flow consistent.
public class NoMoveCommand implements Command {

    // Execution is intentionally empty because a no-move turn should not change the
    // board state.
    @Override
    public void execute() {
    }
}