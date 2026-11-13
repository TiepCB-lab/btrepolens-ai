package com.btrepolens.chat;

import com.btrepolens.common.AppException;
import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** Holds a session-scoped advisory lock while the turn is active. */
@Component
class ChatTurnLock {
    static final int NAMESPACE = 0x4254524c;
    private final DataSource dataSource;
    ChatTurnLock(DataSource dataSource) { this.dataSource = dataSource; }

    record Turn(Connection connection, long generation, int pid, int high, int low) implements AutoCloseable {
        @Override public void close() throws SQLException {
            try {
                try (var statement = connection.prepareStatement("SELECT pg_advisory_unlock(?, ?)")) {
                    statement.setInt(1, high);
                    statement.setInt(2, low);
                    try (var result = statement.executeQuery()) {
                        if (!result.next() || !result.getBoolean(1)) throw new SQLException("Advisory lock was lost");
                    }
                }
            } catch (SQLException e) {
                // Never return a connection with an uncertain session lock to the pool.
                try { connection.abort(Runnable::run); } catch (SQLException abortFailure) { e.addSuppressed(abortFailure); }
                throw e;
            } finally { connection.close(); }
        }
    }

    Turn acquire(long sessionId) throws SQLException {
        Connection connection = dataSource.getConnection();
        boolean denied = false;
        int high = (int) (sessionId >>> 32) ^ NAMESPACE;
        int low = (int) sessionId;
        try {
            connection.setAutoCommit(true);
            int pid;
            try (var statement = connection.prepareStatement("SELECT pg_try_advisory_lock(?, ?), pg_backend_pid()") ) {
                statement.setInt(1, high);
                statement.setInt(2, low);
                try (var result = statement.executeQuery()) {
                    result.next();
                    if (!result.getBoolean(1)) {
                        denied = true;
                        throw new AppException(HttpStatus.CONFLICT, "SESSION_BUSY", "A response is already in progress for this session");
                    }
                    pid = result.getInt(2);
                }
            }
            try (var statement = connection.prepareStatement("UPDATE chat_session SET turn_generation=turn_generation+1 WHERE id=? RETURNING turn_generation")) {
                statement.setLong(1, sessionId);
                try (var result = statement.executeQuery()) {
                    if (!result.next()) throw new AppException(HttpStatus.NOT_FOUND, "SESSION_NOT_FOUND", "Chat session not found");
                    return new Turn(connection, result.getLong(1), pid, high, low);
                }
            }
        } catch (SQLException | RuntimeException e) {
            // A lost response can leave the session lock held; discard uncertain connections.
            if (!denied) try { connection.abort(Runnable::run); } catch (SQLException abortFailure) { e.addSuppressed(abortFailure); }
            try { connection.close(); } catch (SQLException closeFailure) { e.addSuppressed(closeFailure); }
            throw e;
        }
    }
}
