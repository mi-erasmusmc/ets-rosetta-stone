package eu.etransafe.domain.converters;

import eu.etransafe.domain.ConceptRelationship;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.stereotype.Component;

import static eu.etransafe.domain.CDMEnum.valueOfFromDb;

@Component
@Converter(autoApply = true)
public class RelationshipConverter implements AttributeConverter<ConceptRelationship.Identifier, String> {

    @Override
    public String convertToDatabaseColumn(ConceptRelationship.Identifier attribute) {
        if (attribute == null) {
            return null;
        }
        return attribute.value();
    }

    @Override
    public ConceptRelationship.Identifier convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return null;
        }
        return valueOfFromDb(dbData, ConceptRelationship.Identifier.class);
    }
}
