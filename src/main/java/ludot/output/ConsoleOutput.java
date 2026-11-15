// Presents game messages with console headings, indentation and line wrapping.
package ludot.output;

public final class ConsoleOutput implements GameOutput {
    private static final int LINE_WIDTH = 96;
    private static final String DIVIDER = "===============================================";
    private static final String DETAIL_INDENT = "    ";
    private String previousMessage = "";

    public void write(String message) {
        if (message.startsWith("Round ") || message.endsWith(" player wins!!!")
                || message.startsWith("The game ran ")) {
            printHeading(message);
        } else if (message.contains(" player rolled ")) {
            if (!previousMessage.isEmpty() && !previousMessage.startsWith("Round ")) {
                System.out.println();
            }
            printLine(message, "");
        } else if (message.startsWith("The ") && message.contains(" player has four ")) {
            printLine(message, "");
        } else if (message.contains(" rolls ")) {
            if (previousMessage.contains(" player has four ")) {
                System.out.println();
            }
            printLine(message, "");
        } else if (message.contains(" player has the highest roll")) {
            System.out.println();
            printLine(message, "");
        } else if (message.startsWith("The order of a single round")) {
            printLine(message, "");
        } else if (message.contains(" player now has ")
                || message.startsWith("A mystery cell has spawned")) {
            System.out.println();
            printLine(message, "");
        } else if (message.equals("============================")) {
            return;
        } else if (message.startsWith("Location of pieces ")) {
            printLine("----------- " + message + " -----------", "");
        } else {
            printLine(message, DETAIL_INDENT);
        }
        previousMessage = message;
    }

    private void printHeading(String message) {
        if (!previousMessage.isEmpty()) {
            System.out.println();
        }
        System.out.println(DIVIDER);
        printLine(message, "");
        System.out.println(DIVIDER);
    }

    private void printLine(String message, String indent) {
        int availableWidth = LINE_WIDTH - indent.length();
        while (message.length() > availableWidth) {
            int breakPosition = message.lastIndexOf(' ', availableWidth);
            if (breakPosition <= 0) {
                breakPosition = availableWidth;
            }
            System.out.println(indent + message.substring(0, breakPosition));
            message = message.substring(breakPosition).trim();
        }
        System.out.println(indent + message);
    }
}
