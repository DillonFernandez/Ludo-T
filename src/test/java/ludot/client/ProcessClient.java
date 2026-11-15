// Runs a separate client process for burst, completion and database-isolation tests.
package ludot.client;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import ludot.application.GameSnapshot;

public final class ProcessClient {
    public static void main(String[] args) throws Exception {
        try (GameClient client = new GameClient("127.0.0.1", Integer.parseInt(args[0]),
                update -> {
                }, reason -> {
                })) {
            GameSnapshot initial = client.request("STATUS").get(5, TimeUnit.SECONDS);
            if (args.length > 1 && args[1].equals("boundary")) {
                for (String name : new String[] { "ludot.persistence.JdbcGameRepository", "org.h2.Driver" }) {
                    try {
                        Class.forName(name);
                        throw new AssertionError("The isolated client includes database code: " + name);
                    } catch (ClassNotFoundException expected) {
                    }
                }
                System.out.println("BOUNDARY_OK " + ProcessHandle.current().pid() + " " + initial.getStatus());
                return;
            }
            System.out.println("READY " + ProcessHandle.current().pid() + " " + initial.getSession());
            BufferedReader input = new BufferedReader(new InputStreamReader(System.in));
            String command;
            while ((command = input.readLine()) != null && !command.equals("EXIT")) {
                if (command.startsWith("BURST ")) {
                    CountDownLatch start = new CountDownLatch(1);
                    var completed = AutomaticClients.sendBurst(client,
                            Integer.parseInt(command.substring(6)), start);
                    start.countDown();
                    AutomaticClients.BurstResult result = completed.get(15, TimeUnit.SECONDS);
                    System.out.println("BURST_DONE " + result.getCompletedRequests() + " "
                            + result.getMaximumOutstanding() + " " + result.getSession()
                            + " " + result.getLastRevision());
                } else if (command.equals("WAIT_FINISHED")) {
                    GameSnapshot snapshot = client.request("STATUS").get(5, TimeUnit.SECONDS);
                    while (snapshot.isRunning()) {
                        snapshot = client.request("STATUS").get(5, TimeUnit.SECONDS);
                    }
                    System.out.println("FINISHED " + snapshot.encode());
                } else {
                    System.out.println("STATE " + client.request(command).get(5, TimeUnit.SECONDS).encode());
                }
            }
        }
    }
}
