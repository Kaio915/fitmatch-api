package fitmatch_api.controller;

import fitmatch_api.dto.AlimentoDTO;
import fitmatch_api.service.FoodSearchService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/alimentos")
public class AlimentoController {

    private final FoodSearchService foodSearchService;

    public AlimentoController(FoodSearchService foodSearchService) {
        this.foodSearchService = foodSearchService;
    }

    @GetMapping("/buscar")
    public ResponseEntity<List<AlimentoDTO>> buscar(
            @RequestParam(name = "termo", required = false) String termo
    ) {
        if (termo == null || termo.trim().length() < 2) {
            return ResponseEntity.ok(List.of());
        }
        return ResponseEntity.ok(foodSearchService.search(termo));
    }
}
