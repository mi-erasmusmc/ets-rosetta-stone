package eu.etransafe.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import eu.etransafe.exception.RosettaException;

import java.io.Serial;
import java.io.Serializable;
import java.util.Set;
import java.util.stream.Collectors;

public record MappingItem(Set<Concept> concepts) implements Serializable {
    @Serial
    private static final long serialVersionUID = 223076576L;

    public MappingItem(Concept concept) {
        this(Set.of(concept));
    }

    public String humanReadable() {
        return concepts().stream().map(Concept::string).collect(Collectors.joining(" AND "));
    }

    public String humanReadableSimple() {
        return concepts().stream().map(Concept::name).collect(Collectors.joining(" AND "));
    }

    public boolean moreThanOneDomain() {
        return concepts
                .stream()
                .map(Concept::domain)
                .distinct()
                .count() > 1;
    }

    @JsonIgnore
    public Concept getSingleConcept() {
        if (size() != 1) {
            throw new RosettaException("Found " + concepts.size() + " concepts in MappingItem when only 1 was expected");
        } else {
            return concepts.stream().findAny().orElseThrow(() -> new RosettaException("Could not find a concept when one was expected"));
        }
    }

    public int size() {
        return concepts().size();
    }
}
