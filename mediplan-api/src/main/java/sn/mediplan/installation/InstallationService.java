package sn.mediplan.installation;

import java.time.LocalDateTime;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import sn.mediplan.commun.RegleMetierException;
import sn.mediplan.parametrage.ServiceHospitalier;
import sn.mediplan.parametrage.ServiceHospitalierRepository;
import sn.mediplan.utilisateur.Utilisateur;
import sn.mediplan.utilisateur.UtilisateurRepository;

/**
 * Première visite : crée le compte unique de l'administrateur et son service.
 * Une fois un compte créé, l'installation est définitivement fermée.
 */
@Service
public class InstallationService {

    private final UtilisateurRepository utilisateurs;
    private final ServiceHospitalierRepository services;
    private final PasswordEncoder encodeur;

    public InstallationService(UtilisateurRepository utilisateurs, ServiceHospitalierRepository services,
            PasswordEncoder encodeur) {
        this.utilisateurs = utilisateurs;
        this.services = services;
        this.encodeur = encodeur;
    }

    @Transactional(readOnly = true)
    public boolean estInstallee() {
        return utilisateurs.count() > 0;
    }

    @Transactional
    public Utilisateur installer(InstallationRequete r) {
        if (estInstallee()) {
            throw RegleMetierException.conflit(
                    "MediPlan est déjà configuré. Connectez-vous avec votre nom d'utilisateur.");
        }

        String nomService = r.service().trim();
        ServiceHospitalier service = services.findByNomIgnoreCase(nomService)
                .orElseGet(() -> services.save(new ServiceHospitalier(nomService)));

        Utilisateur u = new Utilisateur();
        u.setNom(r.nom().trim());
        u.setPrenom(r.prenom().trim());
        u.setNomUtilisateur(Utilisateur.normaliserNomUtilisateur(r.nomUtilisateur()));
        u.setPoste(r.poste().trim());
        u.setService(service);
        u.setMotDePasseHache(encodeur.encode(r.motDePasse()));
        u.setActif(true);
        u.setDateCreation(LocalDateTime.now());
        return utilisateurs.save(u);
    }
}
