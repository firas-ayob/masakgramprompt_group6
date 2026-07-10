package com.masakgram.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.masakgram.db.DatabaseManager;
import com.masakgram.dto.IngredientDTO;
import com.masakgram.dto.NutritionResultDTO;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class PipelineEngine {
    private static final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true);

    private static String sanitizePath(String rawPath) {
        if (rawPath == null) return "";
        String cleanPath = rawPath.replace("\\", "/");
        cleanPath = cleanPath.replaceAll("/+", "/");
        return cleanPath.trim();
    }

    public static class TranscriptRecord {
        public int transcriptId;
        public int reelId;
        public String text;
        
        public TranscriptRecord(int transcriptId, int reelId, String text) {
            this.transcriptId = transcriptId;
            this.reelId = reelId;
            this.text = text;
        }
    }

    /**
     * ✅ PIPELINE UTILITY: Safely converts Java Double objects into database-compatible Floats.
     * Maps null references directly to SQL NULL flags to prevent application crashes.
     */
    private static void setSafeFloat(PreparedStatement stmt, int index, Double value) throws SQLException {
        if (value == null) {
            stmt.setNull(index, java.sql.Types.FLOAT);
        } else {
            stmt.setFloat(index, value.floatValue());
        }
    }
    
    public static List<TranscriptRecord> getAllTranscriptsFromDB() {
        List<TranscriptRecord> list = new ArrayList<>();
        String query = "SELECT transcript_id, reel_id, file_path FROM transcript ORDER BY reel_id ASC";
        
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query);
             ResultSet rs = stmt.executeQuery()) {
            
            while (rs.next()) {
                int transcriptId = rs.getInt("transcript_id");
                int reelId = rs.getInt("reel_id");
                String cleanPath = sanitizePath(rs.getString("file_path"));
                
                try {
                    // ✅ Read file as UTF-8
                    String textContent = Files.readString(Paths.get(cleanPath));
                    if (textContent == null || textContent.isBlank()) {
                        System.err.println("⚠️ Empty content for Reel " + reelId);
                        textContent = "Transkrip tidak dapat dibaca untuk Reel ID " + reelId;
                    }
                    list.add(new TranscriptRecord(transcriptId, reelId, textContent));
                } catch (IOException e) {
                    System.err.println("⚠️ File not found: " + cleanPath);
                    String fallbackText = "Transkrip tidak dapat dibaca dari fail fizikal untuk Reel ID " + reelId;
                    list.add(new TranscriptRecord(transcriptId, reelId, fallbackText));
                }
            }
        } catch (SQLException e) {
            System.err.println("Database error: " + e.getMessage());
        }
        return list;
    }

    public static int logExperimentStart(int transcriptId, String modelName, String techniqueName) {
        int modelId = getSeededModelId(modelName);
        int techniqueId = getSeededTechniqueId(techniqueName);

        String query = "INSERT INTO experiment (transcript_id, model_id, technique_id, status, executed_at) "
                     + "VALUES (?, ?, ?, 'running', NOW())";
                     
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query, Statement.RETURN_GENERATED_KEYS)) {
            
            stmt.setInt(1, transcriptId);
            stmt.setInt(2, modelId);
            stmt.setInt(3, techniqueId);
            stmt.executeUpdate();

            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            System.err.println("❌ DB Error: " + e.getMessage());
        }
        return -1;
    }

    private static int getSeededModelId(String modelName) {
        if (modelName == null) return 1;
        String input = modelName.toLowerCase().trim();
        
        // Map display names to database model names
        Map<String, String> modelMapping = new LinkedHashMap<>();
        modelMapping.put("medgemma", "medgemma");           // Must be first
        modelMapping.put("sea-lion", "gemma-sea-lion");     // Sea-Lion
        modelMapping.put("sealion", "gemma-sea-lion");      // Sea-Lion alias
        modelMapping.put("gemma-sea-lion", "gemma-sea-lion"); // Sea-Lion
        modelMapping.put("gemma", "gemma");                 // Base Gemma (not sea-lion)
        modelMapping.put("llama", "llama");
        modelMapping.put("phi4", "phi");
        modelMapping.put("phi", "phi");
        modelMapping.put("qwen", "qwen");
        
        // Find which model this input matches
        String targetDbName = null;
        for (Map.Entry<String, String> entry : modelMapping.entrySet()) {
            if (input.contains(entry.getKey())) {
                targetDbName = entry.getValue();
                break;
            }
        }
        
        if (targetDbName == null) {
            System.err.println("⚠️ No mapping found for: " + modelName);
            return 1;
        }
        
        // Query database for the matched model
        String query = "SELECT model_id FROM llm_model WHERE LOWER(model_name) LIKE ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            
            stmt.setString(1, "%" + targetDbName + "%");
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    int id = rs.getInt("model_id");
                    System.out.println("✅ Mapped '" + modelName + "' → model_id: " + id + " (" + targetDbName + ")");
                    return id;
                }
            }
        } catch (SQLException e) {
            System.err.println("Error mapping model: " + e.getMessage());
        }
        
        return 1;
    }

    private static int getSeededTechniqueId(String techniqueName) {
        if (techniqueName == null) return 1;
        String input = techniqueName.toLowerCase().replaceAll("[\\s_-]", "");
        
        // Map technique names
        Map<String, String> techMapping = new LinkedHashMap<>();
        techMapping.put("structuredoutput", "structured-output");
        techMapping.put("structuredoutput", "structured-output");
        techMapping.put("structured", "structured-output");
        techMapping.put("chainofthought", "chain-of-thought");
        techMapping.put("cot", "chain-of-thought");
        techMapping.put("fewshot", "few-shot");
        techMapping.put("few", "few-shot");
        techMapping.put("zeroshot", "zero-shot");
        techMapping.put("zero", "zero-shot");
        
        String targetDbName = null;
        for (Map.Entry<String, String> entry : techMapping.entrySet()) {
            if (input.contains(entry.getKey())) {
                targetDbName = entry.getValue();
                break;
            }
        }
        
        if (targetDbName == null) {
            System.err.println("⚠️ No mapping found for technique: " + techniqueName);
            return 1;
        }
        
        String query = "SELECT technique_id FROM prompt_technique WHERE LOWER(technique_name) LIKE ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            
            stmt.setString(1, "%" + targetDbName + "%");
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("technique_id");
                }
            }
        } catch (SQLException e) {
            System.err.println("Error mapping technique: " + e.getMessage());
        }
        
        return 1;
    }

    // ============================================================
    // ✅ FIXED: Save Pipeline Output
    // ============================================================
    public static boolean savePipelineOutputToDB(int reelId, int experimentId, String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            System.err.println("❌ Empty JSON for Reel " + reelId);
            return false;
        }

        // ✅ Parse the JSON
        NutritionResultDTO dto = parseNutritionData(rawJson, reelId);
        
        // ✅ If parsing fails, create minimal valid DTO
        if (dto == null) {
            System.err.println("⚠️ Failed to parse JSON, creating minimal record for Reel " + reelId);
            dto = new NutritionResultDTO();
            dto.recipeName = "Recipe Reel " + reelId;
            dto.servingsEstimated = 1.0;
            dto.ingredients = new ArrayList<>();
        }

        // ✅ Ensure ingredients list is not null
        if (dto.ingredients == null) {
            dto.ingredients = new ArrayList<>();
        }

        // ✅ Save to database
        return saveNutritionResult(experimentId, reelId, dto, rawJson);
    }

    // ============================================================
    // ✅ FIXED: Parse Nutrition Data with fallback
    // ============================================================
    private static NutritionResultDTO parseNutritionData(String rawJson, int reelId) {
        try {
            String cleanJson = cleanJsonResponse(rawJson);
            NutritionResultDTO dto = mapper.readValue(cleanJson, NutritionResultDTO.class);
            
            // ✅ Trigger the math calculation before returning!
            if (dto != null) {
                dto.calculateTotals();
            }
            
            if (dto.ingredients != null) {
                for (IngredientDTO ing : dto.ingredients) {
                    if (ing != null) ing.postProcess();
                }
            }
            return dto;
        } catch (Exception e) {
            System.err.println("❌ Failed to parse JSON for Reel " + reelId + ": " + e.getMessage());
            return null;
        }
    }

    // ============================================================
    // ✅ Clean JSON Response
    // ============================================================
    private static String cleanJsonResponse(String rawJson) {
        if (rawJson == null) return "";
        
        String cleaned = rawJson.trim();
        
        // Extract from Ollama wrapper
        try {
            JsonNode root = mapper.readTree(cleaned);
            if (root.has("message") && root.get("message").has("content")) {
                cleaned = root.get("message").get("content").asText().trim();
            }
        } catch (Exception e) {}
        
        // Remove markdown code blocks
        cleaned = cleaned.replaceAll("```json\\s*", "")
                         .replaceAll("```\\s*", "")
                         .trim();
        
        // Fix common JSON issues
        cleaned = cleaned.replaceAll(",\\s*}", "}")
                         .replaceAll(",\\s*]", "]");
        
        // Find JSON start
        if (!cleaned.startsWith("{") && !cleaned.startsWith("[")) {
            int start = cleaned.indexOf('{');
            int startArray = cleaned.indexOf('[');
            if (startArray >= 0 && (start < 0 || startArray < start)) {
                start = startArray;
            }
            if (start >= 0) {
                cleaned = cleaned.substring(start);
            }
        }
        
        return cleaned;
    }

    // ============================================================
    // ✅ FIXED: Save Nutrition Result - returns boolean
    // ============================================================
    private static boolean saveNutritionResult(int experimentId, int reelId, 
            NutritionResultDTO dto, String rawJson) {
        
        if (dto == null) {
            System.err.println("⚠️ No data to save for Reel " + reelId);
            return false;
        }

        // ✅ Set defaults
        if (dto.recipeName == null || dto.recipeName.isBlank()) {
            dto.recipeName = "Recipe Reel " + reelId;
        }
        if (dto.servingsEstimated == null || dto.servingsEstimated <= 0) {
            dto.servingsEstimated = 1.0;
        }
        if (dto.ingredients == null) {
            dto.ingredients = new ArrayList<>();
        }

        String queryResult = "INSERT INTO nutrition_result ("
                + "experiment_id, recipe_name, servings_estimated, "
                + "serving_calories, serving_total_fat_g, serving_saturated_fat_g, serving_cholesterol_mg, "
                + "serving_sodium_mg, serving_carbohydrate_g, serving_fiber_g, serving_sugars_g, serving_protein_g, "
                + "serving_vitamin_d_mcg, serving_calcium_mg, serving_iron_mg, serving_potassium_mg, "
                + "total_calories, total_fat_g, total_saturated_fat_g, total_cholesterol_mg, "
                + "total_sodium_mg, total_carbohydrate_g, total_fiber_g, total_sugars_g, total_protein_g, "
                + "total_vitamin_d_mcg, total_calcium_mg, total_iron_mg, total_potassium_mg, "
                + "raw_json_output, json_valid"
                + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        String queryIngredient = "INSERT INTO ingredient_result ("
                + "result_id, name_original, name_en, quantity_value, "
                + "unit_original, unit_en, estimated_weight_g, "
                + "calories, total_fat_g, saturated_fat_g, cholesterol_mg, sodium_mg, "
                + "total_carbohydrate_g, dietary_fiber_g, total_sugars_g, protein_g, "
                + "vitamin_d_mcg, calcium_mg, iron_mg, potassium_mg"
                + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = DatabaseManager.getConnection()) {
            conn.setAutoCommit(false);

            int generatedResultId = -1;

            // ✅ SECTION 1: INSERT NUTRITION RESULT
            try (PreparedStatement stmt = conn.prepareStatement(queryResult, Statement.RETURN_GENERATED_KEYS)) {
                int idx = 1;

                // Meta & Core Fields
                stmt.setInt(idx++, experimentId);
                stmt.setString(idx++, dto.recipeName);
                
                // servings_estimated is defined as INT NULL in schema
                if (dto.servingsEstimated == null) {
                    stmt.setNull(idx++, java.sql.Types.INTEGER);
                } else {
                    stmt.setInt(idx++, dto.servingsEstimated.intValue());
                }

                // --- Per-Serving Values (Mapped directly from LLM fields, NO calculations) ---
                setSafeFloat(stmt, idx++, dto.sCalories);
                setSafeFloat(stmt, idx++, dto.sFat);
                setSafeFloat(stmt, idx++, dto.sSaturatedFat);
                setSafeFloat(stmt, idx++, dto.sCholesterol);
                setSafeFloat(stmt, idx++, dto.sSodium);
                setSafeFloat(stmt, idx++, dto.sCarbohydrates);
                setSafeFloat(stmt, idx++, dto.sDietaryFiber);
                setSafeFloat(stmt, idx++, dto.sTotalSugars);
                setSafeFloat(stmt, idx++, dto.sProtein);
                setSafeFloat(stmt, idx++, dto.sVitaminD);
                setSafeFloat(stmt, idx++, dto.sCalcium);
                setSafeFloat(stmt, idx++, dto.sIron);
                setSafeFloat(stmt, idx++, dto.sPotassium);

                // --- Whole-Recipe Totals (Mapped directly from LLM fields, NO calculations) ---
                setSafeFloat(stmt, idx++, dto.tCalories);
                setSafeFloat(stmt, idx++, dto.tFat);
                setSafeFloat(stmt, idx++, dto.tSaturatedFat);
                setSafeFloat(stmt, idx++, dto.tCholesterol);
                setSafeFloat(stmt, idx++, dto.tSodium);
                setSafeFloat(stmt, idx++, dto.tCarbohydrates);
                setSafeFloat(stmt, idx++, dto.tDietaryFiber);
                setSafeFloat(stmt, idx++, dto.tTotalSugars);
                setSafeFloat(stmt, idx++, dto.tProtein);
                setSafeFloat(stmt, idx++, dto.tVitaminD);
                setSafeFloat(stmt, idx++, dto.tCalcium);
                setSafeFloat(stmt, idx++, dto.tIron);
                setSafeFloat(stmt, idx++, dto.tPotassium);

                // LLM Response Tracking Metadata
                stmt.setString(idx++, rawJson);
                stmt.setBoolean(idx++, true);

                int affected = stmt.executeUpdate();
                if (affected == 0) {
                    System.err.println("❌ Critical Error: No rows inserted into nutrition_result.");
                    conn.rollback();
                    return false;
                }

                try (ResultSet rs = stmt.getGeneratedKeys()) {
                    if (rs.next()) {
                        generatedResultId = rs.getInt(1);
                        System.out.println("✅ nutrition_result row successfully synchronized. ID: " + generatedResultId);
                    }
                }
            }

         // ✅ SECTION 2: INSERT INGREDIENTS (BATCH UPDATES)
            if (generatedResultId != -1 && dto.ingredients != null && !dto.ingredients.isEmpty()) {
                int insertedCount = 0;
                try (PreparedStatement stmt = conn.prepareStatement(queryIngredient)) {
                    for (IngredientDTO ing : dto.ingredients) {
                        if (ing == null) continue;
                        
                        // 1. Run post-processing to clean text metrics, apply regex, and fill fallback grams
                        ing.postProcess();
                        
                        // 2. Validate target object rules (name_original cannot be null or blank)
                        String origName = ing.nameOriginal; 
                        if (origName == null || origName.isBlank()) continue;

                        // Fallback language translation if name_en is absent
                        String enName = (ing.nameEn != null && !ing.nameEn.isBlank()) ? ing.nameEn : origName;

                        // 3. Map parameters cleanly matching your IngredientDTO field variables
                        stmt.setInt(1, generatedResultId); // result_id
                        stmt.setString(2, origName);        // name_original
                        stmt.setString(3, enName);          // name_en
                        
                        setSafeFloat(stmt, 4, ing.quantityValue);      // quantity_value
                        stmt.setString(5, ing.unitOriginal);           // unit_original
                        stmt.setString(6, ing.unitEn);                 // unit_en
                        setSafeFloat(stmt, 7, ing.estimatedWeightG);   // estimated_weight_g

                        // 4. Bind Nutritional Macro/Micro Fields (Safely catches NULL to prevent NPE)
                        setSafeFloat(stmt, 8, ing.calories);
                        setSafeFloat(stmt, 9, ing.totalFatG);
                        setSafeFloat(stmt, 10, ing.saturatedFatG);      
                        setSafeFloat(stmt, 11, ing.cholesterolMg);      
                        setSafeFloat(stmt, 12, ing.sodiumMg);
                        setSafeFloat(stmt, 13, ing.totalCarbohydrateG);
                        setSafeFloat(stmt, 14, ing.dietaryFiberG);      
                        setSafeFloat(stmt, 15, ing.totalSugarsG);       
                        setSafeFloat(stmt, 16, ing.proteinG);
                        setSafeFloat(stmt, 17, ing.vitaminDMcg);        
                        setSafeFloat(stmt, 18, ing.calciumMg);          
                        setSafeFloat(stmt, 19, ing.ironMg);             
                        setSafeFloat(stmt, 20, ing.potassiumMg);        

                        stmt.addBatch();
                        insertedCount++;
                    }

                    if (insertedCount > 0) {
                        stmt.executeBatch();
                        System.out.println("✅ Synchronized " + insertedCount + " ingredient records into ingredient_result.");
                    }
                }
            }

            conn.commit();
            System.out.println("==== ✅ TRANSACTION COMMITTED SUCCESSFULLY ====");
            return true;

        } catch (SQLException e) {
            System.err.println("❌ Database rollback triggered due to SQLException: " + e.getMessage());
            e.printStackTrace();
            return false;
        } catch (Exception e) {
            System.err.println("❌ Non-SQL System failure: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public static void updateExperimentStatus(int experimentId, String status) {
        String query = "UPDATE experiment SET status = ? WHERE experiment_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, status);
            stmt.setInt(2, experimentId);
            int updated = stmt.executeUpdate();
            if (updated > 0) {
                System.out.println("📊 Experiment " + experimentId + " → " + status);
            }
        } catch (SQLException e) {
            System.err.println("Error updating status: " + e.getMessage());
        }
    }

    public static String buildUserPrompt(String techniqueName, String transcriptText) throws IOException {
        String cleanedTechName = techniqueName.trim().toLowerCase().replace("-", "_");
        String userPromptPath = "prompts/" + cleanedTechName + "_user.txt";
        return Files.readString(Paths.get(userPromptPath)).replace("{{TRANSCRIPT}}", transcriptText);
    }

    public static String loadSystemPrompt(String techniqueName) throws IOException {
        String cleanedTechName = techniqueName.trim().toLowerCase().replace("-", "_");
        String systemPromptPath = "prompts/" + cleanedTechName + "_system.txt";
        return Files.readString(Paths.get(systemPromptPath));
    }
}