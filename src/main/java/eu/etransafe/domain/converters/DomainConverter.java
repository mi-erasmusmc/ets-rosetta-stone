package eu.etransafe.domain.converters;

import eu.etransafe.domain.Domain;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.stereotype.Component;

import static eu.etransafe.domain.CDMEnum.valueOfFromDb;

@Component
@Converter(autoApply = true)
public class DomainConverter implements AttributeConverter<Domain, String> {

    @Override
    public String convertToDatabaseColumn(Domain attribute) {
        if (attribute == null) {
            return null;
        }
        return attribute.value();
    }

    @Override
    public Domain convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return null;
        }
        return valueOfFromDb(dbData, Domain.class);
    }
}
