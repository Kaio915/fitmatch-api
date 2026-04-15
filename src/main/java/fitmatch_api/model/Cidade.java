package fitmatch_api.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "cidades")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Cidade {

    @Id
    private Long idIbge;

    @Column(nullable = false)
    private String nome;

    @Column(nullable = false, length = 2)
    private String uf;
}
