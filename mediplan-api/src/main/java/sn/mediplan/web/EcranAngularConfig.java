package sn.mediplan.web;

import java.io.IOException;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * Sert l'écran Angular compilé (copié dans resources/static au moment du paquetage).
 * Une adresse comme /planning ou /stagiaires?q=… n'est pas un fichier : on renvoie index.html
 * et c'est Angular qui affiche la bonne page. Les adresses /api ne sont jamais concernées.
 */
@Configuration
public class EcranAngularConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String chemin, Resource dossier) throws IOException {
                        Resource demande = dossier.createRelative(chemin);
                        if (demande.exists() && demande.isReadable()) {
                            return demande;
                        }
                        if (chemin.startsWith("api/") || chemin.contains(".")) {
                            return null; // vraie ressource absente : 404
                        }
                        Resource index = new ClassPathResource("/static/index.html");
                        return index.exists() ? index : null;
                    }
                });
    }
}
