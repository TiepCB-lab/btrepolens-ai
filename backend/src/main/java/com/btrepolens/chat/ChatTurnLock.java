package com.btrepolens.chat;

import com.btrepolens.common.AppException;
import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** A transaction-scoped advisory lock is released by PostgreSQL on connection loss. */
@Component
class ChatTurnLock {
    // Keep in sync with the busy-state query in ChatService.
    private static final int NAMESPACE = 0x4254524c;
    private final DataSource dataSource;
    ChatTurnLock(DataSource dataSource) { this.dataSource = dataSource; }

    Connection acquire(long sessionId) throws SQLException {
        Connection connection = dataSource.getConnection();
        try {
            connection.setAutoCommit(false);
            try (var statement = connection.prepareStatement("SELECT pg_try_advisory_xact_lock(?, ?)") ) {
                statement.setInt(1, (int) (sessionId >>> 32) ^ NAMESPACE);
                statement.setInt(2, (int) sessionId);
                try (var result = statement.executeQuery()) {
                    result.next();
                    if (!result.getBoolean(1)) throw new AppException(HttpStatus.CONFLICT, "SESSION_BUSY", "A response is already in progress for this session");
                }
            }
            return connection;
        } catch (SQLException | RuntimeException e) {
            connection.close();
            throw e;
        }
    }
}
