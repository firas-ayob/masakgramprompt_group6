package com.masakgram.client;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Scanner;

public class MasakGramClient {
    private static final String HOST = "localhost";
    private static final int PORT = 12345;

    private static final String[] MODELS = {
        "Llama 3.2 (3B)",
        "Phi-4-mini (3.8B)",
        "Qwen 2.5 (3B)",
        "Gemma-SEA-LION v4 (4B)",
        "MedGemma (4B)"
    };

    private static final String[] TECHNIQUES = {
        "zero-shot",
        "few-shot",
        "chain-of-thought",
        "structured-output"
    };

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        System.out.println("=== MASAKGRAMPROMPT BATCH PROCESSING DASHBOARD ===");

        // 1. Choose Model
        System.out.println("\n--- Select LLM Model ---");
        for (int i = 0; i < MODELS.length; i++) {
            System.out.println("[" + (i + 1) + "] " + MODELS[i]);
        }
        System.out.print("Choice (1-" + MODELS.length + "): ");
        int modelChoice = scanner.nextInt();
        String chosenModel = MODELS[modelChoice - 1];

        // 2. Choose Prompt Technique
        System.out.println("\n--- Select Prompt Technique ---");
        for (int i = 0; i < TECHNIQUES.length; i++) {
            System.out.println("[" + (i + 1) + "] " + TECHNIQUES[i]);
        }
        System.out.print("Choice (1-" + TECHNIQUES.length + "): ");
        int techChoice = scanner.nextInt();
        String chosenTechnique = TECHNIQUES[techChoice - 1];

        // 3. Dispatch Batch Execution Request Over TCP
        System.out.println("\nConnecting to Application Logic Server...");
        try (
            Socket socket = new Socket(HOST, PORT);
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))
        ) {
            // New Protocol: BATCH_EXECUTE|modelName|technique
            String command = "BATCH_EXECUTE|" + chosenModel + "|" + chosenTechnique;
            out.println(command);
            System.out.println("🚀 Sent batch processing request: " + command);

            String response;
            while ((response = in.readLine()) != null) {
                System.out.println("[Server Update] -> " + response);
                if (response.startsWith("STATUS|COMPLETED") || response.startsWith("ERROR")) {
                    break;
                }
            }
        } catch (Exception e) {
            System.err.println("❌ Client Error: " + e.getMessage());
        }
    }
}