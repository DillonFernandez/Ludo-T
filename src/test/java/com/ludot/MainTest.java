package com.ludot;

import com.ludot.engine.GameBuilder;
import com.ludot.engine.LudoSimulation;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Verifies that the command-line entry point starts the simulation without reading input.
class MainTest {

    // Confirms that the simulation starts automatically and does not depend on user
    // input.
    @Test
    void mainStartsSimulationAutomaticallyWithoutReadingInput() {
        InputStream originalIn = System.in;
        var throwingInput = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new AssertionError("The simulation must not read user input.");
            }
        };

        AtomicBoolean runInvoked = new AtomicBoolean(false);
        var originalSupplier = Main.simulationSupplier;

        try {
            System.setIn(throwingInput);
            Main.simulationSupplier = () -> new LudoSimulation(new GameBuilder().build()) {
                @Override
                public void run() {
                    runInvoked.set(true);
                }
            };

            assertDoesNotThrow(() -> Main.main(new String[0]));
            assertTrue(runInvoked.get(), "Main.main should start the simulation automatically.");
        } finally {
            Main.simulationSupplier = originalSupplier;
            System.setIn(originalIn);
        }
    }
}