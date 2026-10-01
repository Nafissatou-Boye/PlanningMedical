package sn.mediplan.securite;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/** Création et vérification des jetons de connexion (JWT signés HS256). */
@Service
public class JwtService {

    private final SecretKey cle;
    private final Duration duree;

    public JwtService(
            @Value("${mediplan.jwt.secret}") String secret,
            @Value("${mediplan.jwt.duree-heures:12}") long dureeHeures) {
        byte[] octets = secret.getBytes(StandardCharsets.UTF_8);
        if (octets.length < 32) {
            throw new IllegalStateException("mediplan.jwt.secret doit contenir au moins 32 caractères.");
        }
        this.cle = Keys.hmacShaKeyFor(octets);
        this.duree = Duration.ofHours(dureeHeures);
    }

    public String generer(String nomUtilisateur) {
        Instant maintenant = Instant.now();
        return Jwts.builder()
                .subject(nomUtilisateur)
                .issuer("mediplan")
                .issuedAt(Date.from(maintenant))
                .expiration(Date.from(maintenant.plus(duree)))
                .signWith(cle)
                .compact();
    }

    /** Renvoie le nom d'utilisateur si le jeton est valide et non expiré. */
    public Optional<String> lireNomUtilisateur(String jeton) {
        try {
            String sujet = Jwts.parser()
                    .verifyWith(cle)
                    .requireIssuer("mediplan")
                    .build()
                    .parseSignedClaims(jeton)
                    .getPayload()
                    .getSubject();
            return Optional.ofNullable(sujet);
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public long dureeEnSecondes() {
        return duree.toSeconds();
    }
}
