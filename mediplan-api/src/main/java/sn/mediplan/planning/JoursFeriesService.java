package sn.mediplan.planning;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import sn.mediplan.commun.RegleMetierException;

/**
 * Jours fériés. Ils s'affichent dans le planning des gardes et le PDF (comme un week-end, avec leur nom).
 * Les fériés à date fixe et ceux qui dépendent de Pâques sont calculés automatiquement ; les fêtes musulmanes
 * (Korité, Tabaski, Tamkharit, Maouloud, Grand Magal) dépendent de la lune et se saisissent quand leur date est
 * annoncée.
 */
@Service
public class JoursFeriesService {

    public record JourFerieDto(Long id, LocalDate date, String libelle) {
    }

    public record JourFerieRequete(
            @NotNull(message = "Choisissez une date.") LocalDate date,
            @NotBlank(message = "Donnez un nom au jour férié.") @Size(max = 100, message = "100 caractères maximum.") String libelle) {
    }

    private final JourFerieRepository feries;

    public JoursFeriesService(JourFerieRepository feries) {
        this.feries = feries;
    }

    @Transactional(readOnly = true)
    public List<JourFerieDto> lister(int annee) {
        return feries.findByDateFerieBetweenOrderByDateFerieAsc(LocalDate.of(annee, 1, 1), LocalDate.of(annee, 12, 31))
                .stream().map(f -> new JourFerieDto(f.getId(), f.getDateFerie(), f.getLibelle())).toList();
    }

    @Transactional
    public JourFerieDto creer(JourFerieRequete r) {
        if (feries.existsByDateFerie(r.date())) {
            throw RegleMetierException.conflit("Un jour férié existe déjà à cette date.");
        }
        JourFerie f = new JourFerie();
        f.setDateFerie(r.date());
        f.setLibelle(r.libelle().trim());
        feries.saveAndFlush(f);
        return new JourFerieDto(f.getId(), f.getDateFerie(), f.getLibelle());
    }

    @Transactional
    public JourFerieDto modifier(Long id, JourFerieRequete r) {
        JourFerie f = feries.findById(id)
                .orElseThrow(() -> RegleMetierException.introuvable("Jour férié introuvable."));
        if (feries.existsByDateFerieAndIdNot(r.date(), id)) {
            throw RegleMetierException.conflit("Un jour férié existe déjà à cette date.");
        }
        f.setDateFerie(r.date());
        f.setLibelle(r.libelle().trim());
        return new JourFerieDto(f.getId(), f.getDateFerie(), f.getLibelle());
    }

    @Transactional
    public void supprimer(Long id) {
        feries.deleteById(id);
    }

    /** Ajoute les fériés du Sénégal calculables pour l'année (ceux déjà saisis ne sont pas dupliqués). */
    @Transactional
    public List<JourFerieDto> ajouterFeriesSenegal(int annee) {
        for (Map.Entry<LocalDate, String> e : feriesCalculables(annee).entrySet()) {
            if (!feries.existsByDateFerie(e.getKey())) {
                JourFerie f = new JourFerie();
                f.setDateFerie(e.getKey());
                f.setLibelle(e.getValue());
                feries.save(f);
            }
        }
        feries.flush();
        return lister(annee);
    }

    /** Fériés à date fixe et fériés liés à Pâques. */
    public static Map<LocalDate, String> feriesCalculables(int annee) {
        LocalDate paques = paques(annee);
        Map<LocalDate, String> m = new LinkedHashMap<>(); // deux fêtes le même jour : « A / B »
        m.merge(LocalDate.of(annee, 1, 1), "Jour de l'An", (x, y) -> x + " / " + y);
        m.merge(LocalDate.of(annee, 4, 4), "Fête de l'Indépendance", (x, y) -> x + " / " + y);
        m.merge(paques.plusDays(1), "Lundi de Pâques", (x, y) -> x + " / " + y);
        m.merge(LocalDate.of(annee, 5, 1), "Fête du Travail", (x, y) -> x + " / " + y);
        m.merge(paques.plusDays(39), "Ascension", (x, y) -> x + " / " + y);
        m.merge(paques.plusDays(50), "Lundi de Pentecôte", (x, y) -> x + " / " + y);
        m.merge(LocalDate.of(annee, 8, 15), "Assomption", (x, y) -> x + " / " + y);
        m.merge(LocalDate.of(annee, 11, 1), "Toussaint", (x, y) -> x + " / " + y);
        m.merge(LocalDate.of(annee, 12, 25), "Noël", (x, y) -> x + " / " + y);
        return m;
    }

    /** Dimanche de Pâques (calendrier grégorien, algorithme de Meeus/Jones/Butcher). */
    public static LocalDate paques(int annee) {
        int a = annee % 19, b = annee / 100, c = annee % 100, d = b / 4, e = b % 4;
        int f = (b + 8) / 25, g = (b - f + 1) / 3, h = (19 * a + b - d - g + 15) % 30;
        int i = c / 4, k = c % 4, l = (32 + 2 * e + 2 * i - h - k) % 7;
        int m = (a + 11 * h + 22 * l) / 451;
        int mois = (h + l - 7 * m + 114) / 31, jour = (h + l - 7 * m + 114) % 31 + 1;
        return LocalDate.of(annee, mois, jour);
    }
}
