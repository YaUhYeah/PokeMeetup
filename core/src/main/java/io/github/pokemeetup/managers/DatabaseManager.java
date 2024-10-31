package io.github.pokemeetup.managers;


import at.favre.lib.crypto.bcrypt.BCrypt;
import io.github.pokemeetup.utils.GameLogger;

import java.sql.*;

import static io.github.pokemeetup.utils.PasswordUtils.hashPassword;

public class DatabaseManager {
    private static final String DB_PATH = "~/data/real";
    private static final String DB_USER = "sa";
    private static final String DB_PASS = "";
    private Connection connection;
    private static final int BASE_PORT = 9092;
    private static volatile boolean serverStarted = false;
    private static final Object LOCK = new Object();

    public DatabaseManager() {
        try {
            // Try to connect first before starting server
            if (tryConnect()) {
                GameLogger.info("Connected to existing H2 database server");
            } else {
                // If connection fails, try to start server
                startServerIfNeeded();
                connectToDatabase();
            }
            initializeTables();
        } catch (SQLException e) {
            GameLogger.info("Database initialization error: " + e.getMessage());
            e.printStackTrace();
        }
    } private boolean tryConnect() {
        try {
            String url = "jdbc:h2:tcp://localhost:" + BASE_PORT + "/" + DB_PATH + ";IFEXISTS=TRUE";
            connection = DriverManager.getConnection(url, DB_USER, DB_PASS);
            return true;
        } catch (SQLException e) {
            return false;
        }
    }

    private void startServerIfNeeded() {
        synchronized (LOCK) {
            if (!serverStarted) {
                // Try multiple ports if the default is in use
                for (int port = BASE_PORT; port < BASE_PORT + 10; port++) {
                    try {
                        org.h2.tools.Server.createTcpServer(
                            "-tcpPort", String.valueOf(port),
                            "-tcpAllowOthers",
                            "-ifNotExists"
                        ).start();
                        GameLogger.info("H2 TCP Server started on port " + port);
                        serverStarted = true;
                        break;
                    } catch (SQLException e) {
                        if (port == BASE_PORT + 9) {
                            GameLogger.info("Failed to start H2 server on any port");
                            e.printStackTrace();
                        }
                    }
                }
            }
        }
    }
    public static void shutdownServer() {
        try {
            org.h2.tools.Server.shutdownTcpServer("tcp://localhost:" + BASE_PORT, "", true, true);
            GameLogger.info("H2 server shutdown complete");
        } catch (SQLException e) {
            GameLogger.info("Error shutting down H2 server: " + e.getMessage());
        }
    }
    private void connectToDatabase() throws SQLException {
        // Try multiple ports if the default fails
        SQLException lastException = null;
        for (int port = BASE_PORT; port < BASE_PORT + 10; port++) {
            try {
                String url = "jdbc:h2:tcp://localhost:" + port + "/" + DB_PATH + ";IFEXISTS=FALSE";
                connection = DriverManager.getConnection(url, DB_USER, DB_PASS);
                GameLogger.info("Connected to database successfully on port " + port);
                return;
            } catch (SQLException e) {
                lastException = e;
            }
        }
        if (lastException != null) {
            throw lastException;
        }
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

    // Update all database operations to use ensureConnection()
    public boolean registerPlayer(String username, String password) throws Exception {
        ensureConnection();
        if (doesUsernameExist(username)) {
            GameLogger.info("Registration failed: Username '" + username + "' already exists.");
            return false;
        }

        String hashedPassword = hashPassword(password);
        String sql = "INSERT INTO players (username, password_hash, x, y) VALUES (?, ?, ?, ?)";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, username);
            stmt.setString(2, hashedPassword);
            stmt.setInt(3, 800); // Default spawn X
            stmt.setInt(4, 800); // Default spawn Y
            stmt.executeUpdate();
            GameLogger.info("Successfully registered player: " + username);
            return true;
        } catch (SQLException e) {
            GameLogger.info("Registration failed for username: " + username);
            e.printStackTrace();
            return false;
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
    }    private boolean doesUsernameExist(String username) {
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
    }public int[] getPlayerCoordinates(String username) {
        String sql = "SELECT x, y FROM players WHERE username = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                int x = rs.getInt("x");
                int y = rs.getInt("y");
                rs.close();
//                GameLogger.info(STR."Retrieved coordinates for \{username}: (\{x}, \{y})");
                return new int[]{x, y};
            } else {
//                GameLogger.info(STR."No coordinates found for \{username}, using default");
                return new int[]{800, 800}; // Default spawn position
            }
        } catch (SQLException e) {
            GameLogger.info("Error retrieving coordinates for username: " + username);
            e.printStackTrace();
            return new int[]{800, 800}; // Default spawn position
        }
    }
    // Other methods remain the same but add ensureConnection() at the start
}
