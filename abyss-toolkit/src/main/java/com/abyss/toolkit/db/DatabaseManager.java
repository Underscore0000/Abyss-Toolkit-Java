package com.abyss.toolkit.db;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DatabaseManager {

    private static final Logger log = LoggerFactory.getLogger(DatabaseManager.class);
    private static final String DB_PATH = System.getProperty("user.home") + "/.abyss-toolkit/abyss.db";
    private static Connection connection;

    public static synchronized void init() {
        try {
            java.io.File dbFile = new java.io.File(DB_PATH);
            dbFile.getParentFile().mkdirs();
            connection = DriverManager.getConnection("jdbc:sqlite:" + DB_PATH);

            try (Statement st = connection.createStatement()) {
                st.execute("""
                    CREATE TABLE IF NOT EXISTS scans (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        timestamp TEXT,
                        type TEXT,
                        target TEXT,
                        result TEXT,
                        details TEXT
                    )""");
                st.execute("""
                    CREATE TABLE IF NOT EXISTS reports (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        name TEXT,
                        date TEXT,
                        path TEXT,
                        format TEXT
                    )""");
                st.execute("""
                    CREATE TABLE IF NOT EXISTS settings (
                        key TEXT PRIMARY KEY,
                        value TEXT
                    )""");
                st.execute("""
                    CREATE TABLE IF NOT EXISTS scan_results (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        scan_id INTEGER,
                        key TEXT,
                        value TEXT,
                        FOREIGN KEY(scan_id) REFERENCES scans(id)
                    )""");
                st.execute("""
                    CREATE TABLE IF NOT EXISTS attack_results (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        timestamp TEXT,
                        type TEXT,
                        target TEXT,
                        result TEXT,
                        details TEXT
                    )""");
            }

            // Migration
            try { connection.createStatement().execute("ALTER TABLE scans ADD COLUMN details TEXT"); } catch (SQLException ignored) {}
            try { connection.createStatement().execute("ALTER TABLE reports ADD COLUMN format TEXT"); } catch (SQLException ignored) {}

            log.info("Database initialized at {}", DB_PATH);
        } catch (SQLException e) {
            log.error("Database initialization failed", e);
        }
    }

    public static synchronized long recordScan(String type, String target, String result, String details) {
        String sql = "INSERT INTO scans(timestamp, type, target, result, details) VALUES (?,?,?,?,?)";
        try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, LocalDateTime.now().toString());
            ps.setString(2, type);
            ps.setString(3, target);
            ps.setString(4, result);
            ps.setString(5, details);
            ps.executeUpdate();
            ResultSet rs = ps.getGeneratedKeys();
            if (rs.next()) return rs.getLong(1);
        } catch (SQLException e) {
            log.error("Failed to record scan", e);
        }
        return -1;
    }

    public static synchronized void recordScanDetail(long scanId, String key, String value) {
        String sql = "INSERT INTO scan_results(scan_id, key, value) VALUES (?,?,?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, scanId);
            ps.setString(2, key);
            ps.setString(3, value);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("Failed to record scan detail", e);
        }
    }

    public static synchronized void recordReport(String name, String path, String format) {
        String sql = "INSERT INTO reports(name, date, path, format) VALUES (?,?,?,?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.setString(2, LocalDateTime.now().toString());
            ps.setString(3, path);
            ps.setString(4, format);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("Failed to record report", e);
        }
    }

    public static synchronized List<Map<String, String>> recentScans(int limit) {
        List<Map<String, String>> out = new ArrayList<>();
        String sql = "SELECT id, timestamp, type, target, result FROM scans ORDER BY id DESC LIMIT ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, String> row = new HashMap<>();
                    row.put("id", String.valueOf(rs.getInt("id")));
                    row.put("timestamp", rs.getString("timestamp"));
                    row.put("type", rs.getString("type"));
                    row.put("target", rs.getString("target"));
                    row.put("result", rs.getString("result"));
                    out.add(row);
                }
            }
        } catch (SQLException e) {
            log.error("Failed to fetch recent scans", e);
        }
        return out;
    }

    public static synchronized List<Map<String, String>> recentReports(int limit) {
        List<Map<String, String>> out = new ArrayList<>();
        String sql = "SELECT name, date, path, format FROM reports ORDER BY id DESC LIMIT ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, String> row = new HashMap<>();
                    row.put("name", rs.getString("name"));
                    row.put("date", rs.getString("date"));
                    row.put("path", rs.getString("path"));
                    row.put("format", rs.getString("format"));
                    out.add(row);
                }
            }
        } catch (SQLException e) {
            log.error("Failed to fetch recent reports", e);
        }
        return out;
    }

    public static synchronized int countScans() {
        return countRows("scans");
    }

    public static synchronized int countReports() {
        return countRows("reports");
    }

    public static synchronized void deleteScan(int id) {
        String sql = "DELETE FROM scans WHERE id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("Failed to delete scan", e);
        }
    }

    public static synchronized void deleteAllScans() {
        String sql = "DELETE FROM scans";
        try (Statement st = connection.createStatement()) {
            st.executeUpdate(sql);
        } catch (SQLException e) {
            log.error("Failed to delete all scans", e);
        }
    }

    public static synchronized void deleteReport(int id) {
        String sql = "DELETE FROM reports WHERE id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("Failed to delete report", e);
        }
    }

    private static int countRows(String table) {
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) AS c FROM " + table)) {
            if (rs.next()) return rs.getInt("c");
        } catch (SQLException e) {
            log.error("Count query failed for {}", table, e);
        }
        return 0;
    }

    public static synchronized void saveSetting(String key, String value) {
        String sql = "INSERT INTO settings(key, value) VALUES (?,?) ON CONFLICT(key) DO UPDATE SET value = excluded.value";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, key);
            ps.setString(2, value);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("Failed to save setting {}", key, e);
        }
    }

    public static synchronized String getSetting(String key, String defaultValue) {
        String sql = "SELECT value FROM settings WHERE key = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("value");
            }
        } catch (SQLException e) {
            log.error("Failed to read setting {}", key, e);
        }
        return defaultValue;
    }
}