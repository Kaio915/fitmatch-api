package fitmatch_api.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Alimento da Tabela TACO (Tabela Brasileira de Composição de Alimentos).
 * Cada registro representa a composição nutricional por 100 g do alimento
 * (calorias, proteína, carboidrato e lipídeos).
 */
@Entity
@Table(
        name = "taco_foods",
        uniqueConstraints = @UniqueConstraint(columnNames = {"description"})
)
public class TacoFood {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Integer tacoId;

    @Column(nullable = false, length = 500)
    private String description;

    @Column(nullable = false)
    private Double baseQty;

    @Column(nullable = false)
    private Double calories;

    @Column(nullable = false)
    private Double protein;

    @Column(nullable = false)
    private Double carbs;

    @Column(nullable = false)
    private Double lipids;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Integer getTacoId() {
        return tacoId;
    }

    public void setTacoId(Integer tacoId) {
        this.tacoId = tacoId;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Double getBaseQty() {
        return baseQty;
    }

    public void setBaseQty(Double baseQty) {
        this.baseQty = baseQty;
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

    public Double getLipids() {
        return lipids;
    }

    public void setLipids(Double lipids) {
        this.lipids = lipids;
    }
}
