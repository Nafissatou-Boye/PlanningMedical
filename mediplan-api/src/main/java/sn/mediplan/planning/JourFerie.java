package sn.mediplan.planning;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "jour_ferie")
@Getter
@Setter
@NoArgsConstructor
public class JourFerie {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "date_ferie", nullable = false)
    private LocalDate dateFerie;

    @Column(nullable = false, length = 100)
    private String libelle;
}
