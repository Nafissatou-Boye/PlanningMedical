package sn.mediplan.diffusion;

import java.awt.Color;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import org.springframework.core.io.ClassPathResource;

import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Image;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;

import sn.mediplan.parametrage.Etablissement;
import sn.mediplan.planning.PlanningDtos.CongeDuMois;

/** Éléments communs aux PDF officiels (gardes et stagiaires) : palette, polices, en-tête, signatures. */
public final class PdfCommun {

    private PdfCommun() {
    }

    public static final String[] MOIS = { "JANVIER", "FÉVRIER", "MARS", "AVRIL", "MAI", "JUIN", "JUILLET", "AOÛT",
            "SEPTEMBRE", "OCTOBRE", "NOVEMBRE", "DÉCEMBRE" };
    public static final DateTimeFormatter JJ_MM_AAAA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    // Palette MediPlan
    public static final Color NOIR = new Color(0x17, 0x3B, 0x5E);        // texte bleu foncé
    public static final Color BLEU_MEDICAL = new Color(0x28, 0x79, 0xA8);
    public static final Color TURQUOISE = new Color(0x4F, 0xAF, 0xC2);
    public static final Color BLEU_CIEL = new Color(0xCF, 0xEF, 0xF5);
    public static final Color FOND = new Color(0xF3, 0xFA, 0xFC);
    public static final Color BORDURE = new Color(0xB9, 0xDD, 0xE8);
    public static final Color GRIS_TEXTE = new Color(0x7A, 0x93, 0xA8);
    public static final Color VERT = new Color(0x2E, 0x8B, 0x57);         // ministère (comme l'original)
    public static final Color ORANGE = new Color(0xC0, 0x62, 0x1E);       // week-ends et fériés
    public static final Color FOND_WEEKEND = new Color(0xFF, 0xF4, 0xEC);
    public static final Color PASTEL_JOUR = new Color(0xFF, 0xF1, 0xBF);
    public static final Color PASTEL_NUIT = new Color(0xDD, 0xEC, 0xFA);
    public static final Color PASTEL_24H = new Color(0xEE, 0xE5, 0xF8);
    public static final Color ASTREINTE = new Color(0xD9, 0xF3, 0xF0);
    public static final Color SAUMON = new Color(0xFC, 0xE4, 0xD6);       // poste partiel (VPA) par défaut

    /** Nom de fichier sans accents ni espaces : « anesthesie-reanimation ». */
    public static String slug(String texte) {
        return java.text.Normalizer.normalize(texte, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
    }

    /** En-tête officiel : logo | République, devise, ministère, établissement, service | référence, lieu et date. */
    public static PdfPTable entete(String service, String numeroReference, LocalDate dateDocument,
            Etablissement etab) {
        String republique = etab != null ? etab.getRepublique() : "REPUBLIQUE DU SENEGAL";
        String devise = etab != null ? etab.getDevise() : "Un Peuple – Un But – Une Foi";
        String ministere = etab != null ? etab.getMinistere() : "Ministère de la Santé et de l'Action Sociale";
        String nomEtab = etab != null ? etab.getNom() : "";
        String ville = etab != null ? etab.getVille() : "";

        PdfPTable t = new PdfPTable(new float[] { 8, 58, 34 });
        t.setWidthPercentage(100);

        PdfPCell celluleLogo = new PdfPCell();
        bordureBas(celluleLogo);
        celluleLogo.setVerticalAlignment(Element.ALIGN_MIDDLE);
        Image logo = logo(etab);
        if (logo != null) {
            logo.scaleToFit(36, 36);
            celluleLogo.addElement(logo);
        }
        t.addCell(celluleLogo);

        PdfPTable bloc = new PdfPTable(1);
        bloc.addCell(texte(republique, police(11, Font.BOLD, NOIR)));
        bloc.addCell(texte(devise, police(7.5f, Font.ITALIC, GRIS_TEXTE)));
        bloc.addCell(texte(ministere, police(9.5f, Font.BOLD, VERT)));
        bloc.addCell(texte(nomEtab, police(10, Font.BOLD, NOIR)));
        bloc.addCell(texte("Service " + service, police(10, Font.BOLD, BLEU_MEDICAL)));
        PdfPCell celluleBloc = new PdfPCell(bloc);
        bordureBas(celluleBloc);
        t.addCell(celluleBloc);

        PdfPTable droite = new PdfPTable(1);
        // Comme sur le document officiel : « N°……..MSAS/CHNP/ANESTH-REA » ; sans référence, pointillés à compléter
        boolean avecRef = numeroReference != null && !numeroReference.isBlank();
        PdfPCell ref = texte("N°……." + (avecRef ? numeroReference : "……………………………"),
                police(10, Font.BOLD, BLEU_MEDICAL));
        ref.setHorizontalAlignment(Element.ALIGN_RIGHT);
        droite.addCell(ref);
        LocalDate date = dateDocument != null ? dateDocument : LocalDate.now();
        PdfPCell lieu = texte((ville == null || ville.isBlank() ? "" : ville + ", ") + "le " + date.format(JJ_MM_AAAA),
                police(9.5f, Font.NORMAL, NOIR));
        lieu.setHorizontalAlignment(Element.ALIGN_RIGHT);
        lieu.setPaddingTop(4);
        droite.addCell(lieu);
        PdfPCell celluleDroite = new PdfPCell(droite);
        bordureBas(celluleDroite);
        t.addCell(celluleDroite);
        return t;
    }

    private static void bordureBas(PdfPCell c) {
        c.setBorder(Rectangle.BOTTOM);
        c.setBorderColor(TURQUOISE);
        c.setBorderWidth(1.2f);
        c.setPaddingBottom(8);
    }

    public static Image logo(Etablissement etab) {
        String chemin = etab != null && etab.getLogoChemin() != null ? etab.getLogoChemin() : "pdf/logo-ministere.jpg";
        ClassPathResource ressource = new ClassPathResource(chemin);
        if (!ressource.exists()) {
            return null;
        }
        try (InputStream in = ressource.getInputStream()) {
            return Image.getInstance(in.readAllBytes());
        } catch (Exception e) {
            return null;
        }
    }

    public static PdfPTable signatures(List<String> fonctions, float espaceAvant) {
        return signatures(fonctions, java.util.Collections.nCopies(fonctions.size(), (String) null), espaceAvant);
    }

    /** Fonctions soulignées ; si un nom est donné, il est imprimé sous la place laissée à la signature. */
    public static PdfPTable signatures(List<String> fonctions, List<String> noms, float espaceAvant) {
        PdfPTable t = new PdfPTable(fonctions.size());
        t.setWidthPercentage(100);
        t.setSpacingBefore(espaceAvant);
        for (int i = 0; i < fonctions.size(); i++) {
            Phrase ph = new Phrase(fonctions.get(i), police(10.5f, Font.BOLD | Font.UNDERLINE, NOIR));
            String nom = i < noms.size() ? noms.get(i) : null;
            if (nom != null && !nom.isBlank()) {
                ph.add(new Phrase("\n\n" + nom, police(10, Font.BOLD, NOIR)));
            }
            PdfPCell c = new PdfPCell(ph);
            c.setBorder(Rectangle.NO_BORDER);
            c.setHorizontalAlignment(Element.ALIGN_CENTER);
            c.setMinimumHeight(45);
            t.addCell(c);
        }
        return t;
    }

    /** Pastille de légende : petit carré coloré + libellé. */
    public static void pastille(PdfPTable t, Color couleur, String libelle) {
        PdfPCell p = new PdfPCell(new Phrase(" "));
        p.setBackgroundColor(couleur);
        p.setBorderColor(BORDURE);
        p.setFixedHeight(9);
        t.addCell(p);
        t.addCell(texte(libelle, police(7.5f, Font.NORMAL, GRIS_TEXTE)));
    }

    /** Helvetica, encodage Windows (accents français). */
    public static Font police(float taille, int style, Color couleur) {
        return FontFactory.getFont(FontFactory.HELVETICA, BaseFont.CP1252, taille, style, couleur);
    }

    public static PdfPCell texte(String contenu, Font police) {
        PdfPCell c = new PdfPCell(new Phrase(contenu, police));
        c.setBorder(Rectangle.NO_BORDER);
        c.setPaddingTop(0);
        c.setPaddingBottom(1.5f);
        c.setPaddingLeft(2);
        return c;
    }

    public static PdfPCell cellule(String contenu, Font police, Color fond) {
        PdfPCell c = new PdfPCell(new Phrase(contenu, police));
        c.setHorizontalAlignment(Element.ALIGN_CENTER);
        c.setVerticalAlignment(Element.ALIGN_MIDDLE);
        c.setBorderColor(BORDURE);
        c.setPaddingTop(1.5f);
        c.setPaddingBottom(2.5f);
        if (fond != null) {
            c.setBackgroundColor(fond);
        }
        return c;
    }

    /** « #FCE4D6 » → couleur ; valeur par défaut si vide ou invalide. */
    public static Color couleur(String hex, Color parDefaut) {
        if (hex == null || !hex.matches("#?[0-9a-fA-F]{6}")) {
            return parDefaut;
        }
        return new Color(Integer.parseInt(hex.replace("#", ""), 16));
    }

    public static PdfPTable conges(List<CongeDuMois> liste) {
        PdfPTable cadre = new PdfPTable(new float[] { 12, 88 });
        cadre.setWidthPercentage(100);
        cadre.setSpacingBefore(10);

        PdfPCell titre = new PdfPCell(new Phrase("CONGÉS", police(10, Font.BOLD, Color.WHITE)));
        titre.setBackgroundColor(TURQUOISE);
        titre.setBorder(Rectangle.NO_BORDER);
        titre.setHorizontalAlignment(Element.ALIGN_CENTER);
        titre.setVerticalAlignment(Element.ALIGN_MIDDLE);
        titre.setPadding(6);
        cadre.addCell(titre);

        String contenu = liste.isEmpty() ? "Aucun congé ce mois-ci"
                : liste.stream()
                        .map(c -> c.agent() + " : du " + c.dateDebut().format(JJ_MM_AAAA) + " au "
                                + c.dateFin().format(JJ_MM_AAAA))
                        .collect(Collectors.joining("     •     "));
        PdfPCell liste2 = new PdfPCell(new Phrase(contenu, police(9.5f, Font.BOLD, NOIR)));
        liste2.setBackgroundColor(FOND);
        liste2.setBorder(Rectangle.NO_BORDER);
        liste2.setVerticalAlignment(Element.ALIGN_MIDDLE);
        liste2.setPadding(6);
        liste2.setPaddingLeft(10);
        cadre.addCell(liste2);
        return cadre;
    }
}
