package com.masakgram.server;

import com.masakgram.db.DatabaseManager;
import com.masakgram.llm.OllamaClient;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;

public class ClientHandler implements Runnable {
    private final Socket socket;

    public ClientHandler(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        try (
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true)
        ) {
            String inputLine = in.readLine();
            
            if (inputLine != null && inputLine.startsWith("BATCH_EXECUTE|")) {
                String[] tokens = inputLine.split("\\|");
                String modelName = tokens[1].trim();
                String technique = tokens[2].trim();

                System.out.println("🚀 Starting batch execution for model: " + modelName + ", technique: " + technique);

                // Get all transcripts
                List<PipelineEngine.TranscriptRecord> records = PipelineEngine.getAllTranscriptsFromDB();
                if (records.isEmpty()) {
                    out.println("ERROR|No valid transcripts processed or paths are unreachable.");
                    return;
                }

                out.println("STATUS|RUNNING|Found " + records.size() + " records. Initializing components...");
                
                String targetTag = fetchModelTagFromDB(modelName);
                System.out.println("🔍 Using model tag: " + targetTag);

                try {
                    String systemPrompt = PipelineEngine.loadSystemPrompt(technique);
                    System.out.println("📝 System prompt loaded, length: " + systemPrompt.length());

                    int completedCount = 0;
                    int failedCount = 0;

                    for (PipelineEngine.TranscriptRecord record : records) {
                        System.out.println("📄 Processing transcript " + record.transcriptId + " (Reel " + record.reelId + ")");
                        out.println("STATUS|RUNNING|Processing Reel ID: " + record.reelId + " (Transcript " + record.transcriptId + ")");

                        // Log experiment start
                        int experimentId = PipelineEngine.logExperimentStart(record.transcriptId, modelName, technique);
                        if (experimentId == -1) {
                            out.println("STATUS|WARNING|Failed to create experiment for Reel " + record.reelId);
                            failedCount++;
                            continue;
                        }

                        // Build user prompt
                        String completeUserPrompt = PipelineEngine.buildUserPrompt(technique, record.text);
                        System.out.println("📝 User prompt size: " + completeUserPrompt.length() + " chars");
                        
                        // Debug: save prompt to file
                        try {
                            String debugDir = "debug_prompts/";
                            Files.createDirectories(Paths.get(debugDir));
                            Files.writeString(Paths.get(debugDir + "prompt_reel_" + record.reelId + ".txt"), 
                                "=== SYSTEM PROMPT ===\n" + systemPrompt + "\n\n=== USER PROMPT ===\n" + completeUserPrompt);
                        } catch (Exception e) {
                            // Ignore debug write errors
                        }

                        // Query LLM
                        String rawJsonResponse = OllamaClient.queryLLM(targetTag, systemPrompt, completeUserPrompt);

                        if (rawJsonResponse != null && !rawJsonResponse.isBlank()) {
                            boolean saved = PipelineEngine.savePipelineOutputToDB(record.reelId, experimentId, rawJsonResponse);

                            if (saved) {
                                PipelineEngine.updateExperimentStatus(experimentId, "completed");
                                completedCount++;
                                out.println("STATUS|RUNNING|✅ Successfully stored analysis for Reel ID: " + record.reelId);
                            } else {
                                PipelineEngine.updateExperimentStatus(experimentId, "failed");
                                failedCount++;
                                out.println("STATUS|WARNING|❌ Incomplete/invalid result for Reel ID: " + record.reelId);
                            }
                        } else {
                            PipelineEngine.updateExperimentStatus(experimentId, "failed");
                            failedCount++;
                            out.println("STATUS|WARNING|❌ Inference returned blank output for Reel ID: " + record.reelId);
                        }
                    } 

                    out.println("STATUS|COMPLETED|Batch execution complete. ✅ " + completedCount + " succeeded, ❌ " + failedCount + " failed.");
                    System.out.println("📊 Batch complete: " + completedCount + " succeeded, " + failedCount + " failed");

                } catch (Exception e) {
                    out.println("ERROR|Pipeline breakdown: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        } catch (Exception e) {
            System.err.println("❌ Thread handler operation exception: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private String fetchModelTagFromDB(String modelName) {
        String query = "SELECT model_tag FROM llm_model WHERE LOWER(model_name) = LOWER(?) OR LOWER(model_tag) = LOWER(?)";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, modelName);
            stmt.setString(2, modelName);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String tag = rs.getString("model_tag");
                    System.out.println("✅ Found model tag: " + tag + " for model: " + modelName);
                    return tag;
                }
            }
        } catch (Exception e) {
            System.err.println("❌ Failed model tag lookup: " + e.getMessage());
        }
        // Default fallback
        System.out.println("⚠️ Using default model tag: llama3.2:3b");
        return "llama3.2:3b";
    }
}