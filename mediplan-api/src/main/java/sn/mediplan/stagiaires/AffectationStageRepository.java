package sn.mediplan.stagiaires;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AffectationStageRepository extends JpaRepository<AffectationStage, Long> {

    @Query("""
            select a from AffectationStage a join fetch a.agent join fetch a.poste join fetch a.periode p
            where p.planning.id = :planningId""")
    List<AffectationStage> findByPlanning(Long planningId);

    @Query("""
            select a from AffectationStage a join fetch a.agent join fetch a.poste
            where a.periode.id = :periodeId""")
    List<AffectationStage> findByPeriode(Long periodeId);

    /**
     * Postes tenus par des stagiaires sur des semaines qui touchent [debut, fin], tous plannings confondus
     * (une semaine à cheval sur deux mois peut figurer dans deux plannings).
     */
    @Query("""
            select a from AffectationStage a join fetch a.agent join fetch a.poste join fetch a.periode p
            where p.dateDebut <= :fin and p.dateFin >= :debut""")
    List<AffectationStage> findDansPeriode(LocalDate debut, LocalDate fin);
}
