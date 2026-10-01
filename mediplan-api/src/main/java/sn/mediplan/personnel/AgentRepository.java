package sn.mediplan.personnel;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AgentRepository extends JpaRepository<Agent, Long> {

    /** Tous les agents avec profession et services (évite une requête par agent). */
    @EntityGraph(attributePaths = { "profession", "services" })
    @Query("select a from Agent a order by a.nom, a.prenom")
    List<Agent> findAllComplets();

    /** Agents actifs rattachés au service, candidats aux gardes. */
    @EntityGraph(attributePaths = { "profession" })
    @Query("select distinct a from Agent a join a.services s where s.id = :serviceId and a.actif = true order by a.nom, a.prenom")
    List<Agent> findActifsDuService(Long serviceId);

    boolean existsByMatriculeIgnoreCase(String matricule);

    boolean existsByMatriculeIgnoreCaseAndIdNot(String matricule, Long id);
}
