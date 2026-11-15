// Validates, captures and encodes game state for storage and transmission.
package ludot.application;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import ludot.config.GameSettings;
import ludot.game.LudoGame;
import ludot.model.Player;

public final class GameSnapshot {
    private final long revision;
    private final String session;
    private final int round;
    private final String status;
    private final int mystery;
    private final int[] locations;
    private final String log;

    public GameSnapshot(long revision, String session, int round, String status,
            int mystery, int[] locations, String log) {
        if (session == null || status == null || locations == null || log == null
                || revision < 0 || round < 0
                || round > GameSettings.getInstance().getMaximumRounds()
                || locations.length != 16 || mystery < -1 || mystery > 51
                || !Arrays.asList("READY", "RUNNING", "FINISHED", "INTERRUPTED", "FAILED").contains(status)
                || !session.matches("[a-zA-Z0-9-]{1,40}")) {
            throw new IllegalArgumentException("Invalid game snapshot.");
        }
        for (int location : locations) {
            if (location < -1 || location > GameSettings.getInstance().getHome()) {
                throw new IllegalArgumentException("Invalid piece location.");
            }
        }
        this.revision = revision;
        this.session = session;
        this.round = round;
        this.status = status;
        this.mystery = mystery;
        this.locations = locations.clone();
        this.log = log;
    }

    public static GameSnapshot capture(long revision, String session,
            String status, LudoGame game, String log) {
        int[] locations = new int[16];
        for (Player player : game.getBoard().getPlayers()) {
            for (int piece = 0; piece < 4; piece++) {
                locations[player.getColour().ordinal() * 4 + piece] = player.getPieces()[piece].getLocation();
            }
        }
        return new GameSnapshot(revision, session, game.getCompletedRounds(),
                status, game.getMysteryLocation(), locations, log);
    }

    public String encode() {
        StringBuilder positions = new StringBuilder();
        for (int index = 0; index < locations.length; index++) {
            if (index > 0) {
                positions.append(',');
            }
            positions.append(locations[index]);
        }
        return revision + "\t" + session + "\t" + round + "\t" + status
                + "\t" + mystery + "\t" + positions + "\t"
                + Base64.getEncoder().encodeToString(log.getBytes(StandardCharsets.UTF_8));
    }

    public static GameSnapshot decode(String text) {
        String[] fields = text.split("\t", -1);
        if (fields.length != 7) {
            throw new IllegalArgumentException("Invalid snapshot message.");
        }
        String[] positions = fields[5].split(",", -1);
        int[] locations = new int[positions.length];
        for (int index = 0; index < positions.length; index++) {
            locations[index] = Integer.parseInt(positions[index]);
        }
        return new GameSnapshot(Long.parseLong(fields[0]), fields[1],
                Integer.parseInt(fields[2]), fields[3], Integer.parseInt(fields[4]),
                locations, new String(Base64.getDecoder().decode(fields[6]), StandardCharsets.UTF_8));
    }

    public long getRevision() {
        return revision;
    }

    public String getSession() {
        return session;
    }

    public int getRound() {
        return round;
    }

    public String getStatus() {
        return status;
    }

    public int getMystery() {
        return mystery;
    }

    public int[] getLocations() {
        return locations.clone();
    }

    public String getLog() {
        return log;
    }

    public boolean isRunning() {
        return status.equals("RUNNING");
    }
}
