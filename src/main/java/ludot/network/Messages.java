// Reads length-limited network lines and encodes message text with Base64.
package ludot.network;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public final class Messages {
    private Messages() {
    }

    public static String readLine(Reader reader, int maximumLength) throws IOException {
        StringBuilder line = new StringBuilder();
        int character;
        while ((character = reader.read()) != -1) {
            if (character == '\n') {
                return line.toString();
            }
            if (line.length() == maximumLength) {
                throw new IOException("The network message is too long.");
            }
            if (character != '\r') {
                line.append((char) character);
            }
        }
        if (line.length() > 0) {
            throw new IOException("The connection ended during a message.");
        }
        return null;
    }

    public static String encodeText(String text) {
        return Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

    public static String decodeText(String text) {
        return new String(Base64.getDecoder().decode(text), StandardCharsets.UTF_8);
    }
}
