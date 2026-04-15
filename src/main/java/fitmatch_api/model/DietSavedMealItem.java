package fitmatch_api.model;

import jakarta.persistence.*;

@Entity
@Table(name = "diet_saved_meal_items")
public class DietSavedMealItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "saved_meal_id", nullable = false)
    private DietSavedMeal savedMeal;

    @Column
    private Long foodId;

    @Column(nullable = false)
    private String foodName;

    @Column(nullable = false)
    private Double quantityGrams;

    @Column(nullable = false)
    private Double calories;

    @Column(nullable = false)
    private Double protein;

    @Column(nullable = false)
    private Double carbs;

    @Column(nullable = false)
    private Double fat;

    @Column(nullable = false)
    private Integer sortOrder;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public DietSavedMeal getSavedMeal() {
        return savedMeal;
    }

    public void setSavedMeal(DietSavedMeal savedMeal) {
        this.savedMeal = savedMeal;
    }

    public Long getFoodId() {
        return foodId;
    }

    public void setFoodId(Long foodId) {
        this.foodId = foodId;
    }

    public String getFoodName() {
        return foodName;
    }

    public void setFoodName(String foodName) {
        this.foodName = foodName;
    }

    public Double getQuantityGrams() {
        return quantityGrams;
    }

    public void setQuantityGrams(Double quantityGrams) {
        this.quantityGrams = quantityGrams;
    }

    public Double getCalories() {
        return calories;
    }

    public void setCalories(Double calories) {
        this.calories = calories;
    }

    public Double getProtein() {
        return protein;
    }

    public void setProtein(Double protein) {
        this.protein = protein;
    }

    public Double getCarbs() {
        return carbs;
    }

    public void setCarbs(Double carbs) {
        this.carbs = carbs;
    }

    public Double getFat() {
        return fat;
    }

    public void setFat(Double fat) {
        this.fat = fat;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }
}
