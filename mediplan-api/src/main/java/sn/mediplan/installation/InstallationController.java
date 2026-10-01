package sn.mediplan.installation;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import sn.mediplan.auth.ReponseConnexion;
import sn.mediplan.securite.JwtService;
import sn.mediplan.utilisateur.ProfilDto;
import sn.mediplan.utilisateur.Utilisateur;

@RestController
@RequestMapping("/api/installation")
public class InstallationController {

    private final InstallationService installation;
    private final JwtService jwt;

    public InstallationController(InstallationService installation, JwtService jwt) {
        this.installation = installation;
        this.jwt = jwt;
    }

    /** Le frontend l'appelle au démarrage : première visite ou écran de connexion ? */
    @GetMapping("/statut")
    public StatutInstallation statut() {
        return new StatutInstallation(installation.estInstallee());
    }

    /** Crée le compte et connecte directement l'utilisateur. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ReponseConnexion installer(@Valid @RequestBody InstallationRequete requete) {
        Utilisateur u = installation.installer(requete);
        return new ReponseConnexion(jwt.generer(u.getNomUtilisateur()), jwt.dureeEnSecondes(), ProfilDto.de(u));
    }

    public record StatutInstallation(boolean installee) {
    }
}
