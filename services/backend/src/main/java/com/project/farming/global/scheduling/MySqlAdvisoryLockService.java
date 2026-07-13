package com.project.farming.global.scheduling;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

@Service
@RequiredArgsConstructor
public class MySqlAdvisoryLockService {

    private final DataSource dataSource;

    public boolean executeWithLock(String lockName, int waitSeconds, Runnable action) {
        try (Connection connection = dataSource.getConnection()) {
            if (!acquire(connection, lockName, waitSeconds)) {
                return false;
            }
            try {
                action.run();
                return true;
            } finally {
                release(connection, lockName);
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("스케줄러 DB lock 처리에 실패했습니다: " + lockName, ex);
        }
    }

    private boolean acquire(Connection connection, String lockName, int waitSeconds) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT GET_LOCK(?, ?)") ) {
            statement.setString(1, lockName);
            statement.setInt(2, Math.max(0, waitSeconds));
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() && resultSet.getInt(1) == 1;
            }
        }
    }

    private void release(Connection connection, String lockName) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT RELEASE_LOCK(?)")) {
            statement.setString(1, lockName);
            statement.executeQuery().close();
        }
    }
}
