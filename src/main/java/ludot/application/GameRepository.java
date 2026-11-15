// Defines storage and retrieval of game snapshots and paged session messages.
package ludot.application;

import java.sql.SQLException;
import java.util.List;

public interface GameRepository extends AutoCloseable {
    GameSnapshot loadLatest() throws SQLException;

    void save(GameSnapshot snapshot, List<String> messages) throws SQLException;

    List<String> loadMessages(String session, int offset, int limit) throws SQLException;

    void close() throws SQLException;
}
