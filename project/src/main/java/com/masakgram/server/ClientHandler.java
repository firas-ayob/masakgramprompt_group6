package com.masakgram.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.masakgram.db.DatabaseManager;
import com.masakgram.llm.OllamaClient;

import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;

public class ClientHandler implements Runnable {
    private final Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private ObjectMapper mapper = new ObjectMapper();

    public ClientHandler(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        try {
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);

            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                System.out.println("📨 Received: " + inputLine);
                
                if (inputLine.startsWith("BATCH_EXECUTE|")) {
                    handleBatchExecute(inputLine);
                } else if (inputLine.startsWith("GET_MATRIX|")) {
                    handleGetMatrix(inputLine);
                } else if (inputLine.startsWith("GET_METRICS|")) {
                    handleGetMetrics(inputLine);
                } else if (inputLine.startsWith("GET_DETAILS|")) {
                    handleGetDetails(inputLine);
                } else if (inputLine.startsWith("GET_TRANSCRIPT|")) {
                    handleGetTranscript(inputLine);
                } else if (inputLine.startsWith("GET_LAYERS|")) {
                    handleGetLayers(inputLine);
                } else if (inputLine.startsWith("EXPORT_CSV|")) {
                    handleExportCSV(inputLine);
                } else {
                    out.println("ERROR|Unknown command: " + inputLine);
                }
            }
        } catch (IOException e) {
            System.err.println("❌ Client connection error: " + e.getMessage());
        } finally {
            try {
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    // ============================================================
    // HANDLER: GET_DETAILS - FIXED WITH PROPER HALLUCINATION DATA
    // ============================================================
    private void handleGetDetails(String inputLine) {
        String data = inputLine.substring("GET_DETAILS|".length());
        String[] params = data.split("\\|");
        
        try {
            int transcriptId = Integer.parseInt(params[0]);
            String modelName = params[1];
            String techniqueName = params[2];

            System.out.println("🔍 Getting details for: transcript=" + transcriptId + 
                             ", model=" + modelName + ", technique=" + techniqueName);

            ObjectNode details = mapper.createObjectNode();

            // Get transcript info
            String transcriptSql = "SELECT verified_by_name FROM transcript WHERE transcript_id = ?";
            try (Connection conn = DatabaseManager.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(transcriptSql)) {
                pstmt.setInt(1, transcriptId);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        details.put("transcript_id", transcriptId);
                        details.put("file_name", rs.getString("verified_by_name"));
                    } else {
                        details.put("transcript_id", transcriptId);
                        details.put("file_name", "Unknown");
                    }
                }
            }

            // Get experiment results
            String expSql = "SELECT e.status, nr.result_id, nr.recipe_name, nr.servings_estimated, "
                          + "       nr.total_calories, nr.total_protein_g, nr.total_carbohydrate_g, nr.total_fat_g "
                          + "FROM experiment e "
                          + "INNER JOIN llm_model m ON e.model_id = m.model_id "
                          + "INNER JOIN prompt_technique pt ON e.technique_id = pt.technique_id "
                          + "LEFT JOIN nutrition_result nr ON e.experiment_id = nr.experiment_id "
                          + "WHERE e.transcript_id = ? AND m.model_name LIKE ? AND pt.technique_name = ?";

            String status = "UNEXECUTED";
            int resultId = -1;
            boolean hasResult = false;

            try (Connection conn = DatabaseManager.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(expSql)) {
                pstmt.setInt(1, transcriptId);
                pstmt.setString(2, "%" + modelName.substring(0, Math.min(modelName.length(), 8)) + "%");
                pstmt.setString(3, techniqueName);

                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        status = rs.getString("status");
                        details.put("status", status != null ? status : "PENDING");
                        resultId = rs.getInt("result_id");
                        hasResult = !rs.wasNull();
                        System.out.println("  - Experiment found: status=" + status + ", resultId=" + resultId);

                        if (hasResult && resultId > 0 && status != null && status.equalsIgnoreCase("completed")) {
                            ObjectNode nutrition = mapper.createObjectNode();
                            nutrition.put("recipe_name", rs.getString("recipe_name"));
                            nutrition.put("servings", rs.getInt("servings_estimated"));
                            nutrition.put("calories", rs.getFloat("total_calories"));
                            nutrition.put("protein", rs.getFloat("total_protein_g"));
                            nutrition.put("carbs", rs.getFloat("total_carbohydrate_g"));
                            nutrition.put("fat", rs.getFloat("total_fat_g"));
                            details.set("nutrition", nutrition);
                        }
                    } else {
                        details.put("status", "UNEXECUTED");
                    }
                }
            }

            // ============================================================
            // GET LLM INGREDIENTS
            // ============================================================
            ArrayNode llmIngredients = mapper.createArrayNode();
            List<Map<String, Object>> llmIngredientList = new ArrayList<>();
            
            if (resultId > 0) {
                String ingSql = "SELECT ingredient_id, name_original, quantity_value, unit_original "
                              + "FROM ingredient_result WHERE result_id = ?";
                try (Connection conn = DatabaseManager.getConnection();
                     PreparedStatement pstmt = conn.prepareStatement(ingSql)) {
                    pstmt.setInt(1, resultId);
                    try (ResultSet rs = pstmt.executeQuery()) {
                        while (rs.next()) {
                            ObjectNode ing = mapper.createObjectNode();
                            int ingId = rs.getInt("ingredient_id");
                            String name = rs.getString("name_original");
                            float quantity = rs.getFloat("quantity_value");
                            String unit = rs.getString("unit_original");
                            
                            ing.put("id", ingId);
                            ing.put("name", name != null ? name : "Unknown");
                            ing.put("quantity", quantity);
                            ing.put("unit", unit != null ? unit : "");
                            
                            llmIngredients.add(ing);
                            
                            Map<String, Object> ingMap = new HashMap<>();
                            ingMap.put("id", ingId);
                            ingMap.put("name", name != null ? name.toLowerCase().trim() : "");
                            ingMap.put("original_name", name != null ? name : "");
                            llmIngredientList.add(ingMap);
                            
                            System.out.println("  - LLM Ingredient: " + name);
                        }
                    }
                }
            }
            details.set("llm_ingredients", llmIngredients);
            System.out.println("  - Total LLM ingredients: " + llmIngredientList.size());

            // ============================================================
            // GET GROUND TRUTH INGREDIENTS
            // ============================================================
            ArrayNode gtIngredients = mapper.createArrayNode();
            List<Map<String, Object>> gtIngredientList = new ArrayList<>();
            
            String gtSql = "SELECT gt_ingredient_id, name_original, name_en, quantity_expression "
                         + "FROM ground_truth_ingredient gti "
                         + "INNER JOIN ground_truth_reel gtr ON gti.gt_reel_id = gtr.gt_reel_id "
                         + "WHERE gtr.transcript_id = ?";
            try (Connection conn = DatabaseManager.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(gtSql)) {
                pstmt.setInt(1, transcriptId);
                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        ObjectNode ing = mapper.createObjectNode();
                        int gtId = rs.getInt("gt_ingredient_id");
                        String name = rs.getString("name_original");
                        String nameEn = rs.getString("name_en");
                        String quantity = rs.getString("quantity_expression");
                        
                        ing.put("id", gtId);
                        ing.put("name", name != null ? name : "Unknown");
                        ing.put("name_en", nameEn != null ? nameEn : "");
                        ing.put("quantity", quantity != null ? quantity : "");
                        
                        gtIngredients.add(ing);
                        
                        Map<String, Object> ingMap = new HashMap<>();
                        ingMap.put("id", gtId);
                        ingMap.put("name", name != null ? name.toLowerCase().trim() : "");
                        ingMap.put("original_name", name != null ? name : "");
                        ingMap.put("name_en", nameEn);
                        gtIngredientList.add(ingMap);
                        
                        System.out.println("  - GT Ingredient: " + name);
                    }
                }
            }
            details.set("gt_ingredients", gtIngredients);
            System.out.println("  - Total GT ingredients: " + gtIngredientList.size());

            // ============================================================
            // PERFORM HALLUCINATION ANALYSIS
            // ============================================================
            ObjectNode hallucinationAnalysis = performDetailedHallucinationAnalysis(
                llmIngredientList, 
                gtIngredientList
            );
            details.set("hallucination_analysis", hallucinationAnalysis);
            
            System.out.println("  - Hallucination status: " + 
                (hallucinationAnalysis.has("status") ? hallucinationAnalysis.get("status").asText() : "UNKNOWN"));

            // Send response
            String jsonResponse = mapper.writeValueAsString(details);
            System.out.println("  - Response size: " + jsonResponse.length() + " chars");
            out.println("SUCCESS|" + jsonResponse);
            
        } catch (Exception e) {
            System.err.println("❌ Error in handleGetDetails: " + e.getMessage());
            e.printStackTrace();
            out.println("ERROR|" + e.getMessage());
        }
    }

    // ============================================================
    // DETAILED HALLUCINATION ANALYSIS - FIXED
    // ============================================================
    private ObjectNode performDetailedHallucinationAnalysis(
            List<Map<String, Object>> llmIngredients,
            List<Map<String, Object>> gtIngredients) {
        
        ObjectNode analysis = mapper.createObjectNode();
        ArrayNode hallucinatedIngredients = mapper.createArrayNode();
        ArrayNode missingIngredients = mapper.createArrayNode();
        ArrayNode matchedIngredients = mapper.createArrayNode();

        System.out.println("🔍 Performing hallucination analysis...");
        System.out.println("  - LLM ingredients: " + llmIngredients.size());
        System.out.println("  - GT ingredients: " + gtIngredients.size());

        // If either list is empty, return N/A with what we have
        if (llmIngredients.isEmpty() || gtIngredients.isEmpty()) {
            analysis.put("status", "N/A");
            analysis.put("summary", "Insufficient data for hallucination analysis");
            analysis.put("total_llm_ingredients", llmIngredients.size());
            analysis.put("total_gt_ingredients", gtIngredients.size());
            analysis.put("hallucinated_count", 0);
            analysis.put("missing_count", 0);
            analysis.put("matched_count", 0);
            analysis.set("hallucinated", hallucinatedIngredients);
            analysis.set("missing", missingIngredients);
            analysis.set("matched", matchedIngredients);
            return analysis;
        }

        // Create sets for quick lookup with partial matching
        for (Map<String, Object> llm : llmIngredients) {
            String llmName = (String) llm.get("name");
            boolean matched = false;
            String matchedGtName = null;
            
            for (Map<String, Object> gt : gtIngredients) {
                String gtName = (String) gt.get("name");
                // Check if names match (including partial matches)
                if (gtName.contains(llmName) || llmName.contains(gtName)) {
                    matched = true;
                    matchedGtName = (String) gt.get("original_name");
                    break;
                }
            }
            
            if (!matched) {
                // This is a hallucinated ingredient (LLM predicted but not in GT)
                ObjectNode hallucinated = mapper.createObjectNode();
                hallucinated.put("name", (String) llm.get("original_name"));
                hallucinated.put("id", (Integer) llm.get("id"));
                hallucinated.put("reason", "Not found in ground truth ingredients");
                hallucinatedIngredients.add(hallucinated);
                System.out.println("  ⚠️ Hallucinated: " + llm.get("original_name"));
            } else {
                // Find which GT ingredient it matches
                for (Map<String, Object> gt : gtIngredients) {
                    String gtName = (String) gt.get("name");
                    if (gtName.contains(llmName) || llmName.contains(gtName)) {
                        ObjectNode matched1 = mapper.createObjectNode();
                        matched1.put("llm_name", (String) llm.get("original_name"));
                        matched1.put("gt_name", (String) gt.get("original_name"));
                        matched1.put("match_type", "Match Found");
                        matchedIngredients.add(matched1);
                        System.out.println("  ✅ Matched: " + llm.get("original_name") + " ↔ " + gt.get("original_name"));
                        break;
                    }
                }
            }
        }

        // Find missing ingredients (in GT but not predicted by LLM)
        for (Map<String, Object> gt : gtIngredients) {
            String gtName = (String) gt.get("name");
            boolean found = false;
            
            for (Map<String, Object> llm : llmIngredients) {
                String llmName = (String) llm.get("name");
                if (gtName.contains(llmName) || llmName.contains(gtName)) {
                    found = true;
                    break;
                }
            }
            
            if (!found) {
                ObjectNode missing = mapper.createObjectNode();
                missing.put("name", (String) gt.get("original_name"));
                missing.put("id", (Integer) gt.get("id"));
                if (gt.containsKey("name_en") && gt.get("name_en") != null) {
                    missing.put("name_en", (String) gt.get("name_en"));
                }
                missing.put("reason", "Not predicted by LLM");
                missingIngredients.add(missing);
                System.out.println("  ❌ Missing: " + gt.get("original_name"));
            }
        }

        // Determine overall status
        String status;
        String summary;
        int hallucinatedCount = hallucinatedIngredients.size();
        int missingCount = missingIngredients.size();
        int matchedCount = matchedIngredients.size();
        
        if (hallucinatedCount == 0 && missingCount == 0) {
            status = "NO_HALLUCINATION";
            summary = "All ingredients correctly predicted";
        } else if (hallucinatedCount > 0 && missingCount == 0) {
            status = "HALLUCINATION_PLUS";
            summary = "LLM added " + hallucinatedCount + " ingredient(s) not in ground truth";
        } else if (hallucinatedCount == 0 && missingCount > 0) {
            status = "HALLUCINATION_MINUS";
            summary = "LLM missed " + missingCount + " ingredient(s) from ground truth";
        } else {
            status = "HALLUCINATION_BOTH";
            summary = "LLM added " + hallucinatedCount + " extra ingredient(s) and missed " + 
                      missingCount + " ingredient(s)";
        }

        System.out.println("  - Final status: " + status);

        analysis.put("status", status);
        analysis.put("summary", summary);
        analysis.put("total_llm_ingredients", llmIngredients.size());
        analysis.put("total_gt_ingredients", gtIngredients.size());
        analysis.put("hallucinated_count", hallucinatedCount);
        analysis.put("missing_count", missingCount);
        analysis.put("matched_count", matchedCount);
        
        analysis.set("hallucinated", hallucinatedIngredients);
        analysis.set("missing", missingIngredients);
        analysis.set("matched", matchedIngredients);

        return analysis;
    }

    // ============================================================
    // OTHER HANDLERS
    // ============================================================

    private void handleBatchExecute(String inputLine) {
        String[] tokens = inputLine.split("\\|");
        String modelName = tokens[1].trim();
        String technique = tokens[2].trim();

        System.out.println("🚀 Starting batch execution for model: " + modelName + ", technique: " + technique);

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

                int experimentId = PipelineEngine.logExperimentStart(record.transcriptId, modelName, technique);
                if (experimentId == -1) {
                    out.println("STATUS|WARNING|Failed to create experiment for Reel " + record.reelId);
                    failedCount++;
                    continue;
                }

                String completeUserPrompt = PipelineEngine.buildUserPrompt(technique, record.text);
                System.out.println("📝 User prompt size: " + completeUserPrompt.length() + " chars");

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

    private void handleGetMatrix(String inputLine) {
        String techniqueName = inputLine.substring("GET_MATRIX|".length());
        try {
            ObjectNode response = mapper.createObjectNode();
            ArrayNode rows = mapper.createArrayNode();

            String sql = "SELECT t.transcript_id, "
                       + "       COALESCE(t.file_name, 'No Name') AS transcript_file, "
                       + "       m.model_name, "
                       + "       e.status, "
                       + "       e.experiment_id "
                       + "FROM transcript t "
                       + "LEFT JOIN experiment e ON t.transcript_id = e.transcript_id "
                       + "     AND e.technique_id = (SELECT technique_id FROM prompt_technique WHERE technique_name = ? LIMIT 1) "
                       + "LEFT JOIN llm_model m ON e.model_id = m.model_id "
                       + "ORDER BY t.transcript_id ASC";

            try (Connection conn = DatabaseManager.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, techniqueName);
                try (ResultSet rs = pstmt.executeQuery()) {
                    Map<Integer, ObjectNode> matrixMap = new LinkedHashMap<>();

                    while (rs.next()) {
                        int id = rs.getInt("transcript_id");
                        String file = rs.getString("transcript_file");
                        String model = rs.getString("model_name");
                        String status = rs.getString("status");
                        int expId = rs.getInt("experiment_id");
                        boolean hasExpId = !rs.wasNull();

                        if (!matrixMap.containsKey(id)) {
                            ObjectNode row = mapper.createObjectNode();
                            row.put("transcript_id", id);
                            row.put("transcript_file", file != null ? file : "No Name");
                            row.put("Llama 3.2", "-");
                            row.put("Phi-4-mini", "-");
                            row.put("Qwen 2.5", "-");
                            row.put("Gemma-SEA", "-");
                            row.put("MedGemma", "-");
                            matrixMap.put(id, row);
                        }

                        if (model != null && status != null) {
                            ObjectNode row = matrixMap.get(id);
                            String colName = getModelColumnName(model);
                            String displayValue = status.toUpperCase();
                            if (hasExpId) {
                                displayValue += " (#" + expId + ")";
                            }
                            row.put(colName, displayValue);
                        }
                    }

                    for (ObjectNode row : matrixMap.values()) {
                        rows.add(row);
                    }
                }
            }

            response.put("success", true);
            response.set("data", rows);
            out.println("SUCCESS|" + mapper.writeValueAsString(response));
        } catch (Exception e) {
            out.println("ERROR|" + e.getMessage());
            e.printStackTrace();
        }
    }

    private void handleGetMetrics(String inputLine) {
        String techniqueName = inputLine.substring("GET_METRICS|".length());
        try {
            ObjectNode metrics = mapper.createObjectNode();

            String countSql = "SELECT COUNT(*) AS total FROM transcript";
            try (Connection conn = DatabaseManager.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(countSql);
                 ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    metrics.put("total_transcripts", rs.getInt("total"));
                }
            }

            String expSql = "SELECT COUNT(*) AS total FROM experiment";
            try (Connection conn = DatabaseManager.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(expSql);
                 ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    metrics.put("total_experiments", rs.getInt("total"));
                }
            }

            String rateSql = "SELECT "
                           + "  SUM(CASE WHEN LOWER(e.status) = 'completed' THEN 1 ELSE 0 END) as success_count, "
                           + "  SUM(CASE WHEN LOWER(e.status) = 'failed' THEN 1 ELSE 0 END) as failure_count "
                           + "FROM experiment e "
                           + "INNER JOIN prompt_technique pt ON e.technique_id = pt.technique_id "
                           + "WHERE pt.technique_name = ?";
            try (Connection conn = DatabaseManager.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(rateSql)) {
                pstmt.setString(1, techniqueName);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        int success = rs.getInt("success_count");
                        int failure = rs.getInt("failure_count");
                        int total = success + failure;

                        if (total > 0) {
                            metrics.put("success_rate", String.format("%.1f%%", ((double) success / total) * 100));
                            metrics.put("failure_rate", String.format("%.1f%%", ((double) failure / total) * 100));
                        } else {
                            metrics.put("success_rate", "0.0%");
                            metrics.put("failure_rate", "0.0%");
                        }
                    }
                }
            }

            out.println("SUCCESS|" + mapper.writeValueAsString(metrics));
        } catch (Exception e) {
            out.println("ERROR|" + e.getMessage());
            e.printStackTrace();
        }
    }

    private void handleGetTranscript(String inputLine) {
        String transcriptIdStr = inputLine.substring("GET_TRANSCRIPT|".length());
        try {
            int transcriptId = Integer.parseInt(transcriptIdStr);
            ObjectNode result = mapper.createObjectNode();

            String sql = "SELECT file_path FROM transcript WHERE transcript_id = ?";
            try (Connection conn = DatabaseManager.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, transcriptId);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        String filePath = rs.getString("file_path");
                        if (filePath != null) {
                            File file = new File(filePath);
                            if (file.exists()) {
                                String content = Files.readString(file.toPath());
                                result.put("content", content);
                                out.println("SUCCESS|" + mapper.writeValueAsString(result));
                                return;
                            } else {
                                result.put("content", "File not found: " + filePath);
                                out.println("SUCCESS|" + mapper.writeValueAsString(result));
                                return;
                            }
                        }
                    }
                }
            }
            result.put("content", "No transcript file found for ID: " + transcriptId);
            out.println("SUCCESS|" + mapper.writeValueAsString(result));
        } catch (Exception e) {
            out.println("ERROR|" + e.getMessage());
            e.printStackTrace();
        }
    }

    private void handleGetLayers(String inputLine) {
        try {
            List<String> layers = CSVExporterService.getAvailableLayers();
            ObjectNode response = mapper.createObjectNode();
            ArrayNode layersArray = mapper.createArrayNode();
            for (String layer : layers) {
                layersArray.add(layer);
            }
            response.set("layers", layersArray);
            out.println("SUCCESS|" + mapper.writeValueAsString(response));
        } catch (Exception e) {
            out.println("ERROR|" + e.getMessage());
            e.printStackTrace();
        }
    }

    private void handleExportCSV(String inputLine) {
        String data = inputLine.substring("EXPORT_CSV|".length());
        String[] params = data.split("\\|");
        
        try {
            String layerName = params[0];
            String targetDir = params[1];
            
            File dir = new File(targetDir);
            if (!dir.exists()) {
                dir.mkdirs();
            }
            
            String filePath = CSVExporterService.exportLayer(layerName, dir);
            out.println("SUCCESS|" + filePath);
        } catch (Exception e) {
            out.println("ERROR|" + e.getMessage());
            e.printStackTrace();
        }
    }

    // ============================================================
    // HELPER METHODS
    // ============================================================

    private String getModelColumnName(String modelName) {
        if (modelName == null) return modelName;
        if (modelName.contains("Llama 3.2")) return "Llama 3.2";
        if (modelName.contains("Phi-4")) return "Phi-4-mini";
        if (modelName.contains("Qwen 2.5")) return "Qwen 2.5";
        if (modelName.contains("Gemma-SEA")) return "Gemma-SEA";
        if (modelName.contains("MedGemma")) return "MedGemma";
        return modelName;
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
        return "llama3.2:3b";
    }
}