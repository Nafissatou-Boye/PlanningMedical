package sn.mediplan.parametrage;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PosteStageRepository extends JpaRepository<PosteStage, Long> {

    @Query("select p from PosteStage p order by p.ordreAffichage, p.id")
    List<PosteStage> findAllOrdonnes();

    boolean existsByServiceIdAndLibelleIgnoreCase(Long serviceId, String libelle);

    @Query("select coalesce(max(p.ordreAffichage), 0) from PosteStage p where p.service.id = :serviceId")
    int ordreMax(Long serviceId);
}
