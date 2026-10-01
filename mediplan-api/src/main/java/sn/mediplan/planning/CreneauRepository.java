package sn.mediplan.planning;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CreneauRepository extends JpaRepository<Creneau, Long> {

    @Query("select c from Creneau c join fetch c.plageHoraire where c.planning.id = :planningId")
    List<Creneau> findByPlanning(Long planningId);

    Optional<Creneau> findByPlageHoraireIdAndDateGarde(Long plageHoraireId, LocalDate dateGarde);
}
