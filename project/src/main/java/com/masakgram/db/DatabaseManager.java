package com.masakgram.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DatabaseManager {
    // ⚠️ UPDATE THIS BASED ON YOUR ENVIRONMENT
    private static final String URL = "jdbc:mysql://localhost:3306/masakgramprompt?useSSL=false&serverTimezone=Asia/Kuala_Lumpur&characterEncoding=utf8";
    private static final String USER = "root";
    private static final String PASSWORD = ""; // Set your MySQL password here

    public static Connection getConnection() throws SQLException {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            System.err.println("❌ MySQL JDBC Driver not found!");
            e.printStackTrace();
        }
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }

    public static boolean testConnection() {
        try (Connection conn = getConnection()) {
            boolean isConnected = conn != null && !conn.isClosed();
            if (isConnected) {
                System.out.println("✅ Database connection successful");
            }
            return isConnected;
        } catch (SQLException e) {
            System.err.println("❌ Database Connection Failed: " + e.getMessage());
            System.err.println("   URL: " + URL);
            System.err.println("   User: " + USER);
            return false;
        }
    }

    // Helper method for testing
    public static void main(String[] args) {
        testConnection();
    }
}