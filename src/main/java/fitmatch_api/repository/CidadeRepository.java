package fitmatch_api.repository;

import fitmatch_api.model.Cidade;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CidadeRepository extends JpaRepository<Cidade, Long> {

    List<Cidade> findByNomeContainingIgnoreCase(String nome);

    List<Cidade> findByUf(String uf);

    List<Cidade> findByNomeContainingIgnoreCaseAndUf(String nome, String uf);
}
