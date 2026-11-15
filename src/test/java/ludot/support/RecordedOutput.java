// Stores game messages so tests can compare their text and sequence.
package ludot.support;

import java.util.ArrayList;
import java.util.List;
import ludot.output.GameOutput;

public final class RecordedOutput implements GameOutput {
    public final List<String> lines = new ArrayList<>();

    public void write(String message) {
        lines.add(message);
    }

    public String text() {
        return String.join("\n", lines);
    }
}
