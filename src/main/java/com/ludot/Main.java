package com.ludot;

import com.ludot.engine.LudoSimulation;

import java.util.function.Supplier;

// Starts the default LUDO-T simulation from the command line.
public class Main {

    // Provides the simulation instance used by the entry point.
    static Supplier<LudoSimulation> simulationSupplier = LudoSimulation::createDefault;

    public static void main(String[] args) {
        LudoSimulation simulation = simulationSupplier.get();
        simulation.run();
    }
}