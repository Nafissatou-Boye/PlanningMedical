package sn.mediplan.stagiaires;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PeriodeStageRepository extends JpaRepository<PeriodeStage, Long> {

    List<PeriodeStage> findByPlanningIdOrderByDateDebutAsc(Long planningId);
}
