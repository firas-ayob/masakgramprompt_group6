package com.masakgram.test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.masakgram.dto.IngredientDTO;
import com.masakgram.server.PipelineEngine;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class LiveLlmExtractionTest {

    private static final ObjectMapper lenientMapper = new ObjectMapper();

    static {
        lenientMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        lenientMapper.configure(com.fasterxml.jackson.databind.MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true);
    }

    public static void main(String[] args) {
        System.out.println("--- 🧪 STARTING LIVE TRANSCRIPT TO LLM EXTRACTION TEST 🧪 ---");

        try {
            // 1. EXTRACT RAW TRANSCRIPT FROM DATABASE
            List<PipelineEngine.TranscriptRecord> records = PipelineEngine.getAllTranscriptsFromDB();
            if (records == null || records.isEmpty()) {
                System.out.println("❌ No transcript records found in your database.");
                return;
            }

            // Target the first transcript record for testing
            PipelineEngine.TranscriptRecord target = records.get(0);
            System.out.println("\n[STEP 1] Fetched Transcript from DB:");
            System.out.println(" 🔹 Transcript ID: " + target.transcriptId);
            System.out.println(" 🔹 Spoken Text   : \"" + target.text.trim() + "\"\n");

            // 2. CONSTRUCT SYSTEM PROMPT DIRECTLY
            String systemInstructions = "You are a recipe parser. Extract ingredients from the user transcript. " +
                    "Return ONLY a JSON object with an array named \"ingredients\". " +
                    "For each ingredient field, provide:\n" +
                    "- \"ingredient_name_original\": The Malay ingredient name spoken\n" +
                    "- \"ingredient_name_en\": Translated English name\n" +
                    "- \"quantity_value\": The number, or the raw phrase if it is text like 'secukup rasa'\n" +
                    "- \"quantity_unit_en\": Unit name like 'spoons', 'grams', 'cloves', or empty if none\n" +
                    "Do not include markdown or conversational text.";

            // 3. SEND TRANSCRIPT TO LOCAL OLLAMA INSTANCE
            System.out.println("[STEP 2] Sending text payload to Llama3.2...");
            String rawLlmResponse = callOllamaLocal("llama3.2:3b", systemInstructions, target.text);
            System.out.println("🔹 Raw LLM Output Captured:\n" + rawLlmResponse);

            // 4. EXTRACT AND PARSE FIELDS VIA JACKSON + INGREDIENT DTO
            System.out.println("\n[STEP 3] Running extraction nodes validation rules...");
            
            JsonNode rootNode = lenientMapper.readTree(rawLlmResponse);
            String nestedContent = "";

            if (rootNode.has("message") && rootNode.get("message").has("content")) {
                nestedContent = rootNode.get("message").get("content").asText().trim();
            } else {
                nestedContent = rawLlmResponse.trim();
            }

            // Stripping Markdown backtick blocks cleanly
            if (nestedContent.contains("```")) {
                if (nestedContent.contains("\"ingredients\"")) {
                    // Squeeze down exactly to the JSON boundaries if extra noise surrounds it
                    nestedContent = nestedContent.substring(nestedContent.indexOf("{"));
                    nestedContent = nestedContent.substring(0, nestedContent.lastIndexOf("}") + 1);
                } else if (nestedContent.contains("```json")) {
                    nestedContent = nestedContent.substring(nestedContent.indexOf("```json") + 7);
                    if (nestedContent.contains("```")) nestedContent = nestedContent.substring(0, nestedContent.indexOf("```"));
                } else {
                    nestedContent = nestedContent.substring(nestedContent.indexOf("```") + 3);
                    if (nestedContent.contains("```")) nestedContent = nestedContent.substring(0, nestedContent.indexOf("```"));
                }
            }
            nestedContent = nestedContent.trim();

            JsonNode ingredientsRoot = lenientMapper.readTree(nestedContent);
            JsonNode ingredientsNode = ingredientsRoot.get("ingredients");
            
            List<IngredientDTO> extractedIngredients = new ArrayList<>();

            if (ingredientsNode != null && ingredientsNode.isArray()) {
                for (JsonNode node : ingredientsNode) {
                    IngredientDTO ing = lenientMapper.convertValue(node, IngredientDTO.class);

                    // 1. Broad Name Target Hunting
                    if (ing.nameOriginal == null || ing.nameOriginal.isBlank()) {
                        if (node.has("ingredient_name_original")) ing.nameOriginal = node.get("ingredient_name_original").asText();
                        else if (node.has("ingredient_name")) ing.nameOriginal = node.get("ingredient_name").asText();
                        else if (node.has("name")) ing.nameOriginal = node.get("name").asText();
                    }
                    if (ing.nameEn == null || ing.nameEn.isBlank()) {
                        if (node.has("ingredient_name_en")) ing.nameEn = node.get("ingredient_name_en").asText();
                    }

                    // 2. Multi-Key Quantity Hunting (Catching raw string fractions/phrases like "secukup rasa")
                    if (ing.rawQuantityValue == null || ((String) ing.rawQuantityValue).isBlank()) {
                        if (node.has("quantity_value")) ing.rawQuantityValue = node.get("quantity_value").asText();
                        else if (node.has("quantity")) ing.rawQuantityValue = node.get("quantity").asText();
                        else if (node.has("amount")) ing.rawQuantityValue = node.get("amount").asText();
                        else if (node.has("value")) ing.rawQuantityValue = node.get("value").asText();
                    }

                    // 3. Multi-Key Unit Hunting (Catching "spoons", "cloves", "g")
                    if (ing.unitEn == null || ing.unitEn.isBlank()) {
                        if (node.has("quantity_unit_en")) ing.unitEn = node.get("quantity_unit_en").asText();
                        else if (node.has("quantity_unit")) ing.unitEn = node.get("quantity_unit").asText();
                        else if (node.has("unit_en")) ing.unitEn = node.get("unit_en").asText();
                        else if (node.has("unit")) ing.unitEn = node.get("unit").asText();
                    }
                    
                    if (ing.unitOriginal == null || ing.unitOriginal.isBlank()) {
                        if (node.has("quantity_unit_original")) ing.unitOriginal = node.get("quantity_unit_original").asText();
                        else if (node.has("unit_original")) ing.unitOriginal = node.get("unit_original").asText();
                        else {
                            // Perfect Fallback: If original unit was left blank by LLM, copy over the populated unitEn text!
                            ing.unitOriginal = ing.unitEn; 
                        }
                    }

                    // 4. Run postProcess to translate string text and set correct numeric primitives
                    ing.postProcess();
                    extractedIngredients.add(ing);
                }
            }

            // 5. PRINT OUT SIMULATED DATABASE PARAMETERS
            System.out.println("\n=== 🎯 FINAL PARSED RESULTS FOR DATABASE INSERT 🎯 ===");
            for (int i = 0; i < extractedIngredients.size(); i++) {
                IngredientDTO item = extractedIngredients.get(i);
                System.out.println(String.format("Ingredient #%d:", i + 1));
                System.out.println("  ↳ name_original  : " + item.nameOriginal);
                System.out.println("  ↳ name_en        : " + item.nameEn);
                System.out.println("  ↳ quantity_value : " + item.quantityValue); 
                System.out.println("  ↳ unit_original  : " + item.unitOriginal);  
                System.out.println("  ↳ unit_en        : " + item.unitEn);
            }

        } catch (Exception e) {
            System.err.println("❌ Pipeline Extraction Execution Failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Helper method making a direct POST connection to your local Ollama instance api endpoint
     */
    /**
     * Helper method making a direct POST connection to your local Ollama instance api endpoint
     */
    private static String callOllamaLocal(String model, String systemPrompt, String userTranscript) throws Exception {
        URL url = new URL("http://localhost:11434/api/chat");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);

        // 1. Build the payload structurally using standard Jackson Objects to guarantee valid JSON formatting
        com.fasterxml.jackson.databind.node.ObjectNode payloadNode = lenientMapper.createObjectNode();
        payloadNode.put("model", model);
        payloadNode.put("stream", false);

        com.fasterxml.jackson.databind.node.ArrayNode messagesArray = lenientMapper.createArrayNode();
        
        // System instruction entry block
        com.fasterxml.jackson.databind.node.ObjectNode systemMessage = lenientMapper.createObjectNode();
        systemMessage.put("role", "system");
        systemMessage.put("content", systemPrompt);
        messagesArray.add(systemMessage);

        // User transcript entry block (safely handles any quotes or weird characters automatically)
        com.fasterxml.jackson.databind.node.ObjectNode userMessage = lenientMapper.createObjectNode();
        userMessage.put("role", "user");
        userMessage.put("content", userTranscript);
        messagesArray.add(userMessage);

        payloadNode.set("messages", messagesArray);

        // 2. Write the safely generated payload string out to the API stream
        String cleanJsonPayload = lenientMapper.writeValueAsString(payloadNode);

        try (OutputStream os = conn.getOutputStream()) {
            byte[] input = cleanJsonPayload.getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }

        // 3. Capture response data stream
        StringBuilder response = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                response.append(line.trim());
            }
        }
        return response.toString();
    }
}