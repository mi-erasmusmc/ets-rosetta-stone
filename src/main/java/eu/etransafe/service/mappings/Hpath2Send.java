package eu.etransafe.service.mappings;


import eu.etransafe.domain.Concept;
import eu.etransafe.domain.Mapping;
import eu.etransafe.domain.Vocabulary;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Set;

import static eu.etransafe.domain.Vocabularies.ETOX;
import static eu.etransafe.domain.Vocabularies.SEND;
import static java.util.stream.Collectors.toSet;

@Slf4j
@Service
public class Hpath2Send {

    private final MappingService mappingService;
    private final MappingCache mappingCache;

    public Hpath2Send(MappingService mappingService, MappingCache mappingCache) {
        this.mappingService = mappingService;
        this.mappingCache = mappingCache;
    }

    // Extremely basic implementation, should add the hierarchical traversal stuff
    // Instead of mapping hpath to send, an endpoint that standardizes any word to SEND is probably more usefull
    public Set<Mapping> map(Concept sourceConcept) {
        var targetVocabulary = targetVocabulary(sourceConcept);
        var snomed = toSnomed(sourceConcept);
        return snomed.stream().map(s -> mappingCache.snomedToPreclinical(s, targetVocabulary)).flatMap(Collection::stream).collect(toSet());
    }

    private Set<Mapping> toSnomed(Concept sourceConcept) {
        return mappingService.preclinicalToSnomed(sourceConcept);
    }

    private Set<Vocabulary.Identifier> targetVocabulary(Concept source) {
        return source.vocabulary().equals(Vocabulary.Identifier.HPATH) ? SEND : ETOX;
    }


}
