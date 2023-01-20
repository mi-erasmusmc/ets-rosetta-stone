package eu.etransafe.controller.converters;

import eu.etransafe.domain.MappingAlgorithm;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class MappingAlgorithmConverter implements Converter<String, MappingAlgorithm> {

    @Override
    public MappingAlgorithm convert(String algorithm) {
        try {
            return MappingAlgorithm.valueOf(algorithm.toUpperCase());
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown algorithm: [" + algorithm + "]. Check docs to find out what is available");
        }
    }
}
