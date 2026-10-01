package sn.mediplan.conges;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class TypeCongeConverter implements AttributeConverter<TypeConge, String> {

    @Override
    public String convertToDatabaseColumn(TypeConge valeur) {
        return valeur == null ? null : valeur.name();
    }

    @Override
    public TypeConge convertToEntityAttribute(String colonne) {
        return colonne == null ? null : TypeConge.valueOf(colonne);
    }
}
