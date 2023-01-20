package eu.etransafe.controller.converters;

import eu.etransafe.domain.Vocabulary;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class VocabRequestConverter implements Converter<String, Vocabulary.Type> {

    @Override
    public Vocabulary.Type convert(String source) {
        try {
            return Vocabulary.Type.valueOf(source.toUpperCase());
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown vocabulary type: " + source);
        }
    }
}
