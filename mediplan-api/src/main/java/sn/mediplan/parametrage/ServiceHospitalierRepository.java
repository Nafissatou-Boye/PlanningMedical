package sn.mediplan.parametrage;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ServiceHospitalierRepository extends JpaRepository<ServiceHospitalier, Long> {

    Optional<ServiceHospitalier> findByNomIgnoreCase(String nom);

    @Query("select s from ServiceHospitalier s order by s.ordreAffichage, s.nom")
    List<ServiceHospitalier> findAllOrdonnes();

    @Query("select coalesce(max(s.ordreAffichage), 0) from ServiceHospitalier s")
    int ordreMax();
}
