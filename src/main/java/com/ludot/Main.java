package com.ludot;

import com.ludot.engine.GameBuilder;
import com.ludot.engine.LudoSimulation;
import com.ludot.gui.GuiGameLogger;
import com.ludot.gui.LudoFrame;

import java.util.function.Supplier;

public class Main {

    static Supplier<LudoSimulation> simulationSupplier = Main::createGuiSimulation;

    public static void main(String[] args) {
        LudoSimulation simulation = simulationSupplier.get();
        simulation.run();
    }

    private static LudoSimulation createGuiSimulation() {
        LudoFrame frame = new LudoFrame();
        frame.setVisible(true);

        GuiGameLogger logger = new GuiGameLogger(frame);

        return new LudoSimulation(
                new GameBuilder()
                        .withLogger(logger)
                        .build());
    }
}