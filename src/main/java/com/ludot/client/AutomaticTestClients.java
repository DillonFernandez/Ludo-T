package com.ludot.client;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

public final class AutomaticTestClients {

    private static final String HOST = "localhost";
    private static final int PORT = 5050;
    private static final int REQUESTS_PER_CLIENT = 5;

    private AutomaticTestClients() {
    }

    public static void main(String[] args) throws IOException {
        runClients(HOST, PORT);
    }

    // The default entry point and tests use the same asynchronous client flow.
    public static void runClients(String host, int port) throws IOException {

        try (
                GameClient clientOne = new GameClient(host, port);
                GameClient clientTwo = new GameClient(host, port)) {
            // Keep reading server output so the socket buffers do not fill.
            clientOne.listenForResponses(message -> {
            });

            clientTwo.listenForResponses(message -> {
            });

            CompletableFuture<Void> firstClient = CompletableFuture.runAsync(
                    () -> sendRequests(
                            "Test Client 1",
                            clientOne));

            CompletableFuture<Void> secondClient = CompletableFuture.runAsync(
                    () -> sendRequests(
                            "Test Client 2",
                            clientTwo));

            CompletableFuture.allOf(
                    firstClient,
                    secondClient).join();
        }
    }

    private static void sendRequests(
            String clientName,
            GameClient client) {
        for (int request = 1; request <= REQUESTS_PER_CLIENT; request++) {

            client.sendRequest("START_GAME");

            System.out.println(
                    clientName
                            + " sent START_GAME request "
                            + request);
        }
    }
}
