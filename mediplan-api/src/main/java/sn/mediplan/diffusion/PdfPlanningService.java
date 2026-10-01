package sn.mediplan.diffusion;

import static sn.mediplan.diffusion.PdfCommun.*;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

import sn.mediplan.parametrage.Etablissement;
import sn.mediplan.parametrage.EtablissementRepository;
import sn.mediplan.parametrage.Signataire;
import sn.mediplan.parametrage.SignataireRepository;
import sn.mediplan.planning.PlanningDtos.Case;
import sn.mediplan.planning.PlanningDtos.ColonnePlage;
import sn.mediplan.planning.PlanningDtos.ColonneSousService;
import sn.mediplan.planning.PlanningDtos.Grille;
import sn.mediplan.planning.PlanningDtos.Jour;
import sn.mediplan.planning.PlanningDtos.PeriodeAstreinte;
import sn.mediplan.planning.PlanningService;

/**
 * PDF officiel du planning des gardes, sur le modèle du document du CHN de Pikine :
 * en-tête (République, n° de référence, devise, logo + ministère, établissement, service, lieu et date),
 * titre, grille (week-ends en rouge sur fond gris, binômes « A / B », astreintes fusionnées),
 * bloc CONGÉS et signatures.
 */
@Service
public class PdfPlanningService {

    private static final List<String> SIGNATAIRES_PAR_DEFAUT = List.of("Le Chef de Service", "Le Chef RH",
            "Le Directeur");


    private final PlanningService plannings;
    private final EtablissementRepository etablissements;
    private final SignataireRepository signataires;

    public PdfPlanningService(PlanningService plannings, EtablissementRepository etablissements,
            SignataireRepository signataires) {
        this.plannings = plannings;
        this.etablissements = etablissements;
        this.signataires = signataires;
    }

    /** Nom de fichier lisible : planning-gardes-anesthesie-reanimation-2026-10.pdf */
    public String nomFichier(Grille g) {
        String service = PdfCommun.slug(g.service());
        return "planning-gardes-" + service + "-" + g.annee() + "-" + String.format("%02d", g.mois()) + ".pdf";
    }

    @Transactional(readOnly = true)
    public byte[] generer(Long planningId) {
        Grille g = plannings.grille(planningId);
        Etablissement etab = etablissements.findFirstByOrderByIdAsc().orElse(null);
        List<Signataire> liste = signataires.findByServiceIdOrderByOrdreAffichageAsc(g.serviceId());
        List<String> fonctions = liste.isEmpty() ? SIGNATAIRES_PAR_DEFAUT : liste.stream().map(Signataire::getFonction).toList();
        List<String> noms = liste.stream().map(Signataire::getNom).toList();
        return generer(g, etab, fonctions, noms);
    }

    // =====================================================================

    private byte[] generer(Grille g, Etablissement etab, List<String> signatures, List<String> noms) {
        int nbPlages = g.colonnes().stream().mapToInt(c -> c.plages().size()).sum();
        // Portrait comme le document officiel (tout le mois sur une page) ; paysage si beaucoup de colonnes
        Rectangle format = nbPlages > 6 ? PageSize.A4.rotate() : PageSize.A4;

        ByteArrayOutputStream sortie = new ByteArrayOutputStream();
        Document doc = new Document(format, 30, 30, 28, 28);
        try {
            PdfWriter.getInstance(doc, sortie);
            doc.addTitle("Gardes " + g.service() + " / " + MOIS[g.mois() - 1] + " " + g.annee());
            doc.addCreator("MediPlan");
            doc.open();

            doc.add(PdfCommun.entete(g.service(), g.numeroReference(), g.dateDocument(), etab));
            doc.add(titre(g));
            doc.add(grille(g, nbPlages));
            doc.add(legende(g));
            doc.add(PdfCommun.conges(g.conges()));
            doc.add(PdfCommun.signatures(signatures, noms, 12));

            doc.close();
        } catch (Exception e) {
            throw new IllegalStateException("Impossible de générer le PDF du planning.", e);
        }
        return sortie.toByteArray();
    }

    private Paragraph titre(Grille g) {
        Paragraph p = new Paragraph();
        p.setAlignment(Element.ALIGN_CENTER);
        p.setSpacingBefore(10);
        p.setSpacingAfter(8);
        p.add(new Phrase("GARDES " + g.service().toUpperCase(Locale.FRENCH) + "\n", police(16, Font.BOLD, NOIR)));
        p.add(new Phrase(MOIS[g.mois() - 1] + " " + g.annee(), police(12, Font.BOLD, TURQUOISE)));
        return p;
    }

    // ---------- Grille ----------

    private PdfPTable grille(Grille g, int nbPlages) throws Exception {
        float taille = nbPlages > 8 ? 7f : 8f;

        float[] largeurs = new float[nbPlages + 2];
        largeurs[0] = 1.3f;
        for (int i = 1; i <= nbPlages; i++) {
            largeurs[i] = 1.6f;
        }
        largeurs[nbPlages + 1] = 1.3f;

        PdfPTable t = new PdfPTable(largeurs);
        t.setWidthPercentage(100);
        t.setHeaderRows(2); // en-tête répété si le tableau continue sur une autre page

        // Ligne 1 : DATES | sous-services (bandeau bleu médical) | ASTREINTES
        Font blanc = police(taille + 0.5f, Font.BOLD, Color.WHITE);
        PdfPCell dates = cellule("DATES", blanc, NOIR);
        dates.setRowspan(2);
        t.addCell(dates);
        for (ColonneSousService ss : g.colonnes()) {
            PdfPCell c = cellule(ss.nom().toUpperCase(Locale.FRENCH), blanc, BLEU_MEDICAL);
            c.setColspan(ss.plages().size());
            t.addCell(c);
        }
        PdfPCell astreintes = cellule("ASTREINTES", blanc, NOIR);
        astreintes.setRowspan(2);
        t.addCell(astreintes);

        // Ligne 2 : plages horaires, en pastel selon le type (Jour / Nuit / 24h)
        for (ColonneSousService ss : g.colonnes()) {
            for (ColonnePlage p : ss.plages()) {
                t.addCell(cellule(p.libelle().replace("-", " – "), police(taille, Font.BOLD, NOIR), couleurType(p)));
            }
        }

        // Lignes des jours
        Map<String, Case> cases = new HashMap<>();
        for (Case c : g.cases()) {
            cases.put(c.plageId() + "|" + c.date(), c);
        }
        Map<LocalDate, PeriodeAstreinte> astreinteParDebut = g.astreintes().stream()
                .collect(Collectors.toMap(PeriodeAstreinte::dateDebut, a -> a, (a, b) -> a));

        for (Jour j : g.jours()) {
            boolean weekend = j.weekend() || j.ferie() != null;
            Color fond = weekend ? FOND_WEEKEND : null;
            Color encre = weekend ? ORANGE : BLEU_MEDICAL;

            // « MARDI » en petites capitales suivi du numéro « 01 » en plus grand, sur une seule ligne
            String[] parties = j.libelle().split(" ");
            Phrase date = new Phrase();
            date.add(new Phrase(parties[0].toUpperCase(Locale.FRENCH) + "  ", police(taille - 1.5f, Font.BOLD, encre)));
            date.add(new Phrase(parties.length > 1 ? parties[1] : "", police(taille + 2.5f, Font.BOLD, encre)));
            if (j.ferie() != null) {
                // Astérisque renvoyant à la liste des fériés sous le tableau
                date.add(new Phrase("*", police(taille + 1, Font.BOLD, ORANGE)));
            }
            PdfPCell cDate = new PdfPCell(date);
            cDate.setHorizontalAlignment(Element.ALIGN_LEFT);
            cDate.setVerticalAlignment(Element.ALIGN_MIDDLE);
            cDate.setBorderColor(BORDURE);
            cDate.setPaddingLeft(5);
            cDate.setPaddingTop(1.5f);
            cDate.setPaddingBottom(2.5f);
            if (fond != null) {
                cDate.setBackgroundColor(fond);
            }
            t.addCell(cDate);

            for (ColonneSousService ss : g.colonnes()) {
                for (ColonnePlage p : ss.plages()) {
                    Case c = cases.get(p.plageId() + "|" + j.date());
                    String noms = c == null ? "" : c.agents().stream()
                            .map(a -> a.nomAffichage()).collect(Collectors.joining(" / "));
                    t.addCell(noms.isEmpty()
                            ? cellule("—", police(taille, Font.NORMAL, BORDURE), fond)
                            : cellule(noms, police(taille, weekend ? Font.BOLD : Font.NORMAL, weekend ? ORANGE : NOIR), fond));
                }
            }

            PeriodeAstreinte a = astreinteParDebut.get(j.date());
            if (a != null) {
                PdfPCell cA = cellule(a.nomAffichage() == null ? "—" : a.nomAffichage(),
                        police(taille + 1, Font.BOLD, NOIR), ASTREINTE);
                cA.setRowspan((int) (a.dateFin().toEpochDay() - a.dateDebut().toEpochDay()) + 1);
                t.addCell(cA);
            }
        }
        return t;
    }

    private static Color couleurType(ColonnePlage p) {
        return switch (p.typeGarde()) {
            case NUIT -> PASTEL_NUIT;
            case H24 -> PASTEL_24H;
            default -> PASTEL_JOUR;
        };
    }

    // ---------- Légende, congés et signatures ----------

    private PdfPTable legende(Grille g) throws Exception {
        PdfPTable t = new PdfPTable(new float[] { 1, 5, 1, 5, 1, 5, 1, 10, 1, 7, 35 });
        t.setWidthPercentage(100);
        t.setSpacingBefore(6);
        Object[][] items = { { PASTEL_JOUR, "Jour" }, { PASTEL_NUIT, "Nuit" }, { PASTEL_24H, "24h" }, { FOND_WEEKEND, "Week-end / férié" },
                { ASTREINTE, "Astreinte" } };
        for (Object[] item : items) {
            PdfPCell pastille = new PdfPCell(new Phrase(" "));
            pastille.setBackgroundColor((Color) item[0]);
            pastille.setBorderColor(BORDURE);
            pastille.setFixedHeight(9);
            t.addCell(pastille);
            t.addCell(texte((String) item[1], police(7.5f, Font.NORMAL, GRIS_TEXTE)));
        }
        // « * Férié : 25 Noël • … » : les fériés du mois, repérés par un astérisque dans la colonne DATES
        String feries = g.jours().stream().filter(j -> j.ferie() != null)
                .map(j -> String.format("%02d", j.date().getDayOfMonth()) + " " + j.ferie())
                .collect(Collectors.joining("  •  "));
        Phrase ph = new Phrase();
        if (!feries.isEmpty()) {
            ph.add(new Phrase("* Férié : ", police(7.5f, Font.BOLD, ORANGE)));
            ph.add(new Phrase(feries, police(7.5f, Font.NORMAL, ORANGE)));
        }
        PdfPCell c = new PdfPCell(ph);
        c.setBorder(Rectangle.NO_BORDER);
        c.setPaddingTop(0);
        c.setPaddingLeft(6);
        t.addCell(c);
        return t;
    }
}
