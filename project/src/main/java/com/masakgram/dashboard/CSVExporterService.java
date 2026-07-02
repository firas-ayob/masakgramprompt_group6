package com.masakgram.dashboard;

import com.masakgram.db.DatabaseManager;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Utility Engine to extract individual or all evaluation layers from the database
 * into standalone CSV output targets for Python analysis.
 */
public class CSVExporterService {

    // Map maintaining the relationship between output files and their corresponding evaluation queries
    private static final Map<String, String> QUERY_MAP = new LinkedHashMap<>();

    static {
        // LAYER 1A : EXACT MATCH (EM)
        QUERY_MAP.put("layer1a_exact_match.csv", 
            "SELECT e.experiment_id, e.transcript_id, r.reel_id_instagram AS video_id, m.model_name, pt.technique_name, e.rag_enabled, " +
            "gti.name_original AS gt_name_original, gti.name_en AS gt_name_en, gti.quantity_unit_culinary AS gt_unit_en, " + 
            "ir.name_original AS pred_name_original, ir.name_en AS pred_name_en, ir.unit_original AS pred_unit_original, ir.unit_en AS pred_unit_en " + 
            "FROM experiment e " + 
            "JOIN transcript t                ON e.transcript_id     = t.transcript_id " + 
            "JOIN reel r                      ON t.reel_id           = r.reel_id " + 
            "JOIN llm_model m                 ON e.model_id          = m.model_id " + 
            "JOIN prompt_technique pt         ON e.technique_id      = pt.technique_id " + 
            "JOIN nutrition_result nr         ON e.experiment_id     = nr.experiment_id " + 
            "JOIN ingredient_result ir        ON nr.result_id        = ir.result_id " + 
            "JOIN ground_truth_reel gtr       ON t.transcript_id     = gtr.transcript_id " + 
            "JOIN ground_truth_ingredient gti ON gtr.gt_reel_id      = gti.gt_reel_id " + 
            "WHERE e.status = 'completed' " + 
            "ORDER BY e.experiment_id, gti.gt_ingredient_id;"
        );

        // LAYER 1B : FUZZY MATCH & BLEU / ROUGE
        QUERY_MAP.put("layer1b_text_similarity.csv", 
            "SELECT e.experiment_id, r.reel_id_instagram AS video_id, m.model_name, pt.technique_name, e.rag_enabled, " + 
            "gti.name_original AS gt_name_original, gti.name_en AS gt_name_en, " + 
            "ir.name_original AS pred_name_original, ir.name_en AS pred_name_en " + 
            "FROM experiment e " + 
            "JOIN transcript t                ON e.transcript_id     = t.transcript_id " + 
            "JOIN reel r                      ON t.reel_id           = r.reel_id " + 
            "JOIN llm_model m                 ON e.model_id          = m.model_id " + 
            "JOIN prompt_technique pt         ON e.technique_id      = pt.technique_id " + 
            "JOIN nutrition_result nr         ON e.experiment_id     = nr.experiment_id " + 
            "JOIN ingredient_result ir        ON nr.result_id        = ir.result_id " + 
            "JOIN ground_truth_reel gtr       ON t.transcript_id     = gtr.transcript_id " + 
            "JOIN ground_truth_ingredient gti ON gtr.gt_reel_id      = gti.gt_reel_id " + 
            "WHERE e.status = 'completed' " + 
            "ORDER BY e.experiment_id, gti.gt_ingredient_id;"
        );

        // LAYER 2A : MAE & MAPE — QUANTITY & WEIGHT
        QUERY_MAP.put("layer2a_numeric_quantity.csv", 
            "SELECT e.experiment_id, r.reel_id_instagram AS video_id, m.model_name, pt.technique_name, e.rag_enabled, " + 
            "gti.quantity_value_culinary AS gt_quantity_value, gti.estimated_weight_g AS gt_weight_g, " + 
            "ir.quantity_value AS pred_quantity_value, ir.estimated_weight_g AS pred_weight_g " + 
            "FROM experiment e " + 
            "JOIN transcript t                ON e.transcript_id     = t.transcript_id " + 
            "JOIN reel r                      ON t.reel_id           = r.reel_id " + 
            "JOIN llm_model m                 ON e.model_id          = m.model_id " + 
            "JOIN prompt_technique pt         ON e.technique_id      = pt.technique_id " + 
            "JOIN nutrition_result nr         ON e.experiment_id     = nr.experiment_id " + 
            "JOIN ingredient_result ir        ON nr.result_id        = ir.result_id " + 
            "JOIN ground_truth_reel gtr       ON t.transcript_id     = gtr.transcript_id " + 
            "JOIN ground_truth_ingredient gti ON gtr.gt_reel_id      = gti.gt_reel_id " + 
            "WHERE e.status = 'completed' AND gti.annotation_layer = 'layer2' " + 
            "ORDER BY e.experiment_id, gti.gt_ingredient_id;"
        );

        // LAYER 2B : MAE, MAPE & PEARSON — NUTRITION VALUES
        QUERY_MAP.put("layer2b_numeric_nutrition.csv", 
            "SELECT e.experiment_id, r.reel_id_instagram AS video_id, m.model_name, pt.technique_name, e.rag_enabled, " + 
            "gti.calories AS gt_calories, gti.protein_g AS gt_protein_g, gti.total_fat_g AS gt_fat_g, gti.total_carbohydrate_g AS gt_carbohydrate_g, " + 
            "ir.calories AS pred_calories, ir.protein_g AS pred_protein_g, ir.total_fat_g AS pred_fat_g, ir.total_carbohydrate_g AS pred_carbohydrate_g " + 
            "FROM experiment e " + 
            "JOIN transcript t                ON e.transcript_id     = t.transcript_id " + 
            "JOIN reel r                      ON t.reel_id           = r.reel_id " + 
            "JOIN llm_model m                 ON e.model_id          = m.model_id " + 
            "JOIN prompt_technique pt         ON e.technique_id      = pt.technique_id " + 
            "JOIN nutrition_result nr         ON e.experiment_id     = nr.experiment_id " + 
            "JOIN ingredient_result ir        ON nr.result_id        = ir.result_id " + 
            "JOIN ground_truth_reel gtr       ON t.transcript_id     = gtr.transcript_id " + 
            "JOIN ground_truth_ingredient gti ON gtr.gt_reel_id      = gti.gt_reel_id " + 
            "WHERE e.status = 'completed' AND gti.annotation_layer = 'layer2' " + 
            "ORDER BY e.experiment_id, gti.gt_ingredient_id;"
        );

        // LAYER 2C : RECIPE-LEVEL NUTRITION TOTALS
        QUERY_MAP.put("layer2c_nutrition_totals.csv", 
            "SELECT e.experiment_id, r.reel_id_instagram AS video_id, m.model_name, pt.technique_name, e.rag_enabled, " + 
            "SUM(gti.calories) AS gt_total_calories, SUM(gti.protein_g) AS gt_total_protein_g, SUM(gti.total_fat_g) AS gt_total_fat_g, SUM(gti.total_carbohydrate_g) AS gt_total_carbohydrate_g, " + 
            "nr.total_calories AS pred_total_calories, nr.total_protein_g AS pred_total_protein_g, nr.total_fat_g AS pred_total_fat_g, nr.total_carbohydrate_g AS pred_total_carbohydrate_g " + 
            "FROM experiment e " + 
            "JOIN transcript t                ON e.transcript_id     = t.transcript_id " + 
            "JOIN reel r                      ON t.reel_id           = r.reel_id " + 
            "JOIN llm_model m                 ON e.model_id          = m.model_id " + 
            "JOIN prompt_technique pt         ON e.technique_id      = pt.technique_id " + 
            "JOIN nutrition_result nr         ON e.experiment_id     = nr.experiment_id " + 
            "JOIN ground_truth_reel gtr       ON t.transcript_id     = gtr.transcript_id " + 
            "JOIN ground_truth_ingredient gti ON gtr.gt_reel_id      = gti.gt_reel_id " + 
            "WHERE e.status = 'completed' AND gti.annotation_layer = 'layer2' " + 
            "GROUP BY e.experiment_id, r.reel_id_instagram, m.model_name, pt.technique_name, e.rag_enabled, " + 
            "nr.total_calories, nr.total_protein_g, nr.total_fat_g, nr.total_carbohydrate_g " + 
            "ORDER BY e.experiment_id;"
        );

        // LAYER 3A : JSON VALIDITY RATE
        QUERY_MAP.put("layer3a_json_validity.csv", 
            "SELECT m.model_name, pt.technique_name, e.rag_enabled, COUNT(*) AS total_runs, " + 
            "SUM(CASE WHEN nr.json_valid = TRUE THEN 1 ELSE 0 END) AS valid_count, " + 
            "SUM(CASE WHEN nr.json_valid = FALSE THEN 1 ELSE 0 END) AS invalid_count, " + 
            "ROUND(SUM(CASE WHEN nr.json_valid = TRUE THEN 1 ELSE 0 END) * 100.0 / COUNT(*), 2) AS validity_rate_pct " + 
            "FROM experiment e " + 
            "JOIN llm_model m           ON e.model_id      = m.model_id " + 
            "JOIN prompt_technique pt   ON e.technique_id  = pt.technique_id " + 
            "JOIN nutrition_result nr   ON e.experiment_id = nr.experiment_id " + 
            "WHERE e.status = 'completed' " + 
            "GROUP BY m.model_name, pt.technique_name, e.rag_enabled " + 
            "ORDER BY m.model_name, pt.technique_name;"
        );

        // LAYER 3B : HALLUCINATION RATE (Calculated dynamically because ir.is_hallucinated is missing from schema)
        QUERY_MAP.put("layer3b_hallucination.csv", 
            "SELECT e.experiment_id, r.reel_id_instagram AS video_id, m.model_name, pt.technique_name, e.rag_enabled, " + 
            "ir.name_original AS pred_name_original, ir.name_en AS pred_name_en, " + 
            "CASE WHEN gti.gt_ingredient_id IS NULL THEN 1 ELSE 0 END AS is_hallucinated " + 
            "FROM experiment e " + 
            "JOIN transcript t                ON e.transcript_id  = t.transcript_id " + 
            "JOIN reel r                      ON t.reel_id        = r.reel_id " + 
            "JOIN llm_model m                 ON e.model_id       = m.model_id " + 
            "JOIN prompt_technique pt         ON e.technique_id   = pt.technique_id " + 
            "JOIN nutrition_result nr         ON e.experiment_id  = nr.experiment_id " + 
            "JOIN ingredient_result ir        ON nr.result_id     = ir.result_id " + 
            "JOIN ground_truth_reel gtr       ON t.transcript_id  = gtr.transcript_id " + 
            "LEFT JOIN ground_truth_ingredient gti " +
            "  ON gtr.gt_reel_id = gti.gt_reel_id " + 
            "  AND (LOWER(ir.name_en) = LOWER(gti.name_en) OR LOWER(ir.name_original) = LOWER(gti.name_original)) " + 
            "WHERE e.status = 'completed' " + 
            "ORDER BY e.experiment_id, ir.ingredient_id;"
        );

        // LAYER 3C : INGREDIENT PRECISION, RECALL & F1
        QUERY_MAP.put("layer3c_ingredient_detection.csv", 
            "SELECT e.experiment_id, r.reel_id_instagram AS video_id, m.model_name, pt.technique_name, e.rag_enabled, " + 
            "COUNT(DISTINCT gti.gt_ingredient_id) AS gt_ingredient_count, " +
            "COUNT(DISTINCT ir.ingredient_id) AS pred_ingredient_count, " + 
            "SUM(CASE WHEN gti.gt_ingredient_id IS NOT NULL THEN 1 ELSE 0 END) AS true_positives, " + 
            "SUM(CASE WHEN gti.gt_ingredient_id IS NULL THEN 1 ELSE 0 END) AS false_positives " + 
            "FROM experiment e " + 
            "JOIN transcript t                ON e.transcript_id     = t.transcript_id " + 
            "JOIN reel r                      ON t.reel_id           = r.reel_id " + 
            "JOIN llm_model m                 ON e.model_id          = m.model_id " + 
            "JOIN prompt_technique pt         ON e.technique_id      = pt.technique_id " + 
            "JOIN nutrition_result nr         ON e.experiment_id     = nr.experiment_id " + 
            "JOIN ingredient_result ir        ON nr.result_id        = ir.result_id " + 
            "JOIN ground_truth_reel gtr       ON t.transcript_id     = gtr.transcript_id " + 
            "LEFT JOIN ground_truth_ingredient gti " +
            "  ON gtr.gt_reel_id = gti.gt_reel_id " + 
            "  AND (LOWER(ir.name_en) = LOWER(gti.name_en) OR LOWER(ir.name_original) = LOWER(gti.name_original)) " + 
            "WHERE e.status = 'completed' " + 
            "GROUP BY e.experiment_id, r.reel_id_instagram, m.model_name, pt.technique_name, e.rag_enabled " + 
            "ORDER BY e.experiment_id;"
        );

        // LAYER 4 : HUMAN EVALUATION — LIKERT & KRIPPENDORFF
        QUERY_MAP.put("layer4_human_evaluation.csv", 
            "SELECT he.evaluation_id, he.result_id, e.experiment_id, r.reel_id_instagram AS video_id, m.model_name, pt.technique_name, " + 
            "he.annotator_id, he.fluency_score, he.completeness_score, he.plausibility_score, he.evaluated_at " + 
            "FROM human_evaluation he " + 
            "JOIN nutrition_result nr   ON he.result_id     = nr.result_id " + 
            "JOIN experiment e          ON nr.experiment_id = e.experiment_id " + 
            "JOIN transcript t          ON e.transcript_id  = t.transcript_id " + 
            "JOIN reel r                ON t.reel_id        = r.reel_id " + 
            "JOIN llm_model m           ON e.model_id       = m.model_id " + 
            "JOIN prompt_technique pt   ON e.technique_id  = pt.technique_id " + 
            "ORDER BY he.result_id, he.annotator_id;"
        );

        // LAYER 5 : STATISTICAL SIGNIFICANCE — FRIEDMAN & WILCOXON
        QUERY_MAP.put("layer5_condition_scores.csv", 
            "SELECT r.reel_id_instagram AS video_id, m.model_name, pt.technique_name, e.rag_enabled, " + 
            "COUNT(DISTINCT ir.ingredient_id) AS pred_count, " + 
            "SUM(CASE WHEN gti.gt_ingredient_id IS NOT NULL THEN 1 ELSE 0 END) AS true_positives, " + 
            "SUM(CASE WHEN gti.gt_ingredient_id IS NULL THEN 1 ELSE 0 END) AS false_positives, " + 
            "COUNT(DISTINCT gti.gt_ingredient_id) AS gt_count, nr.json_valid, " + 
            "nr.total_calories AS pred_total_kcal, SUM(gti.calories) AS gt_total_kcal " + 
            "FROM experiment e " + 
            "JOIN transcript t                ON e.transcript_id     = t.transcript_id " + 
            "JOIN reel r                      ON t.reel_id           = r.reel_id " + 
            "JOIN llm_model m                 ON e.model_id          = m.model_id " + 
            "JOIN prompt_technique pt         ON e.technique_id      = pt.technique_id " + 
            "JOIN nutrition_result nr         ON e.experiment_id     = nr.experiment_id " + 
            "JOIN ingredient_result ir        ON nr.result_id        = ir.result_id " + 
            "JOIN ground_truth_reel gtr       ON t.transcript_id     = gtr.transcript_id " + 
            "LEFT JOIN ground_truth_ingredient gti " + 
            "  ON gtr.gt_reel_id = gti.gt_reel_id AND gti.annotation_layer = 'layer2' " + 
            "WHERE e.status = 'completed' " + 
            "GROUP BY r.reel_id_instagram, m.model_name, pt.technique_name, e.rag_enabled, nr.json_valid, nr.total_calories " + 
            "ORDER BY r.reel_id_instagram, m.model_name, pt.technique_name;"
        );
    }

    public static List<String> getAvailableLayers() {
        return new ArrayList<>(QUERY_MAP.keySet());
    }

    public static void exportSingleLayer(String layerFileName, File targetDirectory) throws Exception {
        if (!QUERY_MAP.containsKey(layerFileName)) {
            throw new IllegalArgumentException("Unknown evaluation layer: " + layerFileName);
        }
        if (!targetDirectory.exists()) {
            targetDirectory.mkdirs();
        }

        String sqlQuery = QUERY_MAP.get(layerFileName);
        File csvOutputTarget = new File(targetDirectory, layerFileName);

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sqlQuery);
             ResultSet rs = pstmt.executeQuery();
             BufferedWriter writer = new BufferedWriter(new FileWriter(csvOutputTarget))) {

            ResultSetMetaData metaData = rs.getMetaData();
            int columnCount = metaData.getColumnCount();

            // Write headers
            for (int i = 1; i <= columnCount; i++) {
                writer.write(metaData.getColumnLabel(i));
                if (i < columnCount) writer.write(",");
            }
            writer.newLine();

            // Write Data
            while (rs.next()) {
                for (int i = 1; i <= columnCount; i++) {
                    Object cellData = rs.getObject(i);
                    writer.write(sanitizeCsvCell(cellData));
                    if (i < columnCount) writer.write(",");
                }
                writer.newLine();
            }
        }
    }

    public static List<String> exportAllLayers(File targetDirectory) throws Exception {
        List<String> successfullyExported = new ArrayList<>();
        for (String layerFile : QUERY_MAP.keySet()) {
            try {
                exportSingleLayer(layerFile, targetDirectory);
                successfullyExported.add(layerFile);
            } catch (Exception e) {
                System.err.println("Skipping " + layerFile + ": " + e.getMessage());
            }
        }
        return successfullyExported;
    }

    private static String sanitizeCsvCell(Object value) {
        if (value == null) return "";
        String str = value.toString().trim();
        str = str.replace("\"", "\"\"");
        if (str.contains(",") || str.contains("\"") || str.contains("\n") || str.contains("\r")) {
            return "\"" + str + "\"";
        }
        return str;
    }
}