package sn.mediplan.personnel;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class CategorieAgentConverter implements AttributeConverter<CategorieAgent, String> {

    @Override
    public String convertToDatabaseColumn(CategorieAgent valeur) {
        return valeur == null ? null : valeur.name();
    }

    @Override
    public CategorieAgent convertToEntityAttribute(String colonne) {
        return colonne == null ? null : CategorieAgent.valueOf(colonne);
    }
}
