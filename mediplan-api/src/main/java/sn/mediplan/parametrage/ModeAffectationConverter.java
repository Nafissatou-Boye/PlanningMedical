package sn.mediplan.parametrage;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Stocke l'énumération en VARCHAR (colonne créée par Flyway). */
@Converter(autoApply = true)
public class ModeAffectationConverter implements AttributeConverter<ModeAffectation, String> {

    @Override
    public String convertToDatabaseColumn(ModeAffectation valeur) {
        return valeur == null ? null : valeur.name();
    }

    @Override
    public ModeAffectation convertToEntityAttribute(String colonne) {
        return colonne == null ? null : ModeAffectation.valueOf(colonne);
    }
}
