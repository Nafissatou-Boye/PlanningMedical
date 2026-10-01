package sn.mediplan.parametrage;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Stocke l'énumération en VARCHAR (colonne créée par Flyway). */
@Converter(autoApply = true)
public class TypeGardeConverter implements AttributeConverter<TypeGarde, String> {

    @Override
    public String convertToDatabaseColumn(TypeGarde valeur) {
        return valeur == null ? null : valeur.name();
    }

    @Override
    public TypeGarde convertToEntityAttribute(String colonne) {
        return colonne == null ? null : TypeGarde.valueOf(colonne);
    }
}
