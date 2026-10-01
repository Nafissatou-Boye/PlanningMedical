package sn.mediplan.parametrage;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface EtablissementRepository extends JpaRepository<Etablissement, Long> {

    Optional<Etablissement> findFirstByOrderByIdAsc();
}
