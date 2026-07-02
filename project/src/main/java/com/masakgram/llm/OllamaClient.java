package com.masakgram.llm;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class OllamaClient {
    private static final String OLLAMA_BASE_URL = "http://localhost:11434";

    public static boolean testLLMConnection() {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(OLLAMA_BASE_URL + "/api/tags"))
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            boolean connected = response.statusCode() == 200;
            if (connected) {
                System.out.println("✅ Ollama service is running");
            } else {
                System.err.println("⚠️ Ollama responded with status: " + response.statusCode());
            }
            return connected;
        } catch (Exception e) {
            System.err.println("❌ Ollama connection failed: " + e.getMessage());
            return false;
        }
    }

    public static String queryLLM(String modelTag, String systemPrompt, String userPrompt) {
        System.out.println("🔄 Querying Ollama model: " + modelTag);
        System.out.println("📝 System prompt length: " + systemPrompt.length());
        System.out.println("📝 User prompt length: " + userPrompt.length());
        
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMinutes(20))
                    .build();

            // Escape JSON
            String escapedSystem = escapeJson(systemPrompt);
            String escapedUser = escapeJson(userPrompt);

            String jsonPayload = "{"
                    + "\"model\": \"" + modelTag + "\","
                    + "\"messages\": ["
                    + "  { \"role\": \"system\", \"content\": \"" + escapedSystem + "\" },"
                    + "  { \"role\": \"user\", \"content\": \"" + escapedUser + "\" }"
                    + "],"
                    + "\"stream\": false,"
                    + "\"format\": \"json\""
                    + "}";

            System.out.println("📤 Sending request to Ollama...");

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(OLLAMA_BASE_URL + "/api/chat"))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .timeout(Duration.ofMinutes(20))
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

            long startTime = System.currentTimeMillis();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            long duration = System.currentTimeMillis() - startTime;
            
            System.out.println("⏱️ LLM response received in " + duration + "ms");
            
            if (response.statusCode() == 200) {
                String body = response.body();
                System.out.println("📥 Response size: " + (body != null ? body.length() : 0) + " chars");
                return body;
            } else {
                System.err.println("❌ Ollama API error: " + response.statusCode());
                System.err.println("Response: " + (response.body() != null ? response.body().substring(0, Math.min(200, response.body().length())) : "empty"));
                return null;
            }
        } catch (Exception e) {
            System.err.println("❌ Inference execution exception: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    private static String escapeJson(String input) {
        if (input == null) return "";
        return input.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\b", "\\b")
                    .replace("\f", "\\f")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t");
    }
}