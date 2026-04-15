package fitmatch_api.service;

import fitmatch_api.model.Cidade;
import fitmatch_api.repository.CidadeRepository;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Service
public class CidadeService {

    private final CidadeRepository cidadeRepository;

    public CidadeService(CidadeRepository cidadeRepository) {
        this.cidadeRepository = cidadeRepository;
    }

    public void importarCidades() {

        RestTemplate restTemplate = new RestTemplate();
        String url = "https://servicodados.ibge.gov.br/api/v1/localidades/municipios";

        ResponseEntity<List<Map<String, Object>>> response =
                restTemplate.exchange(
                        url,
                        HttpMethod.GET,
                        null,
                        new ParameterizedTypeReference<List<Map<String, Object>>>() {}
                );

        List<Map<String, Object>> cidades = response.getBody();

        if (cidades == null) return;

        for (Map<String, Object> item : cidades) {

            if (item == null) continue;

            Object idObj = item.get("id");
            Object nomeObj = item.get("nome");
            Object microObj = item.get("microrregiao");

            if (idObj == null || nomeObj == null || microObj == null) {
                continue;
            }

            Map<?, ?> microrregiao = (Map<?, ?>) microObj;

            Object mesoObj = microrregiao.get("mesorregiao");
            if (mesoObj == null) continue;

            Map<?, ?> mesorregiao = (Map<?, ?>) mesoObj;

            Object ufObj = mesorregiao.get("UF");
            if (ufObj == null) continue;

            Map<?, ?> ufMap = (Map<?, ?>) ufObj;

            Object siglaObj = ufMap.get("sigla");
            if (siglaObj == null) continue;

            Long id = Long.valueOf(idObj.toString());
            String nome = nomeObj.toString();
            String uf = siglaObj.toString();

            if (!cidadeRepository.existsById(id)) {
                Cidade cidade = new Cidade(id, nome, uf);
                cidadeRepository.save(cidade);
            }
        }
    }

    public List<Cidade> buscar(String nome, String uf) {

        if (nome != null && uf != null) {
            return cidadeRepository.findByNomeContainingIgnoreCaseAndUf(nome, uf);
        }

        if (nome != null) {
            return cidadeRepository.findByNomeContainingIgnoreCase(nome);
        }

        if (uf != null) {
            return cidadeRepository.findByUf(uf);
        }

        return cidadeRepository.findAll();
    }
}
