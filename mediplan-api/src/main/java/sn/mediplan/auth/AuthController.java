package sn.mediplan.auth;

import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import sn.mediplan.commun.RegleMetierException;
import sn.mediplan.securite.JwtService;
import sn.mediplan.utilisateur.ProfilDto;
import sn.mediplan.utilisateur.Utilisateur;
import sn.mediplan.utilisateur.UtilisateurRepository;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    /** Même message que le nom d'utilisateur ou le mot de passe soit faux (ne rien révéler). */
    private static final String IDENTIFIANTS_INVALIDES = "Nom d'utilisateur ou mot de passe incorrect.";

    private final UtilisateurRepository utilisateurs;
    private final PasswordEncoder encodeur;
    private final JwtService jwt;

    public AuthController(UtilisateurRepository utilisateurs, PasswordEncoder encodeur, JwtService jwt) {
        this.utilisateurs = utilisateurs;
        this.encodeur = encodeur;
        this.jwt = jwt;
    }

    @PostMapping("/connexion")
    @Transactional(readOnly = true)
    public ReponseConnexion connexion(@Valid @RequestBody ConnexionRequete requete) {
        Utilisateur u = utilisateurs.findByNomUtilisateurIgnoreCase(
                        Utilisateur.normaliserNomUtilisateur(requete.nomUtilisateur()))
                .filter(Utilisateur::isActif)
                .filter(trouve -> encodeur.matches(requete.motDePasse(), trouve.getMotDePasseHache()))
                .orElseThrow(() -> RegleMetierException.nonAutorise(IDENTIFIANTS_INVALIDES));
        return new ReponseConnexion(jwt.generer(u.getNomUtilisateur()), jwt.dureeEnSecondes(), ProfilDto.de(u));
    }

    /** Profil de l'utilisateur connecté (appelé au rechargement de la page). */
    @GetMapping("/moi")
    @Transactional(readOnly = true)
    public ProfilDto moi(Authentication authentification) {
        return utilisateurs.findByNomUtilisateurIgnoreCase(authentification.getName())
                .filter(Utilisateur::isActif)
                .map(ProfilDto::de)
                .orElseThrow(() -> RegleMetierException.nonAutorise("Votre session n'est plus valide."));
    }
}
