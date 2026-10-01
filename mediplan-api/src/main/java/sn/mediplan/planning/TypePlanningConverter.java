package sn.mediplan.planning;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class TypePlanningConverter implements AttributeConverter<TypePlanning, String> {

    @Override
    public String convertToDatabaseColumn(TypePlanning valeur) {
        return valeur == null ? null : valeur.name();
    }

    @Override
    public TypePlanning convertToEntityAttribute(String colonne) {
        return colonne == null ? null : TypePlanning.valueOf(colonne);
    }
}
