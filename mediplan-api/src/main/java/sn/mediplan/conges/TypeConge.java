package sn.mediplan.conges;

public enum TypeConge {
    ANNUEL,
    MALADIE,
    MATERNITE,
    FORMATION,
    /** Absence ponctuelle : bloque l'agent mais n'apparaît pas dans le bloc « CONGÉS » du PDF. */
    INDISPONIBILITE,
    AUTRE
}
