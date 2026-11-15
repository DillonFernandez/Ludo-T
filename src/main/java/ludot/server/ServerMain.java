// Starts the game server with its SQL repository and installs shutdown handling.
package ludot.server;

import java.util.concurrent.CountDownLatch;
import ludot.application.GameService;
import ludot.config.GameSettings;
import ludot.persistence.JdbcGameRepository;

public final class ServerMain {
    private ServerMain() {
    }

    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 5050;
        String database = args.length > 1 ? args[1] : "jdbc:h2:tcp://localhost:9092/ludot";
        String host = args.length > 2 ? args[2] : "0.0.0.0";
        String password = System.getenv().getOrDefault("LUDOT_DB_PASSWORD", "");
        try (JdbcGameRepository repository = new JdbcGameRepository(database, "sa", password)) {
            GameService service = new GameService(repository);
            try (GameServer server = new GameServer(host, port, repository, service, 1,
                    GameSettings.getInstance().getRoundsPerUpdate())) {
                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    try {
                        server.close();
                    } catch (Exception error) {
                        System.err.println("Could not finish closing the server.");
                    }
                }));
                System.out.println("LUDO-T server listening on port " + server.getPort() + ".");
                new CountDownLatch(1).await();
            }
        }
    }
}
