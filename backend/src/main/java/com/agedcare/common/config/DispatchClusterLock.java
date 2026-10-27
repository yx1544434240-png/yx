package com.agedcare.common.config;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.stereotype.Component;

/** MySQL 会话级互斥；获取和释放必须使用同一个连接，进程断开后由数据库释放。 */
@Component
@RequiredArgsConstructor
public class DispatchClusterLock {
    private final JdbcTemplate jdbc;
    public boolean run(Runnable scan) {
        return run("dispatch", 0, scan);
    }
    public boolean run(String task, int waitSeconds, Runnable scan) {
        return Boolean.TRUE.equals(jdbc.execute((ConnectionCallback<Boolean>) connection -> {
            String name = "agedcare:" + connection.getCatalog() + ":" + task;
            boolean acquired;
            try (var statement = connection.prepareStatement("SELECT GET_LOCK(?, ?)")) {
                statement.setString(1, name);
                statement.setInt(2, waitSeconds);
                try (var result = statement.executeQuery()) { acquired = result.next() && result.getInt(1) == 1; }
            }
            if (!acquired) return false;
            try { scan.run(); return true; }
            finally {
                try (var statement = connection.prepareStatement("SELECT RELEASE_LOCK(?)")) {
                    statement.setString(1, name);
                    statement.execute();
                }
            }
        }));
    }
}
