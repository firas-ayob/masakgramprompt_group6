package com.masakgram.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonAlias;

@JsonIgnoreProperties(ignoreUnknown = true)
public class IngredientDTO {

    @JsonProperty("name_original")
    @JsonAlias({"name_raw", "nama_asal", "nama_original", "ingredient_name_original", "ingredient_name", "ingredient", "name"})
    public String nameOriginal;

    @JsonProperty("name_en")
    @JsonAlias({"name_english", "nama_english", "nama_en", "english_name", "ingredient_name_en", "name_english_translation"})
    public String nameEn;
    
    @JsonProperty("quantity_value")
    @JsonAlias({"kuantiti", "amount", "quantity", "qty", "value"})
    public Object rawQuantityValue;
    
    public Double quantityValue = 1.0;

    @JsonProperty("unit_original")
    @JsonAlias({"quantity_unit_original", "unit_asal", "unit", "measurement", "unit_raw"})
    public String unitOriginal = "g";

    @JsonProperty("unit_en")
    @JsonAlias({"quantity_unit_en", "unit_english", "english_unit", "unit_translated"})
    public String unitEn = "g";

    @JsonProperty("estimated_weight_g")
    @JsonAlias({"estimated_weight", "weight_g", "weight_grams", "est_weight"})
    public Object rawEstimatedWeightG;
    
    public Double estimatedWeightG = 100.0;
    
    // ✅ PIPELINE MATCH: Changed to 'Double' to allow database NULLs.
    // ✅ PIPELINE MATCH: Suffixes (G, Mg, Mcg) added so PipelineEngine can read them.
    @JsonProperty("calories") public Double calories;
    @JsonProperty("total_fat_g") public Double totalFatG;
    @JsonProperty("saturated_fat_g") public Double saturatedFatG;
    @JsonProperty("cholesterol_mg") public Double cholesterolMg;
    @JsonProperty("sodium_mg") public Double sodiumMg;
    @JsonProperty("total_carbohydrate_g") public Double totalCarbohydrateG;
    @JsonProperty("dietary_fiber_g") public Double dietaryFiberG;
    @JsonProperty("total_sugars_g") public Double totalSugarsG;
    @JsonProperty("protein_g") public Double proteinG;
    @JsonProperty("vitamin_d_mcg") public Double vitaminDMcg;
    @JsonProperty("calcium_mg") public Double calciumMg;
    @JsonProperty("iron_mg") public Double ironMg;
    @JsonProperty("potassium_mg") public Double potassiumMg;

    public IngredientDTO() {}

    public void postProcess() {
        // Handle quantity value
        if (rawQuantityValue != null) {
            if (rawQuantityValue instanceof Number) {
                quantityValue = ((Number) rawQuantityValue).doubleValue();
            } else {
                String strVal = rawQuantityValue.toString().trim();
                try {
                    quantityValue = Double.parseDouble(strVal);
                } catch (NumberFormatException e) {
                    quantityValue = 1.0;
                    if (!strVal.isBlank()) {
                        unitOriginal = strVal;
                        unitEn = strVal;
                    }
                }
            }
        }

        if (quantityValue == null || quantityValue <= 0) {
            quantityValue = 1.0;
        }

        // Handle estimated_weight_g (accepts String like "28g" or "200")
        if (rawEstimatedWeightG != null) {
            if (rawEstimatedWeightG instanceof Number) {
                estimatedWeightG = ((Number) rawEstimatedWeightG).doubleValue();
            } else {
                String strVal = rawEstimatedWeightG.toString().trim();
                // Remove non-numeric characters (g, kg, ml, etc.)
                String numericStr = strVal.replaceAll("[^0-9.]+", "");
                try {
                    if (!numericStr.isEmpty()) {
                        estimatedWeightG = Double.parseDouble(numericStr);
                    } else {
                        estimatedWeightG = quantityValue * 100;
                    }
                } catch (NumberFormatException e) {
                    estimatedWeightG = quantityValue * 100;
                }
            }
        }

        if (estimatedWeightG == null || estimatedWeightG <= 0) {
            estimatedWeightG = quantityValue * 100;
        }

        // Handle units
        if (unitOriginal == null || unitOriginal.isBlank()) {
            unitOriginal = "g";
        }
        if (unitEn == null || unitEn.isBlank()) {
            unitEn = unitOriginal;
        }
    }
}