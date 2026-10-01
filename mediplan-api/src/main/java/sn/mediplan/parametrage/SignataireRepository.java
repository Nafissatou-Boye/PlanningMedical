package sn.mediplan.parametrage;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SignataireRepository extends JpaRepository<Signataire, Long> {

    List<Signataire> findByServiceIdOrderByOrdreAffichageAsc(Long serviceId);

    void deleteByServiceId(Long serviceId);
}
