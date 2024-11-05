package io.github.pokemeetup.managers;

import at.favre.lib.crypto.bcrypt.BCrypt;
import io.github.pokemeetup.utils.GameLogger;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.*;
import java.util.Base64;

import static io.github.pokemeetup.utils.PasswordUtils.hashPassword;

public class DatabaseManager {
    private static final String DB_PATH = "real"; // Relative to H2 server's baseDir ./data
    public static final String DB_USER = "sa";
    public static final String DB_PASS = "";
    private static final String DB_URL = "jdbc:h2:./database/pokemeetup";
    private static final String DB_PASSWORD = "";
    private static final int BASE_PORT = 9101; // Align with ServerLauncher
    private Connection connection;
    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
    }
    public DatabaseManager() {
        try {
            connectToDatabase();
            initializeTables();
        } catch (SQLException e) {
            GameLogger.info("Database initialization error: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Failed to initialize database", e);
        }
    }

    public boolean checkUsernameExists(String username) {
        String sql = "SELECT COUNT(*) FROM PLAYERS WHERE USERNAME = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return rs.getInt(1) > 0;
            }
            return false;
        } catch (SQLException e) {
            GameLogger.error("Database error checking username: " + e.getMessage());
            throw new RuntimeException("Database error checking username", e);
        }
    }

    private void connectToDatabase() throws SQLException {
        String url = String.format("jdbc:h2:tcp://localhost:%d/%s",
            BASE_PORT,
            DB_PATH
        );

        connection = DriverManager.getConnection(url, DB_USER, DB_PASS);
        GameLogger.info("Connected to database on port " + BASE_PORT);
    }

    private void initializeTables() throws SQLException {
        String createTableSQL = "CREATE TABLE IF NOT EXISTS players ("
            + "username VARCHAR(255) PRIMARY KEY,"
            + "password_hash VARCHAR(255),"
            + "x INT,"
            + "y INT"
            + ");";
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(createTableSQL);
            GameLogger.info("Players table initialized successfully.");
        }
    }

    public void dispose() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                GameLogger.info("Database connection closed");
            }
        } catch (SQLException e) {
            GameLogger.info("Error closing database connection: " + e.getMessage());
        }
    }

    // Add reconnection logic
    private void ensureConnection() {
        try {
            if (connection == null || connection.isClosed()) {
                GameLogger.info("Reconnecting to database...");
                connectToDatabase();
            }
        } catch (SQLException e) {
            GameLogger.info("Error checking connection: " + e.getMessage());
            e.printStackTrace();
        }
    }


    public boolean registerPlayer(String username, String password) {
        String sql = "INSERT INTO PLAYERS (USERNAME, PASSWORD, CREATED_AT) VALUES (?, ?, CURRENT_TIMESTAMP())";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            // Hash password before storing
            String hashedPassword = hashPassword(password);

            stmt.setString(1, username);
            stmt.setString(2, hashedPassword);

            int result = stmt.executeUpdate();
            return result > 0;
        } catch (SQLException e) {
            GameLogger.error("Database error registering player: " + e.getMessage());
            throw new RuntimeException("Database error registering player", e);
        }
    }

    private String hashPassword(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(password.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            GameLogger.error("Error hashing password: " + e.getMessage());
            throw new RuntimeException("Error hashing password", e);
        }
    }



    public void initializeDatabase() {
        String createPlayersTable = " CREATE TABLE IF NOT EXISTS PLAYERS (ID BIGINT AUTO_INCREMENT PRIMARY KEY,USERNAME VARCHAR (20) NOT NULL UNIQUE, PASSWORD VARCHAR(255) NOT NULL, CREATED_AT TIMESTAMP NOT NULL, LAST_LOGIN TIMESTAMP STATUS VARCHAR (20) DEFAULT 'OFFLINE', CONSTRAINT UK_USERNAME UNIQUE (USERNAME)";

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(createPlayersTable);
            GameLogger.info("Database tables initialized successfully");
        } catch (SQLException e) {
            GameLogger.error("Error initializing database: " + e.getMessage());
            throw new RuntimeException("Failed to initialize database", e);
        }
    }

    public void updatePlayerCoordinates(String username, int x, int y) {
        ensureConnection();
        String updateSQL = "UPDATE players SET x = ?, y = ? WHERE username = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(updateSQL)) {
            pstmt.setInt(1, x);
            pstmt.setInt(2, y);
            pstmt.setString(3, username);
            int rowsUpdated = pstmt.executeUpdate();
            GameLogger.info("Stored coordinates for " + username + ": (" + x + ", " + y + ")");
        } catch (SQLException e) {
            GameLogger.info("Error updating coordinates for username: " + username);
            e.printStackTrace();
        }
    }

    private boolean doesUsernameExist(String username) {
        String sql = "SELECT 1 FROM players WHERE username = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();
            boolean exists = rs.next();
            rs.close();
            GameLogger.info("Username '" + username + "' exists: " + exists);
            return exists;
        } catch (SQLException e) {
            GameLogger.info("Error checking if username exists: " + username);
            e.printStackTrace();
            return false;
        }
    }

    public void closeConnection() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                GameLogger.info("Database connection closed.");
            }
        } catch (SQLException e) {
            GameLogger.info("Error closing database connection: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public boolean authenticatePlayer(String username, String password) {
        String query = "SELECT password_hash FROM players WHERE username = ?";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                String storedHash = rs.getString("password_hash");
                rs.close();
                boolean verified = BCrypt.verifyer().verify(password.toCharArray(), storedHash).verified;
                GameLogger.info("Authentication for username '" + username + "': " + verified);
                return verified;
            } else {
                GameLogger.info("Authentication failed: Username '" + username + "' does not exist.");
                rs.close();
                return false;
            }
        } catch (SQLException e) {
            GameLogger.info("Authentication failed due to SQL error for username: " + username);
            e.printStackTrace();
            return false;
        }
    }

    public int[] getPlayerCoordinates(String username) {
        String sql = "SELECT x, y FROM players WHERE username = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                int x = rs.getInt("x");
                int y = rs.getInt("y");
                rs.close();
                // GameLogger.info("Retrieved coordinates for " + username + ": (" + x + ", " + y + ")");
                return new int[]{x, y};
            } else {
                // GameLogger.info("No coordinates found for " + username + ", using default");
                return new int[]{0, 0}; // Default spawn position
            }
        } catch (SQLException e) {
            GameLogger.info("Error retrieving coordinates for username: " + username);
            e.printStackTrace();
            return new int[]{0, 0}; // Default spawn position
        }
    }
}
