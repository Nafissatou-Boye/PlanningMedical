package sn.mediplan.utilisateur;

import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UtilisateurRepository extends JpaRepository<Utilisateur, Long> {

    /** Charge aussi le service, pour construire le profil sans requête supplémentaire. */
    @EntityGraph(attributePaths = "service")
    Optional<Utilisateur> findByNomUtilisateurIgnoreCase(String nomUtilisateur);

    boolean existsByNomUtilisateurIgnoreCase(String nomUtilisateur);
}
