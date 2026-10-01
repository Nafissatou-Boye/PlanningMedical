package sn.mediplan.securite;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import sn.mediplan.utilisateur.UtilisateurRepository;

/**
 * Sécurité de l'API :
 * - sans session (chaque requête porte son jeton JWT) ;
 * - dans /api, seules l'installation et la connexion sont publiques ;
 * - l'écran Angular (tout ce qui n'est pas /api) est servi librement par la même application ;
 * - réponses 401 / 403 en JSON, en français.
 */
@Configuration
public class SecuriteConfig {

    @Bean
    SecurityFilterChain chaineSecurite(HttpSecurity http, JwtService jwtService) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/api/installation/**", "/api/auth/connexion", "/error").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        // Tout le reste = l'écran Angular (fichiers statiques), servi sans connexion
                        .anyRequest().permitAll())
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, rep, ex) ->
                                ecrireErreur(rep, 401, "Vous devez vous connecter."))
                        .accessDeniedHandler((req, rep, ex) ->
                                ecrireErreur(rep, 403, "Accès refusé.")))
                .addFilterBefore(new JwtFiltre(jwtService), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    PasswordEncoder encodeurMotDePasse() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Charge l'administrateur depuis la base. Déclarer ce bean évite aussi que Spring Boot
     * crée un utilisateur « user » par défaut avec un mot de passe affiché dans la console.
     */
    @Bean
    UserDetailsService chargeurUtilisateur(UtilisateurRepository utilisateurs) {
        return nomUtilisateur -> utilisateurs.findByNomUtilisateurIgnoreCase(nomUtilisateur)
                .filter(u -> u.isActif())
                .map(u -> User.withUsername(u.getNomUtilisateur())
                        .password(u.getMotDePasseHache())
                        .roles("ADMIN")
                        .build())
                .orElseThrow(() -> new UsernameNotFoundException(nomUtilisateur));
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(@Value("${mediplan.cors.origines}") List<String> origines) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(origines);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        config.setExposedHeaders(List.of("Content-Disposition")); // nom du fichier PDF
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }

    private static void ecrireErreur(jakarta.servlet.http.HttpServletResponse reponse, int statut, String message)
            throws java.io.IOException {
        reponse.setStatus(statut);
        reponse.setContentType(MediaType.APPLICATION_JSON_VALUE);
        reponse.setCharacterEncoding(StandardCharsets.UTF_8.name());
        reponse.getWriter().write(
                "{\"statut\":" + statut + ",\"message\":\"" + message + "\",\"champs\":{}}");
    }
}
