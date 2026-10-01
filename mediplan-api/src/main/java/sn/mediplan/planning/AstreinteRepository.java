package sn.mediplan.planning;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AstreinteRepository extends JpaRepository<Astreinte, Long> {

    @Query("select a from Astreinte a join fetch a.agent where a.planning.id = :planningId order by a.dateDebut")
    List<Astreinte> findByPlanning(Long planningId);

    Optional<Astreinte> findByPlanningIdAndDateDebut(Long planningId, LocalDate dateDebut);

    /** Astreintes d'un agent qui touchent la période (tous services). */
    @Query("select a from Astreinte a where a.agent.id = :agentId and a.dateDebut <= :fin and a.dateFin >= :debut")
    List<Astreinte> findChevauchantes(Long agentId, LocalDate debut, LocalDate fin);
}
