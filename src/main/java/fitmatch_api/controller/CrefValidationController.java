package fitmatch_api.controller;

import fitmatch_api.service.CrefValidationService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/cref")
public class CrefValidationController {

    private final CrefValidationService crefValidationService;

    public CrefValidationController(CrefValidationService crefValidationService) {
        this.crefValidationService = crefValidationService;
    }

    @GetMapping("/validate")
    public CrefValidationService.CrefValidationResult validateGet(
            @RequestParam String cref,
            @RequestParam(required = false) String uf
    ) {
        return crefValidationService.validate(cref, uf);
    }

    @PostMapping("/validate")
    public CrefValidationService.CrefValidationResult validatePost(
            @RequestBody CrefValidationRequest request
    ) {
        String cref = request == null ? null : request.cref();
        String uf = request == null ? null : request.uf();
        return crefValidationService.validate(cref, uf);
    }

    public record CrefValidationRequest(String cref, String uf) {
    }
}
