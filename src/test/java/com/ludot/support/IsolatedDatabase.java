package com.ludot.support;

import com.ludot.database.GameResultRepository;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;

/** An in-memory H2 database that disappears when its last connection closes. */
public final class IsolatedDatabase implements AutoCloseable {
    public final String url = "jdbc:h2:mem:audit_" + UUID.randomUUID();
    public final Connection connection;
    public final GameResultRepository repository;

    public IsolatedDatabase() throws SQLException {
        connection = DriverManager.getConnection(url, "sa", "");
        repository = new GameResultRepository(url);
    }

    @Override
    public void close() throws SQLException {
        connection.close();
    }
}
