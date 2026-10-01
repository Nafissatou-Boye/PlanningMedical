package sn.mediplan.parametrage;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SousServiceRepository extends JpaRepository<SousService, Long> {

    @Query("select s from SousService s order by s.ordreAffichage, s.id")
    List<SousService> findAllOrdonnes();

    boolean existsByServiceIdAndNomIgnoreCase(Long serviceId, String nom);

    @Query("select coalesce(max(s.ordreAffichage), 0) from SousService s where s.service.id = :serviceId")
    int ordreMax(Long serviceId);
}
