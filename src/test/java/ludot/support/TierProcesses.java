// Starts and closes separate database and server processes for integration tests.
package ludot.support;

import java.nio.file.Path;

public final class TierProcesses implements AutoCloseable {
    public final ChildProcess database;
    public ChildProcess server;
    public final String databaseUrl;
    public int port;

    public TierProcesses(Path directory, String scenario) throws Exception {
        database = new ChildProcess("ludot.persistence.DatabaseMain", "0", directory.toString());
        try {
            String ready = database.awaitLine("LUDO-T database listening on port ");
            int databasePort = Integer.parseInt(ready.substring(ready.lastIndexOf(' ') + 1).replace(".", ""));
            databaseUrl = "jdbc:h2:tcp://localhost:" + databasePort + "/ludot";
            if (scenario.equals("production")) {
                server = new ChildProcess("ludot.server.ServerMain", "0", databaseUrl, "127.0.0.1");
                String message = server.awaitLine("LUDO-T server listening on port ");
                port = Integer.parseInt(message.substring(message.lastIndexOf(' ') + 1).replace(".", ""));
            } else {
                server = new ChildProcess("ludot.server.ProcessServer", databaseUrl, scenario);
                port = Integer.parseInt(server.awaitLine("SERVER_READY ").split(" ")[1]);
            }
        } catch (Exception | Error error) {
            if (server != null) {
                server.close();
            }
            database.close();
            throw error;
        }
    }

    public void close() throws Exception {
        try {
            server.close();
        } finally {
            database.close();
        }
    }
}
