package eu.etransafe.domain.converters;

import eu.etransafe.domain.Vocabulary;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.stereotype.Component;

import static eu.etransafe.domain.CDMEnum.valueOfFromDb;

@Component
@Converter(autoApply = true)
public class VocabConverter implements AttributeConverter<Vocabulary.Identifier, String> {

    @Override
    public String convertToDatabaseColumn(Vocabulary.Identifier attribute) {
        if (attribute == null) {
            return null;
        }
        return attribute.value();
    }

    @Override
    public Vocabulary.Identifier convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return null;
        }
        return valueOfFromDb(dbData, Vocabulary.Identifier.class);
    }
}
