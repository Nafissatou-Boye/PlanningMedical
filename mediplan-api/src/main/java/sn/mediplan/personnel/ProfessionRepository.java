package sn.mediplan.personnel;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProfessionRepository extends JpaRepository<Profession, Long> {

    List<Profession> findAllByOrderByLibelleAsc();

    boolean existsByLibelleIgnoreCase(String libelle);
}
