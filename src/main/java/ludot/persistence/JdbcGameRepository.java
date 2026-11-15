// Stores snapshots and messages together in SQL transactions and retrieves session output.
package ludot.persistence;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.ArrayList;
import ludot.application.GameRepository;
import ludot.application.GameSnapshot;

public final class JdbcGameRepository implements GameRepository {
    private final Connection connection;

    public JdbcGameRepository(String url, String user, String password) throws SQLException {
        connection = DriverManager.getConnection(url, user, password);
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS simulations ("
                    + "session_id VARCHAR(40) PRIMARY KEY, revision BIGINT NOT NULL, snapshot CLOB NOT NULL)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS game_messages ("
                    + "message_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY, "
                    + "session_id VARCHAR(40) NOT NULL, message CLOB NOT NULL)");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS game_output_order "
                    + "ON game_messages(session_id, message_id)");
        } catch (SQLException error) {
            connection.close();
            throw error;
        }
    }

    public GameSnapshot loadLatest() throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery(
                        "SELECT snapshot FROM simulations ORDER BY revision DESC FETCH FIRST 1 ROW ONLY")) {
            return rows.next() ? GameSnapshot.decode(rows.getString(1)) : null;
        }
    }

    public void save(GameSnapshot snapshot, List<String> messages) throws SQLException {
        connection.setAutoCommit(false);
        try (PreparedStatement state = connection.prepareStatement(
                "MERGE INTO simulations (session_id, revision, snapshot) KEY(session_id) VALUES (?, ?, ?)");
                PreparedStatement output = connection.prepareStatement(
                        "INSERT INTO game_messages (session_id, message) VALUES (?, ?)")) {
            state.setString(1, snapshot.getSession());
            state.setLong(2, snapshot.getRevision());
            state.setString(3, snapshot.encode());
            state.executeUpdate();
            for (String message : messages) {
                output.setString(1, snapshot.getSession());
                output.setString(2, message);
                output.addBatch();
            }
            output.executeBatch();
            connection.commit();
        } catch (SQLException error) {
            connection.rollback();
            throw error;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    public void close() throws SQLException {
        connection.close();
    }

    public List<String> loadMessages(String session, int offset, int limit) throws SQLException {
        List<String> messages = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT message FROM game_messages WHERE session_id = ? "
                        + "ORDER BY session_id, message_id OFFSET ? ROWS FETCH NEXT ? ROWS ONLY")) {
            statement.setString(1, session);
            statement.setInt(2, offset);
            statement.setInt(3, limit);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    messages.add(rows.getString(1));
                }
            }
        }
        return messages;
    }
}
