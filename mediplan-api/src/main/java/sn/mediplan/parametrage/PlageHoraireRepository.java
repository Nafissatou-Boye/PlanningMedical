package sn.mediplan.parametrage;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PlageHoraireRepository extends JpaRepository<PlageHoraire, Long> {

    @Query("select p from PlageHoraire p order by p.ordreAffichage, p.id")
    List<PlageHoraire> findAllOrdonnees();

    boolean existsBySousServiceIdAndLibelleIgnoreCase(Long sousServiceId, String libelle);

    @Query("select coalesce(max(p.ordreAffichage), 0) from PlageHoraire p where p.sousService.id = :sousServiceId")
    int ordreMax(Long sousServiceId);
}
