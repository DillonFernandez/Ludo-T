package com.ludot.gui;

import com.ludot.output.GameLogger;

// Redirects formatted game messages to the local Swing event log.
public final class GuiGameLogger extends GameLogger {

    private final LudoFrame frame;

    public GuiGameLogger(LudoFrame frame) {
        this.frame = frame;
    }

    @Override
    public void log(String message) {
        frame.appendMessage(message);
    }
}