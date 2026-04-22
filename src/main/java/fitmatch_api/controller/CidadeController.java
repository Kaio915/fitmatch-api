package fitmatch_api.controller;

import fitmatch_api.model.Cidade;
import fitmatch_api.service.CidadeService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
@RestController
@RequestMapping("/cidades")
public class CidadeController {

    private final CidadeService cidadeService;

    public CidadeController(CidadeService cidadeService) {
        this.cidadeService = cidadeService;
    }

    @PostMapping("/importar")
    public String importar() {
        cidadeService.importarCidades();
        return "Cidades importadas com sucesso!";
    }

    @GetMapping
    public List<Cidade> buscar(
            @RequestParam(required = false) String nome,
            @RequestParam(required = false) String uf
    ) {
        return cidadeService.buscar(nome, uf);
    }
}
