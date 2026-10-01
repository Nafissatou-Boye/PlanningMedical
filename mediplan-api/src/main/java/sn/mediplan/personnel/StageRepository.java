package sn.mediplan.personnel;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface StageRepository extends JpaRepository<Stage, Long> {

    @Query("select s from Stage s join fetch s.service order by s.dateDebut desc")
    List<Stage> findAllRecentsDabord();

    List<Stage> findByAgentIdOrderByDateDebutDesc(Long agentId);

    /** Stages qui touchent la période (pour savoir si un stagiaire peut être proposé). */
    @Query("select s from Stage s where s.dateDebut <= :fin and s.dateFin >= :debut")
    List<Stage> findChevauchant(java.time.LocalDate debut, java.time.LocalDate fin);
}
