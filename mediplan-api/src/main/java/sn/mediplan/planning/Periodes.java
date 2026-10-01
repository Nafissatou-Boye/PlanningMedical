package sn.mediplan.planning;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Découpage des astreintes : du 1er au premier dimanche, puis du lundi au dimanche, jusqu'à la fin du mois. */
public final class Periodes {

    private Periodes() {
    }

    public record Periode(LocalDate debut, LocalDate fin) {
    }

    public static List<Periode> astreintes(int annee, int mois) {
        LocalDate debut = LocalDate.of(annee, mois, 1);
        LocalDate finMois = debut.withDayOfMonth(debut.lengthOfMonth());
        List<Periode> periodes = new ArrayList<>();
        while (!debut.isAfter(finMois)) {
            LocalDate dimanche = debut;
            while (dimanche.getDayOfWeek() != DayOfWeek.SUNDAY && dimanche.isBefore(finMois)) {
                dimanche = dimanche.plusDays(1);
            }
            periodes.add(new Periode(debut, dimanche));
            debut = dimanche.plusDays(1);
        }
        return periodes;
    }

    /**
     * Semaines du planning des stagiaires : du lundi au dimanche. Une semaine appartient au mois qui contient
     * son mercredi, comme sur le document officiel (« Du 31/08 au … » est en septembre, « Du 28/09 au 04/10 »
     * aussi). Ainsi, d'un mois à l'autre, aucune semaine n'est oubliée ni comptée deux fois.
     */
    public static List<Periode> semaines(int annee, int mois) {
        LocalDate premier = LocalDate.of(annee, mois, 1);
        LocalDate lundi = premier.with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        List<Periode> semaines = new ArrayList<>();
        for (; ; lundi = lundi.plusWeeks(1)) {
            LocalDate mercredi = lundi.plusDays(2);
            if (mercredi.getYear() * 12 + mercredi.getMonthValue() > annee * 12 + mois) {
                break;
            }
            if (mercredi.getMonthValue() == mois) {
                semaines.add(new Periode(lundi, lundi.plusDays(6)));
            }
        }
        return semaines;
    }
}
