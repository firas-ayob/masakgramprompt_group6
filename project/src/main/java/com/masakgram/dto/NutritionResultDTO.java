package com.masakgram.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonAlias;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class NutritionResultDTO {

    @JsonProperty("recipe_name")
    @JsonAlias({"recipeName", "nama_resepi", "name", "title", "dish_name"})
    public String recipeName;

    @JsonProperty("servings_estimated")
    @JsonAlias({"servings", "serving_size", "servings_estimate", "portion"})
    public Double servingsEstimated = 1.0;

    // ✅ PIPELINE MATCH: Changed to 'Double' to allow database NULLs
    // Per Serving Data Fields
    public Double sCalories, sFat, sSaturatedFat, sCholesterol, sSodium, sCarbohydrates, sDietaryFiber, sTotalSugars, sProtein, sVitaminD, sCalcium, sIron, sPotassium;

    // Total Recipe Data Fields
    public Double tCalories, tFat, tSaturatedFat, tCholesterol, tSodium, tCarbohydrates, tDietaryFiber, tTotalSugars, tProtein, tVitaminD, tCalcium, tIron, tPotassium;

    @JsonProperty("ingredients")
    @JsonAlias({"ingredient_list", "components", "items"})
    public List<IngredientDTO> ingredients;

    @JsonProperty("amount_per_serving")
    @JsonAlias({"per_serving", "serving_nutrition", "per_portion"})
    private void unpackServing(Map<String, Object> map) {
        if (map == null) return;
        this.sCalories = convert(map.get("calories"));
        this.sFat = convert(map.get("total_fat_g"));
        this.sSaturatedFat = convert(map.get("saturated_fat_g"));
        this.sCholesterol = convert(map.get("cholesterol_mg"));
        this.sSodium = convert(map.get("sodium_mg"));
        this.sCarbohydrates = convert(map.get("total_carbohydrate_g"));
        this.sDietaryFiber = convert(map.get("dietary_fiber_g"));
        this.sTotalSugars = convert(map.get("total_sugars_g"));
        this.sProtein = convert(map.get("protein_g"));
        this.sVitaminD = convert(map.get("vitamin_d_mcg"));
        this.sCalcium = convert(map.get("calcium_mg"));
        this.sIron = convert(map.get("iron_mg"));
        this.sPotassium = convert(map.get("potassium_mg"));
    }

    @JsonProperty("nutrition_total")
    @JsonAlias({"total_nutrition", "full_recipe", "recipe_total"})
    private void unpackTotal(Map<String, Object> map) {
        if (map == null) return;
        this.tCalories = convert(map.get("calories"));
        this.tFat = convert(map.get("total_fat_g"));
        this.tSaturatedFat = convert(map.get("saturated_fat_g"));
        this.tCholesterol = convert(map.get("cholesterol_mg"));
        this.tSodium = convert(map.get("sodium_mg"));
        this.tCarbohydrates = convert(map.get("total_carbohydrate_g"));
        this.tDietaryFiber = convert(map.get("dietary_fiber_g"));
        this.tTotalSugars = convert(map.get("total_sugars_g"));
        this.tProtein = convert(map.get("protein_g"));
        this.tVitaminD = convert(map.get("vitamin_d_mcg"));
        this.tCalcium = convert(map.get("calcium_mg"));
        this.tIron = convert(map.get("iron_mg"));
        this.tPotassium = convert(map.get("potassium_mg"));
    }

    /**
     * ✅ FALLBACK METHOD: Because your prompt files do not ask the LLM for 
     * "nutrition_total", this calculates it automatically in Java so your 
     * database columns are completely filled!
     */
    public void calculateTotals() {
        double servings = (this.servingsEstimated != null && this.servingsEstimated > 0) ? this.servingsEstimated : 1.0;

        // Sum everything directly from the ingredients list for highest accuracy
        if (this.ingredients != null && !this.ingredients.isEmpty()) {
            this.tCalories = 0.0; this.tFat = 0.0; this.tSaturatedFat = 0.0;
            this.tCholesterol = 0.0; this.tSodium = 0.0; this.tCarbohydrates = 0.0;
            this.tDietaryFiber = 0.0; this.tTotalSugars = 0.0; this.tProtein = 0.0;
            this.tVitaminD = 0.0; this.tCalcium = 0.0; this.tIron = 0.0; this.tPotassium = 0.0;

            for (IngredientDTO ing : this.ingredients) {
                if (ing.calories != null) this.tCalories += ing.calories;
                if (ing.totalFatG != null) this.tFat += ing.totalFatG;
                if (ing.saturatedFatG != null) this.tSaturatedFat += ing.saturatedFatG;
                if (ing.cholesterolMg != null) this.tCholesterol += ing.cholesterolMg;
                if (ing.sodiumMg != null) this.tSodium += ing.sodiumMg;
                if (ing.totalCarbohydrateG != null) this.tCarbohydrates += ing.totalCarbohydrateG;
                if (ing.dietaryFiberG != null) this.tDietaryFiber += ing.dietaryFiberG;
                if (ing.totalSugarsG != null) this.tTotalSugars += ing.totalSugarsG;
                if (ing.proteinG != null) this.tProtein += ing.proteinG;
                if (ing.vitaminDMcg != null) this.tVitaminD += ing.vitaminDMcg;
                if (ing.calciumMg != null) this.tCalcium += ing.calciumMg;
                if (ing.ironMg != null) this.tIron += ing.ironMg;
                if (ing.potassiumMg != null) this.tPotassium += ing.potassiumMg;
            }
        } 
        // Fallback: If no ingredients exist, multiply per-serving values by the serving count
        else {
            this.tCalories = (sCalories != null) ? sCalories * servings : null;
            this.tFat = (sFat != null) ? sFat * servings : null;
            this.tSaturatedFat = (sSaturatedFat != null) ? sSaturatedFat * servings : null;
            this.tCholesterol = (sCholesterol != null) ? sCholesterol * servings : null;
            this.tSodium = (sSodium != null) ? sSodium * servings : null;
            this.tCarbohydrates = (sCarbohydrates != null) ? sCarbohydrates * servings : null;
            this.tDietaryFiber = (sDietaryFiber != null) ? sDietaryFiber * servings : null;
            this.tTotalSugars = (sTotalSugars != null) ? sTotalSugars * servings : null;
            this.tProtein = (sProtein != null) ? sProtein * servings : null;
            this.tVitaminD = (sVitaminD != null) ? sVitaminD * servings : null;
            this.tCalcium = (sCalcium != null) ? sCalcium * servings : null;
            this.tIron = (sIron != null) ? sIron * servings : null;
            this.tPotassium = (sPotassium != null) ? sPotassium * servings : null;
        }
    }

    // ✅ PIPELINE MATCH: Return Double to allow null values
    private Double convert(Object obj) {
        if (obj == null) return null;
        if (obj instanceof Number) return ((Number) obj).doubleValue();
        try { 
            String str = obj.toString().trim();
            return str.isEmpty() ? null : Double.parseDouble(str); 
        } catch (Exception e) { 
            return null; 
        }
    }
}