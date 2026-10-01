package sn.mediplan.conges;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CongeRepository extends JpaRepository<Conge, Long> {

    /** Congés qui touchent la période [debut, fin] (même partiellement). */
    @Query("""
            select c from Conge c join fetch c.agent
            where c.dateDebut <= :fin and c.dateFin >= :debut
            order by c.dateDebut, c.agent.nom""")
    List<Conge> findChevauchant(LocalDate debut, LocalDate fin);

    @Query("select c from Conge c join fetch c.agent order by c.dateDebut desc")
    List<Conge> findTousRecentsDabord();

    /** Un autre congé du même agent chevauche-t-il ces dates ? */
    @Query("""
            select count(c) > 0 from Conge c
            where c.agent.id = :agentId and c.dateDebut <= :fin and c.dateFin >= :debut
              and (:exclureId is null or c.id <> :exclureId)""")
    boolean existeChevauchement(Long agentId, LocalDate debut, LocalDate fin, Long exclureId);
}
