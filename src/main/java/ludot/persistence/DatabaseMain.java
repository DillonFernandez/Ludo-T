// Starts an H2 TCP server after creating the local LUDO-T database.
package ludot.persistence;

import java.sql.DriverManager;
import java.sql.Connection;
import java.nio.file.Files;
import java.nio.file.Path;
import org.h2.tools.Server;

public final class DatabaseMain {
    private DatabaseMain() {
    }

    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 9092;
        Path directory = Path.of(args.length > 1 ? args[1] : "data").toAbsolutePath();
        Files.createDirectories(directory);
        String password = System.getenv().getOrDefault("LUDOT_DB_PASSWORD", "");
        try (Connection connection = DriverManager.getConnection(
                "jdbc:h2:" + directory.resolve("ludot"), "sa", password)) {
            connection.isValid(2);
        }
        Server server = Server.createTcpServer("-tcpPort", Integer.toString(port),
                "-baseDir", directory.toString(), "-ifExists").start();
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
        System.out.println("LUDO-T database listening on port " + server.getPort() + ".");
    }
}
