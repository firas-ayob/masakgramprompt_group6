package com.masakgram.server;

import com.masakgram.db.DatabaseManager;
import com.masakgram.llm.OllamaClient;
import java.net.ServerSocket;
import java.net.Socket;

public class MasakGramServer {
    private static final int PORT = 12345;

    public static void main(String[] args) {
        System.out.println("=== 🚀 INITIALIZING MASAKGRAMPROMPT SERVER ===");
        System.out.println("=" .repeat(50));
        
        // 1. Verify Dependencies & Infrastructure Connections
        System.out.print("🔍 Checking MySQL Connection... ");
        if (DatabaseManager.testConnection()) {
            System.out.println("✅ SUCCESS");
        } else {
            System.out.println("❌ FAILED (Ensure MySQL is running & schema is seeded)");
            System.out.println("   Hint: Check your database credentials in DatabaseManager.java");
            System.exit(1);
        }

        System.out.print("🔍 Checking Local Ollama LLM Service... ");
        if (OllamaClient.testLLMConnection()) {
            System.out.println("✅ SUCCESS");
        } else {
            System.out.println("❌ FAILED (Ensure Ollama is running locally)");
            System.out.println("   Hint: Run 'ollama serve' or 'ollama run llama3.2:3b'");
            System.exit(1);
        }

        // List available models
        System.out.println("\n📋 Available Ollama Models:");
        try {
            String[] models = {"llama3.2:3b", "phi4-mini:3.8b", "qwen2.5:3b", "gemma-sea-lion-v4:4b", "medgemma:4b"};
            for (String model : models) {
                System.out.println("   - " + model);
            }
        } catch (Exception e) {
            System.out.println("   ⚠️ Could not list models: " + e.getMessage());
        }

        // 2. Start TCP Server
        System.out.println("\n🔌 Application server binding to TCP Port: " + PORT);
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("✅ Server listening on TCP Port: " + PORT);
            System.out.println("=" .repeat(50));
            System.out.println("Waiting for client connections...");

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("📨 New client connected from: " + clientSocket.getInetAddress().getHostAddress());
                new Thread(new ClientHandler(clientSocket)).start();
            }
        } catch (Exception e) {
            System.err.println("❌ Server encountered an unrecoverable crash: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}