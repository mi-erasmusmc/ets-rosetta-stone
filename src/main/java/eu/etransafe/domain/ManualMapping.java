package eu.etransafe.domain;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@AllArgsConstructor
@ToString
@Getter
@EqualsAndHashCode
public class ManualMapping {

    MappingItem items;
    ConceptRelationship.Identifier predicate;
    @Setter
    String source;


    public ManualMapping(MappingItem items, ConceptRelationship.Identifier predicate) {
        this(items, predicate, "eTransafe");
    }

    public List<ManualMapping> get(Set<Concept> concepts) {
        Map<String, List<ManualMapping>> mappings = new HashMap<>();
        var key = concepts.stream().map(Concept::code).sorted().collect(Collectors.joining("-"));
        return mappings.getOrDefault(key, Collections.emptyList());
    }


}
