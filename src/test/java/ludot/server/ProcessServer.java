// Runs the production server and SQL repository with controlled gameplay for process tests.
package ludot.server;

import java.util.concurrent.CountDownLatch;
import ludot.application.GameService;
import ludot.config.GameSettings;
import ludot.persistence.JdbcGameRepository;
import ludot.random.RandomNumbers;
import ludot.random.RandomSource;
import ludot.support.ScriptedRandom;

public final class ProcessServer {
    public static void main(String[] args) throws Exception {
        RandomSource random = new RandomNumbers(1234);
        if (args[1].equals("limit")) {
            ScriptedRandom scripted = new ScriptedRandom();
            scripted.add(5, 4, 3, 2);
            random = scripted;
        }
        JdbcGameRepository repository = new JdbcGameRepository(args[0], "sa", "");
        GameService service = new GameService(repository, random);
        GameServer server = new GameServer("127.0.0.1", 0, repository, service,
                args[1].equals("paused") ? 60_000 : 1,
                GameSettings.getInstance().getRoundsPerUpdate());
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                server.close();
            } catch (Exception error) {
                error.printStackTrace();
            }
        }));
        System.out.println("SERVER_READY " + server.getPort() + " " + ProcessHandle.current().pid());
        new CountDownLatch(1).await();
    }
}
