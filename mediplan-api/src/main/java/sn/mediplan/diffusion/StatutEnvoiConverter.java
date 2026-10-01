package sn.mediplan.diffusion;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class StatutEnvoiConverter implements AttributeConverter<StatutEnvoi, String> {

    @Override
    public String convertToDatabaseColumn(StatutEnvoi valeur) {
        return valeur == null ? null : valeur.name();
    }

    @Override
    public StatutEnvoi convertToEntityAttribute(String colonne) {
        return colonne == null ? null : StatutEnvoi.valueOf(colonne);
    }
}
