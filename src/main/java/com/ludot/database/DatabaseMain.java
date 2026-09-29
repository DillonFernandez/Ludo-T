package com.ludot.database;

import org.h2.tools.Server;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public final class DatabaseMain {

    private static final String DATABASE_URL = "jdbc:h2:./ludotdb";

    private DatabaseMain() {
    }

    public static void main(String[] args) {
        try {
            createDatabaseIfRequired();

            Server tcpServer = Server.createTcpServer(
                    "-tcpPort", "9092").start();

            Server webServer = Server.createWebServer(
                    "-webPort", "8082").start();

            System.out.println("LUDO-T database running at " + tcpServer.getURL());
            System.out.println("H2 console available at " + webServer.getURL());

            Thread.currentThread().join();

        } catch (SQLException e) {
            throw new IllegalStateException(
                    "Unable to start LUDO-T database",
                    e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void createDatabaseIfRequired() throws SQLException {
        try (Connection ignored = DriverManager.getConnection(
                DATABASE_URL,
                "sa",
                "")) {
            // Creates the local database if it does not already exist.
        }
    }
}