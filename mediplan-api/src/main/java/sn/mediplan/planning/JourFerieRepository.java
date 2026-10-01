package sn.mediplan.planning;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface JourFerieRepository extends JpaRepository<JourFerie, Long> {

    List<JourFerie> findByDateFerieBetween(LocalDate debut, LocalDate fin);

    List<JourFerie> findByDateFerieBetweenOrderByDateFerieAsc(LocalDate debut, LocalDate fin);

    boolean existsByDateFerie(LocalDate date);

    boolean existsByDateFerieAndIdNot(LocalDate date, Long id);
}
