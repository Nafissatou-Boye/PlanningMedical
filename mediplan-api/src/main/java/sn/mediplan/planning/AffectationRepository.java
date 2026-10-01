package sn.mediplan.planning;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AffectationRepository extends JpaRepository<Affectation, Long> {

    @Query("""
            select a from Affectation a join fetch a.agent join fetch a.creneau c
            where c.planning.id = :planningId""")
    List<Affectation> findByPlanning(Long planningId);

    List<Affectation> findByCreneauId(Long creneauId);

    /** Gardes (tous services confondus) qui chevauchent [debut, fin[. Des gardes qui se suivent ne se chevauchent pas. */
    @Query("""
            select a from Affectation a
              join fetch a.creneau c join fetch c.plageHoraire p join fetch p.sousService ss join fetch ss.service
            where a.agent.id = :agentId and a.debut < :fin and a.fin > :debut""")
    List<Affectation> findChevauchantes(Long agentId, LocalDateTime debut, LocalDateTime fin);

    /** Toutes les gardes qui touchent la période, pour calculer les disponibilités en une requête. */
    @Query("""
            select a from Affectation a
              join fetch a.creneau c join fetch c.plageHoraire p join fetch p.sousService ss join fetch ss.service
            where a.debut < :fin and a.fin > :debut""")
    List<Affectation> findDansPeriode(LocalDateTime debut, LocalDateTime fin);

    @Query("select count(a) from Affectation a where a.creneau.planning.id = :planningId")
    long compterParPlanning(Long planningId);

    @Query("""
            select count(a) > 0 from Affectation a
            where a.agent.id = :agentId and a.creneau.dateGarde between :debut and :fin""")
    boolean existeEntre(Long agentId, LocalDate debut, LocalDate fin);
}
