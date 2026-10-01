package sn.mediplan.diffusion;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import jakarta.mail.AuthenticationFailedException;
import jakarta.mail.internet.MimeMessage;
import sn.mediplan.commun.RegleMetierException;

/**
 * Compte Gmail qui envoie les plannings. Il se règle depuis l'écran Paramètres :
 * l'adresse et le mot de passe d'application sont vérifiés auprès de Gmail, puis enregistrés dans
 * secrets.properties (à côté de l'application, jamais dans le code ni dans la base) et pris en compte
 * immédiatement, sans redémarrer.
 */
@Component
public class EnvoiEmail {

    private static final Logger LOG = LoggerFactory.getLogger(EnvoiEmail.class);
    private static final Path SECRETS = Paths.get("secrets.properties");

    private final String hote;
    private final int port;
    private volatile JavaMailSenderImpl expediteurSmtp;
    private volatile String adresse;

    public EnvoiEmail(Environment env) {
        this.hote = env.getProperty("spring.mail.host", "smtp.gmail.com");
        this.port = env.getProperty("spring.mail.port", Integer.class, 587);
        String user = env.getProperty("spring.mail.username", "");
        String mdp = env.getProperty("spring.mail.password", "");
        this.adresse = user == null ? "" : user.trim();
        this.expediteurSmtp = creer(this.adresse, mdp == null ? "" : mdp);
        if (!estConfigure()) {
            LOG.info("Envoi des e-mails non configuré (Paramètres › Envoi des e-mails).");
        }
    }

    public boolean estConfigure() {
        String mdp = expediteurSmtp.getPassword();
        return !adresse.isBlank() && mdp != null && !mdp.isBlank();
    }

    public String adresse() {
        return adresse;
    }

    public JavaMailSender expediteur() {
        return expediteurSmtp;
    }

    /** Vérifie le compte auprès de Gmail, envoie un e-mail de test, enregistre, puis l'utilise aussitôt. */
    public synchronized void configurer(String nouvelleAdresse, String motDePasse) {
        String a = nouvelleAdresse.trim();
        String m = motDePasse.replaceAll("\\s", ""); // Google affiche le mot de passe par groupes de 4 lettres
        JavaMailSenderImpl essai = creer(a, m);
        try {
            essai.testConnection();
        } catch (AuthenticationFailedException e) {
            throw RegleMetierException.conflit("Gmail refuse cette adresse ou ce mot de passe d'application. "
                    + "Vérifiez l'adresse, et utilisez un mot de passe d'application (16 lettres), pas le mot de passe du compte.");
        } catch (Exception e) {
            LOG.warn("Test SMTP impossible : {}", e.getMessage());
            throw RegleMetierException.conflit("Impossible de joindre Gmail : vérifiez la connexion Internet, puis réessayez.");
        }
        try {
            MimeMessage message = essai.createMimeMessage();
            MimeMessageHelper h = new MimeMessageHelper(message, false, "UTF-8");
            h.setFrom(a, "MediPlan");
            h.setTo(a);
            h.setSubject("MediPlan : l'envoi des e-mails fonctionne");
            h.setText("Bonjour,\n\nCe message confirme que MediPlan peut envoyer les plannings depuis cette adresse.\n\nMediPlan\n");
            essai.send(message);
        } catch (Exception e) {
            LOG.warn("E-mail de test non envoyé : {}", e.getMessage());
            throw RegleMetierException.conflit("La connexion à Gmail fonctionne, mais l'e-mail de test n'est pas parti : " + e.getMessage());
        }
        enregistrer(a, m);
        this.expediteurSmtp = essai;
        this.adresse = a;
    }

    private JavaMailSenderImpl creer(String user, String mdp) {
        JavaMailSenderImpl s = new JavaMailSenderImpl();
        s.setHost(hote);
        s.setPort(port);
        s.setUsername(user);
        s.setPassword(mdp);
        s.setDefaultEncoding("UTF-8");
        Properties p = s.getJavaMailProperties();
        p.put("mail.smtp.auth", "true");
        p.put("mail.smtp.starttls.enable", "true");
        p.put("mail.smtp.connectiontimeout", "10000");
        p.put("mail.smtp.timeout", "15000");
        p.put("mail.smtp.writetimeout", "15000");
        return s;
    }

    /** Remplace les deux lignes e-mail de secrets.properties en gardant les autres (clé de sécurité...). */
    private void enregistrer(String a, String m) {
        try {
            List<String> lignes = new ArrayList<>();
            if (Files.exists(SECRETS)) {
                for (String l : Files.readAllLines(SECRETS, StandardCharsets.ISO_8859_1)) {
                    if (!l.startsWith("MEDIPLAN_MAIL_USER=") && !l.startsWith("MEDIPLAN_MAIL_PASSWORD=")
                            && !l.contains("Configurer les e-mails")) {
                        lignes.add(l);
                    }
                }
            }
            lignes.add("MEDIPLAN_MAIL_USER=" + a);
            lignes.add("MEDIPLAN_MAIL_PASSWORD=" + m);
            Files.write(SECRETS, lignes, StandardCharsets.ISO_8859_1);
        } catch (IOException e) {
            throw RegleMetierException.conflit("Le compte fonctionne, mais il n'a pas pu être enregistré dans "
                    + SECRETS.toAbsolutePath() + " : " + e.getMessage());
        }
    }
}
