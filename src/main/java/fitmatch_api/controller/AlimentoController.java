package fitmatch_api.controller;

import fitmatch_api.dto.AlimentoDTO;
import fitmatch_api.service.FatSecretService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/alimentos")
public class AlimentoController {

    private final FatSecretService fatSecretService;

    public AlimentoController(FatSecretService fatSecretService) {
        this.fatSecretService = fatSecretService;
    }

    @GetMapping("/buscar")
    public ResponseEntity<List<AlimentoDTO>> buscar(
            @RequestParam(name = "termo", required = false) String termo
    ) {
        if (termo == null || termo.trim().length() < 2) {
            return ResponseEntity.ok(List.of());
        }
        return ResponseEntity.ok(fatSecretService.searchFoods(termo));
    }
}
