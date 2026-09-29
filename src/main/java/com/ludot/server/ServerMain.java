package com.ludot.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.ludot.database.GameResultRepository;
import com.ludot.engine.GameBuilder;
import com.ludot.engine.LudoSimulation;

public final class ServerMain {

    private static final int PORT = 5050;

    private ServerMain() {
    }

    public static void main(String[] args) {
        SocketGameLogger gameLogger = new SocketGameLogger(new GameResultRepository());
        ExecutorService gameExecutor = Executors.newSingleThreadExecutor();
        ExecutorService clientExecutor = Executors.newVirtualThreadPerTaskExecutor();

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("LUDO-T server running on port " + PORT);

            while (true) {
                Socket clientSocket = serverSocket.accept();

                clientExecutor.submit(
                        () -> handleClient(clientSocket, gameLogger, gameExecutor,
                                () -> new LudoSimulation(new GameBuilder()
                                        .withLogger(gameLogger).build()).run()));
            }
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Unable to run LUDO-T server", e);
        } finally {
            clientExecutor.shutdown();
        }
    }

    // Package access permits isolated sockets and a bounded simulation in tests.
    static void handleClient(Socket clientSocket, SocketGameLogger gameLogger,
            ExecutorService gameExecutor, Runnable simulation) {
        PrintWriter output = null;

        try (
                clientSocket;
                BufferedReader input = new BufferedReader(
                        new InputStreamReader(
                                clientSocket.getInputStream()))) {
            output = new PrintWriter(
                    clientSocket.getOutputStream(), true);

            gameLogger.addClient(output);

            String request;

            while ((request = input.readLine()) != null) {
                if ("START_GAME".equalsIgnoreCase(request)) {
                    gameExecutor.submit(() -> {
                        gameLogger.log("RESET_BOARD");
                        simulation.run();
                    });
                }
            }

        } catch (IOException e) {
            System.err.println(
                    "Client connection failed: " + e.getMessage());
        } finally {
            if (output != null) {
                gameLogger.removeClient(output);
                output.close();
            }
        }
    }
}
