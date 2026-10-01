package sn.mediplan.diffusion;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface EnvoiPdfRepository extends JpaRepository<EnvoiPdf, Long> {

    @Query("select e from EnvoiPdf e left join fetch e.agent where e.planning.id = :planningId order by e.dateEnvoi desc")
    List<EnvoiPdf> findByPlanning(Long planningId);

    /** Envois en échec depuis une date (pour les notifications). */
    @Query("""
            select e from EnvoiPdf e left join fetch e.agent join fetch e.planning p join fetch p.service
            where e.statut = :statut and e.dateEnvoi >= :depuis order by e.dateEnvoi desc""")
    List<EnvoiPdf> findParStatutDepuis(StatutEnvoi statut, java.time.LocalDateTime depuis);
}
