package com.ludot.engine;

// Provides a simple entry point for starting a ready-to-run LUDO-T game.
public class LudoSimulation {

    // The simulation stores the game engine that will run the match.
    private final GameEngine gameEngine;

    // The engine is required so the simulation has something concrete to run.
    public LudoSimulation(GameEngine gameEngine) {
        if (gameEngine == null) {
            throw new IllegalArgumentException("GameEngine must not be null.");
        }
        this.gameEngine = gameEngine;
    }

    // This factory creates a simulation with the standard game setup.
    public static LudoSimulation createDefault() {
        GameEngine engine = new GameBuilder().build();
        return new LudoSimulation(engine);
    }

    // Running the simulation starts the game loop until it finishes.
    public void run() {
        gameEngine.run();
    }
}