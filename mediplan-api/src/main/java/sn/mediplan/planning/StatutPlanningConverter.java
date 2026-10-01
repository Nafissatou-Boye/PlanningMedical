package sn.mediplan.planning;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class StatutPlanningConverter implements AttributeConverter<StatutPlanning, String> {

    @Override
    public String convertToDatabaseColumn(StatutPlanning valeur) {
        return valeur == null ? null : valeur.name();
    }

    @Override
    public StatutPlanning convertToEntityAttribute(String colonne) {
        return colonne == null ? null : StatutPlanning.valueOf(colonne);
    }
}
