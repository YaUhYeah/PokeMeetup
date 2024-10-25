package io.github.pokemeetup.managers;

import at.favre.lib.crypto.bcrypt.BCrypt;
import io.github.pokemeetup.system.gameplay.overworld.World;

import java.sql.*;

public class DatabaseManager {
    private Connection connection;

    public DatabaseManager() {
        try {
            // Establish a single persistent connection
            connection = DriverManager.getConnection("jdbc:h2:~/data/real", "sa", "");
            initializeTables();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void initializeTables() {
        String createTableSQL = "CREATE TABLE IF NOT EXISTS players ("
            + "username VARCHAR(255) PRIMARY KEY,"
            + "password_hash VARCHAR(255),"
            + "x INT,"
            + "y INT"
            + ");";
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(createTableSQL);
            System.out.println("Players table initialized successfully.");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // Registration method
    public void updatePlayerCoordinates(String username, int x, int y) {
        // Remove any position scaling - store raw tile coordinates
        String updateSQL = "UPDATE players SET x = ?, y = ? WHERE username = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(updateSQL)) {
            pstmt.setInt(1, x);
            pstmt.setInt(2, y);
            pstmt.setString(3, username);
            int rowsUpdated = pstmt.executeUpdate();
            System.out.println("Stored coordinates for " + username + ": (" + x + ", " + y + ")");
        } catch (SQLException e) {
            System.err.println("Error updating coordinates for username: " + username);
            e.printStackTrace();
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
                System.out.println("Retrieved coordinates for " + username + ": (" + x + ", " + y + ")");
                return new int[]{x, y};
            } else {
                System.out.println("No coordinates found for " + username + ", using default");
                return new int[]{800, 800}; // Default spawn position
            }
        } catch (SQLException e) {
            System.err.println("Error retrieving coordinates for username: " + username);
            e.printStackTrace();
            return new int[]{800, 800}; // Default spawn position
        }
    }

    public boolean registerPlayer(String username, String password) {
        if (doesUsernameExist(username)) {
            System.out.println("Registration failed: Username '" + username + "' already exists.");
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
            System.out.println("Successfully registered player: " + username + " at position (800, 800)");
            return true;
        } catch (SQLException e) {
            System.err.println("Registration failed for username: " + username);
            e.printStackTrace();
            return false;
        }
    }
    // Check if the username already exists
    private boolean doesUsernameExist(String username) {
        String sql = "SELECT 1 FROM players WHERE username = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();
            boolean exists = rs.next();
            rs.close();
            System.out.println("Username '" + username + "' exists: " + exists);
            return exists;
        } catch (SQLException e) {
            System.err.println("Error checking if username exists: " + username);
            e.printStackTrace();
            return false;
        }
    }

    // Authentication method
    public boolean authenticatePlayer(String username, String password) {
        String query = "SELECT password_hash FROM players WHERE username = ?";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                String storedHash = rs.getString("password_hash");
                rs.close();
                boolean verified = BCrypt.verifyer().verify(password.toCharArray(), storedHash).verified;
                System.out.println("Authentication for username '" + username + "': " + verified);
                return verified;
            } else {
                System.out.println("Authentication failed: Username '" + username + "' does not exist.");
                rs.close();
                return false;
            }
        } catch (SQLException e) {
            System.err.println("Authentication failed due to SQL error for username: " + username);
            e.printStackTrace();
            return false;
        }
    }

    // Password hashing using BCrypt
    public String hashPassword(String plainTextPassword) {
        String hashed = BCrypt.withDefaults().hashToString(12, plainTextPassword.toCharArray());
        System.out.println("Hashed password for user: " + hashed);
        return hashed;
    }

    // Close the connection when done (optional, based on your application's lifecycle)
    public void closeConnection() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                System.out.println("Database connection closed.");
            }
        } catch (SQLException e) {
            System.err.println("Error closing the database connection.");
            e.printStackTrace();
        }
    }
}
