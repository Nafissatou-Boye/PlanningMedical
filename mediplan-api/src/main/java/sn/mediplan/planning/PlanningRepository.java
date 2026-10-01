package sn.mediplan.planning;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanningRepository extends JpaRepository<Planning, Long> {

    Optional<Planning> findByServiceIdAndTypeAndAnneeAndMois(Long serviceId, TypePlanning type, int annee, int mois);
}
