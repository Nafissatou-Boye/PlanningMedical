package sn.mediplan.diffusion;

import static sn.mediplan.diffusion.PdfCommun.*;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.List;
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
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

import sn.mediplan.parametrage.Etablissement;
import sn.mediplan.parametrage.EtablissementRepository;
import sn.mediplan.stagiaires.StagiairesDtos.CaseStage;
import sn.mediplan.stagiaires.StagiairesDtos.ColonnePoste;
import sn.mediplan.stagiaires.StagiairesDtos.GrilleStagiaires;
import sn.mediplan.stagiaires.StagiairesDtos.Semaine;
import sn.mediplan.stagiaires.StagiairesService;

/**
 * PDF « ORGANISATION DES ÉTUDIANTS STAGIAIRES », sur le modèle du document du CHN de Pikine :
 * A4 paysage, une ligne par semaine, une colonne par poste, poste partiel (VPA) coloré,
 * signature du Chef de Service. Même en-tête et même palette que le planning des gardes.
 */
@Service
public class PdfStagiairesService {

    private final StagiairesService stagiaires;
    private final EtablissementRepository etablissements;

    public PdfStagiairesService(StagiairesService stagiaires, EtablissementRepository etablissements) {
        this.stagiaires = stagiaires;
        this.etablissements = etablissements;
    }

    /** organisation-stagiaires-anesthesie-reanimation-2026-09.pdf */
    public String nomFichier(GrilleStagiaires g) {
        return "organisation-stagiaires-" + slug(g.service()) + "-" + g.annee() + "-"
                + String.format("%02d", g.mois()) + ".pdf";
    }

    @Transactional(readOnly = true)
    public byte[] generer(Long planningId) {
        GrilleStagiaires g = stagiaires.grille(planningId);
        Etablissement etab = etablissements.findFirstByOrderByIdAsc().orElse(null);
        return generer(g, etab);
    }

    private byte[] generer(GrilleStagiaires g, Etablissement etab) {
        ByteArrayOutputStream sortie = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4.rotate(), 36, 36, 28, 28);
        try {
            PdfWriter.getInstance(doc, sortie);
            doc.addTitle("Organisation des stagiaires " + g.service() + " / " + MOIS[g.mois() - 1] + " " + g.annee());
            doc.addCreator("MediPlan");
            doc.open();

            doc.add(entete(g.service(), g.numeroReference(), g.dateDocument(), etab));
            doc.add(titre(g));
            doc.add(grille(g));
            if (g.postes().stream().anyMatch(ColonnePoste::partiel)) {
                doc.add(legende(g));
            }
            if (!g.conges().isEmpty()) {
                doc.add(conges(g.conges()));
            }
            doc.add(signatures(List.of("", "", "Le Chef de Service"), 14));

            doc.close();
        } catch (Exception e) {
            throw new IllegalStateException("Impossible de générer le PDF des stagiaires.", e);
        }
        return sortie.toByteArray();
    }

    private Paragraph titre(GrilleStagiaires g) {
        Paragraph p = new Paragraph();
        p.setAlignment(Element.ALIGN_CENTER);
        p.setSpacingBefore(10);
        p.setSpacingAfter(10);
        p.add(new Phrase("ORGANISATION DES ÉTUDIANTS STAGIAIRES\n", police(16, Font.BOLD, NOIR)));
        p.add(new Phrase(MOIS[g.mois() - 1] + " " + g.annee(), police(12, Font.BOLD, TURQUOISE)));
        return p;
    }

    private PdfPTable grille(GrilleStagiaires g) {
        List<ColonnePoste> postes = g.postes();
        float[] largeurs = new float[postes.size() + 1];
        largeurs[0] = 1.25f;
        for (int i = 1; i < largeurs.length; i++) {
            largeurs[i] = 1.5f;
        }
        PdfPTable t = new PdfPTable(largeurs);
        t.setWidthPercentage(100);
        t.setHeaderRows(1);

        // En-tête : SEMAINE | postes (poste partiel dans sa couleur, avec ses jours)
        PdfPCell semaine = cellule("SEMAINE", police(9.5f, Font.BOLD, Color.WHITE), NOIR);
        semaine.setPaddingTop(6);
        semaine.setPaddingBottom(7);
        t.addCell(semaine);
        for (ColonnePoste p : postes) {
            Color fond = p.partiel() ? couleur(p.couleur(), SAUMON) : BLEU_MEDICAL;
            Color encre = p.partiel() ? NOIR : Color.WHITE;
            Phrase ph = new Phrase(p.libelle(), police(9.5f, Font.BOLD, encre));
            String jours = StagiairesService.joursLisibles(p.joursActifs());
            if (!jours.isEmpty()) {
                ph.add(new Phrase("\n(" + jours + ")", police(7, Font.NORMAL, encre)));
            }
            PdfPCell c = new PdfPCell(ph);
            c.setBackgroundColor(fond);
            c.setBorderColor(BORDURE);
            c.setHorizontalAlignment(Element.ALIGN_CENTER);
            c.setVerticalAlignment(Element.ALIGN_MIDDLE);
            c.setPaddingTop(4);
            c.setPaddingBottom(6);
            t.addCell(c);
        }

        Map<String, CaseStage> cases = new HashMap<>();
        for (CaseStage c : g.cases()) {
            cases.put(c.periodeId() + "|" + c.posteId(), c);
        }

        // Hauteur des lignes : la grille occupe bien la page, comme l'original
        float hauteur = Math.max(30, Math.min(62, 320f / Math.max(1, g.semaines().size())));
        int i = 0;
        for (Semaine s : g.semaines()) {
            Color fondLigne = i++ % 2 == 1 ? FOND : Color.WHITE;

            // « Du 31/08 au » en petit, « 06/09/26 » en dessous
            String[] morceaux = s.libelle().split(" au ");
            Phrase ph = new Phrase(morceaux[0] + " au\n", police(8.5f, Font.BOLD, BLEU_MEDICAL));
            ph.add(new Phrase(morceaux.length > 1 ? morceaux[1] : "", police(10.5f, Font.BOLD, NOIR)));
            PdfPCell cs = new PdfPCell(ph);
            cs.setBackgroundColor(BLEU_CIEL);
            cs.setBorderColor(BORDURE);
            cs.setHorizontalAlignment(Element.ALIGN_CENTER);
            cs.setVerticalAlignment(Element.ALIGN_MIDDLE);
            cs.setMinimumHeight(hauteur);
            cs.setPaddingBottom(4);
            t.addCell(cs);

            for (ColonnePoste p : postes) {
                CaseStage c = cases.get(s.periodeId() + "|" + p.posteId());
                Color fond = p.partiel() ? couleur(p.couleur(), SAUMON) : fondLigne;
                String noms = c == null ? "" : c.stagiaires().stream().map(x -> x.nomAffichage())
                        .collect(Collectors.joining(" /\n"));
                PdfPCell cp = noms.isEmpty() ? cellule("—", police(9, Font.NORMAL, BORDURE), fond)
                        : cellule(noms, police(10, Font.BOLD, p.partiel() ? ORANGE : NOIR), fond);
                cp.setMinimumHeight(hauteur);
                cp.setPaddingBottom(4);
                t.addCell(cp);
            }
        }
        return t;
    }

    private PdfPTable legende(GrilleStagiaires g) {
        PdfPTable t = new PdfPTable(new float[] { 1, 14, 50 });
        t.setWidthPercentage(100);
        t.setSpacingBefore(6);
        ColonnePoste partiel = g.postes().stream().filter(ColonnePoste::partiel).findFirst().orElseThrow();
        pastille(t, couleur(partiel.couleur(), SAUMON), "Poste en plus du poste principal");
        t.addCell(texte("", police(7, Font.NORMAL, GRIS_TEXTE)));
        return t;
    }

}
