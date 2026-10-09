package com.ludot.command;

// Represents one game action that can be executed when the game needs a single step.
public interface Command {

    // Implementations perform one rule-driven action when the game executes them.
    void execute();
}