package com.ludot.client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.function.Consumer;

public final class GameClient implements AutoCloseable {

    private final Socket socket;
    private final BufferedReader input;
    private final PrintWriter output;

    public GameClient(String host, int port) throws IOException {
        socket = new Socket(host, port);

        input = new BufferedReader(
                new InputStreamReader(socket.getInputStream()));

        output = new PrintWriter(
                socket.getOutputStream(), true);
    }

    public void sendRequest(String request) {
        output.println(request);
    }

    public void listenForResponses(Consumer<String> responseHandler) {
        Thread.ofVirtual().start(() -> {
            try {
                String response;

                while ((response = input.readLine()) != null) {
                    responseHandler.accept(response);
                }
            } catch (IOException e) {
                responseHandler.accept(
                        "Connection to server closed.");
            }
        });
    }

    @Override
    public void close() throws IOException {
        socket.close();
    }
}