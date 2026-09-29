package com.ludot.client;

import org.junit.jupiter.api.Test;
import java.io.*;
import java.net.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class GameClientTest {
    private static final int ASYNC_TIMEOUT_SECONDS = 5;
    private static final int SOCKET_TIMEOUT_MILLIS = 5_000;
    private static final long LISTENER_JOIN_TIMEOUT_MILLIS = 5_000L;

    @Test
    void sendsSeparateFlushedRequestLines() throws Exception {
        try (var peer = new Peer()) {
            peer.client.sendRequest("START_GAME");
            peer.client.sendRequest("start_game");
            assertEquals("START_GAME", peer.input.readLine());
            assertEquals("start_game", peer.input.readLine());
        }
    }

    @Test
    void receivesOrderedResponsesOnAnAsynchronousVirtualThread() throws Exception {
        try (var peer = new Peer()) {
            BlockingQueue<String> received = new LinkedBlockingQueue<>();
            AtomicReference<Thread> listener = new AtomicReference<>();
            peer.listen(message -> {
                listener.set(Thread.currentThread());
                received.add(message);
            });
            List<String> expected = List.of("RESET_BOARD", "PIECE|R1|STANDARD_PATH|-|5", "Red player wins!!!");
            expected.forEach(peer.output::println);
            for (String value : expected) {
                assertEquals(value, received.poll(ASYNC_TIMEOUT_SECONDS, TimeUnit.SECONDS));
            }
            assertNotSame(Thread.currentThread(), listener.get());
            assertTrue(listener.get().isVirtual());
            peer.remote.shutdownOutput();
            listener.get().join(LISTENER_JOIN_TIMEOUT_MILLIS);
            assertFalse(listener.get().isAlive());
        }
    }

    @Test
    void canSendAnotherRequestWhileResponseListeningRemainsActive() throws Exception {
        try (var peer = new Peer()) {
            BlockingQueue<String> received = new LinkedBlockingQueue<>();
            AtomicReference<Thread> listener = new AtomicReference<>();
            peer.listen(message -> {
                listener.set(Thread.currentThread());
                received.add(message);
            });
            peer.output.println("RESET_BOARD");
            assertEquals("RESET_BOARD", received.poll(ASYNC_TIMEOUT_SECONDS, TimeUnit.SECONDS));
            peer.client.sendRequest("START_GAME");
            assertEquals("START_GAME", peer.input.readLine());
            peer.output.println("PIECE|B2|BASE|BLUE|-1");
            assertEquals("PIECE|B2|BASE|BLUE|-1", received.poll(ASYNC_TIMEOUT_SECONDS, TimeUnit.SECONDS));
            peer.remote.shutdownOutput();
            listener.get().join(LISTENER_JOIN_TIMEOUT_MILLIS);
            assertFalse(listener.get().isAlive());
        }
    }

    @Test
    void closingClientUnblocksItsListenerAndClosesTheSocket() throws Exception {
        try (var peer = new Peer()) {
            BlockingQueue<String> received = new LinkedBlockingQueue<>();
            AtomicReference<Thread> listener = new AtomicReference<>();
            peer.listen(message -> {
                listener.set(Thread.currentThread());
                received.add(message);
            });
            peer.output.println("ready");
            assertEquals("ready", received.poll(ASYNC_TIMEOUT_SECONDS, TimeUnit.SECONDS));
            peer.client.close();
            assertEquals("Connection to server closed.", received.poll(ASYNC_TIMEOUT_SECONDS, TimeUnit.SECONDS));
            assertNull(peer.input.readLine());
            listener.get().join(LISTENER_JOIN_TIMEOUT_MILLIS);
            assertFalse(listener.get().isAlive());
        }
    }

    private static final class Peer implements AutoCloseable {
        final ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
        final GameClient client;
        final Socket remote;
        final BufferedReader input;
        final PrintWriter output;
        final CompletableFuture<Thread> worker = new CompletableFuture<>();
        boolean listening;

        Peer() throws IOException {
            server.setSoTimeout(SOCKET_TIMEOUT_MILLIS);
            client = new GameClient(server.getInetAddress().getHostAddress(), server.getLocalPort());
            remote = server.accept();
            remote.setSoTimeout(SOCKET_TIMEOUT_MILLIS);
            input = new BufferedReader(new InputStreamReader(remote.getInputStream()));
            output = new PrintWriter(remote.getOutputStream(), true);
        }

        void listen(java.util.function.Consumer<String> handler) {
            listening = true;
            client.listenForResponses(message -> {
                worker.complete(Thread.currentThread());
                handler.accept(message);
            });
        }

        @Override public void close() throws Exception {
            try {
                client.close();
                if (listening) {
                    Thread thread = worker.get(ASYNC_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                    thread.join(LISTENER_JOIN_TIMEOUT_MILLIS);
                    assertFalse(thread.isAlive(), "Listener must stop even when an assertion fails");
                }
            } finally {
                remote.close();
                server.close();
            }
        }
    }
}
