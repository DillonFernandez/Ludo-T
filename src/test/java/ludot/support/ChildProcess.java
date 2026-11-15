// Launches test JVMs and manages their input, output signals and shutdown.
package ludot.support;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

public final class ChildProcess implements AutoCloseable {
    public final Process process;
    private final LinkedBlockingQueue<String> lines = new LinkedBlockingQueue<>();
    private final StringBuilder transcript = new StringBuilder();
    private final Thread reader;
    private final BufferedWriter input;

    public ChildProcess(String mainClass, String... arguments) throws Exception {
        this(System.getProperty("surefire.test.class.path", System.getProperty("java.class.path")),
                mainClass, arguments);
    }

    public ChildProcess(String classpath, String mainClass, String[] arguments) throws Exception {
        String executable = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
        List<String> command = new ArrayList<>();
        command.add(Path.of(System.getProperty("java.home"), "bin", executable).toString());
        command.add("-cp");
        command.add(classpath);
        command.add(mainClass);
        for (String argument : arguments) {
            command.add(argument);
        }
        ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);
        builder.environment().put("LUDOT_DB_PASSWORD", "");
        process = builder.start();
        input = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
        reader = new Thread(() -> {
            try (BufferedReader output = new BufferedReader(new InputStreamReader(
                    process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = output.readLine()) != null) {
                    synchronized (transcript) {
                        transcript.append(line).append('\n');
                    }
                    lines.add(line);
                }
            } catch (Exception error) {
                lines.add("PROCESS_READER_ERROR " + error.getClass().getSimpleName());
            }
        }, "test-process-output");
        reader.setDaemon(true);
        reader.start();
    }

    public String awaitLine(String prefix) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (System.nanoTime() < deadline) {
            String line = lines.poll(Math.max(1, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
            assertNotNull(line, () -> "Missing process signal " + prefix + ": " + transcript());
            if (line.startsWith(prefix)) {
                return line;
            }
            if (line.startsWith("Exception")) {
                fail(transcript());
            }
        }
        throw new AssertionError("Missing process signal " + prefix + ": " + transcript());
    }

    public void send(String text) throws Exception {
        input.write(text);
        input.newLine();
        input.flush();
    }

    public void awaitSuccess() throws Exception {
        assertTrue(process.waitFor(15, TimeUnit.SECONDS), () -> "Process did not finish: " + transcript());
        reader.join(2000);
        assertEquals(0, process.exitValue(), transcript());
    }

    public String transcript() {
        synchronized (transcript) {
            return transcript.toString();
        }
    }

    public void close() throws Exception {
        try {
            input.close();
        } finally {
            process.destroy();
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                assertTrue(process.waitFor(5, TimeUnit.SECONDS));
            }
            reader.join(2000);
            assertFalse(process.isAlive());
        }
    }
}
