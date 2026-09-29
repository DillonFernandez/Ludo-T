package com.ludot.gui;

import com.ludot.output.GameLogger;

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