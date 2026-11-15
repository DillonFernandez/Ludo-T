// Runs two asynchronous request bursts and checks response sessions and revision order.
package ludot.client;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import ludot.application.GameSnapshot;

public final class AutomaticClients {
    private AutomaticClients() {
    }

    public static void main(String[] args) throws Exception {
        String host = args.length > 0 ? args[0] : "localhost";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 5050;
        int count = args.length > 2 ? Integer.parseInt(args[2]) : 200;
        if (count < 1 || count > 500) {
            throw new IllegalArgumentException("Use between 1 and 500 requests per client.");
        }
        try (GameClient first = new GameClient(host, port, update -> {
        }, reason -> {
        });
                GameClient second = new GameClient(host, port, update -> {
                }, reason -> {
                })) {
            CountDownLatch start = new CountDownLatch(1);
            CompletableFuture<BurstResult> firstRun = sendBurst(first, count, start);
            CompletableFuture<BurstResult> secondRun = sendBurst(second, count, start);
            start.countDown();
            CompletableFuture.allOf(firstRun, secondRun).get(30, TimeUnit.SECONDS);
            System.out.println("Two automatic clients completed " + (2 * count)
                    + " asynchronous requests with matching responses.");
        }
    }

    public static CompletableFuture<BurstResult> sendBurst(GameClient client,
            int count, CountDownLatch start) {
        if (count < 1 || count > 500) {
            throw new IllegalArgumentException("Use between 1 and 500 requests per client.");
        }
        return CompletableFuture.supplyAsync(() -> {
            try {
                start.await();
                long startedAt = System.nanoTime();
                AtomicInteger outstanding = new AtomicInteger();
                int maximumOutstanding = 0;
                List<CompletableFuture<GameSnapshot>> replies = new ArrayList<>();
                for (int index = 0; index < count; index++) {
                    maximumOutstanding = Math.max(maximumOutstanding, outstanding.incrementAndGet());
                    CompletableFuture<GameSnapshot> reply = client.request(index == 0 ? "START" : "STATUS");
                    replies.add(reply);
                    reply.whenComplete((snapshot, error) -> outstanding.decrementAndGet());
                }
                long submittedAt = System.nanoTime();
                CompletableFuture.allOf(replies.toArray(new CompletableFuture<?>[0])).join();
                String session = replies.get(0).join().getSession();
                long previous = -1;
                for (CompletableFuture<GameSnapshot> reply : replies) {
                    GameSnapshot snapshot = reply.join();
                    if (!session.equals(snapshot.getSession()) || snapshot.getRevision() < previous) {
                        throw new IllegalStateException("Responses were inconsistent or out of order.");
                    }
                    previous = snapshot.getRevision();
                }
                return new BurstResult(count, maximumOutstanding, startedAt, submittedAt, session, previous);
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Automatic client interrupted.", error);
            }
        });
    }

    public static final class BurstResult {
        private final int completedRequests;
        private final int maximumOutstanding;
        private final long startedAt;
        private final long submittedAt;
        private final String session;
        private final long lastRevision;

        BurstResult(int completedRequests, int maximumOutstanding, long startedAt,
                long submittedAt, String session, long lastRevision) {
            this.completedRequests = completedRequests;
            this.maximumOutstanding = maximumOutstanding;
            this.startedAt = startedAt;
            this.submittedAt = submittedAt;
            this.session = session;
            this.lastRevision = lastRevision;
        }

        public int getCompletedRequests() {
            return completedRequests;
        }

        public int getMaximumOutstanding() {
            return maximumOutstanding;
        }

        public long getStartedAt() {
            return startedAt;
        }

        public long getSubmittedAt() {
            return submittedAt;
        }

        public String getSession() {
            return session;
        }

        public long getLastRevision() {
            return lastRevision;
        }
    }
}
