package sn.mediplan.securite;

import java.io.IOException;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Lit l'en-tête « Authorization: Bearer ... » et authentifie la requête si le jeton est valide.
 * Volontairement pas annoté @Component : il est ajouté uniquement dans la chaîne Spring Security.
 */
public class JwtFiltre extends OncePerRequestFilter {

    private static final String PREFIXE = "Bearer ";

    private final JwtService jwtService;

    public JwtFiltre(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest requete, HttpServletResponse reponse, FilterChain chaine)
            throws ServletException, IOException {
        String entete = requete.getHeader(HttpHeaders.AUTHORIZATION);
        if (entete != null && entete.startsWith(PREFIXE)
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            jwtService.lireNomUtilisateur(entete.substring(PREFIXE.length()).trim())
                    .ifPresent(nomUtilisateur -> {
                        var authentification = new UsernamePasswordAuthenticationToken(
                                nomUtilisateur, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
                        SecurityContextHolder.getContext().setAuthentication(authentification);
                    });
        }
        chaine.doFilter(requete, reponse);
    }
}
