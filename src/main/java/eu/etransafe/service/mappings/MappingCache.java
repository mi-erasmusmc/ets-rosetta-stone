package eu.etransafe.service.mappings;

import eu.etransafe.domain.Concept;
import eu.etransafe.domain.ConceptRelationship;
import eu.etransafe.domain.ManualMapping;
import eu.etransafe.domain.Mapping;
import eu.etransafe.domain.MappingItem;
import eu.etransafe.domain.Vocabulary;
import eu.etransafe.repo.ConceptRelationshipRepo;
import eu.etransafe.service.concepts.ConceptService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static eu.etransafe.domain.ConceptRelationship.Identifier.EXACT;
import static eu.etransafe.domain.Vocabularies.CLINICAL;
import static eu.etransafe.domain.Vocabularies.ETOX;
import static eu.etransafe.domain.Vocabularies.INTERMEDIARY;
import static eu.etransafe.domain.Vocabularies.PRECLINICAL;
import static eu.etransafe.domain.Vocabularies.SEND;
import static eu.etransafe.domain.Vocabulary.Identifier.SNOMED;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toSet;
import static org.springframework.util.CollectionUtils.isEmpty;

@Service
@Slf4j
public class MappingCache {

    private static final Map<String, List<ManualMapping>> snomedToPreclinicalMap = new HashMap<>(2900);
    private static final Map<String, List<List<String>>> snomedToETOXPartialMap = new HashMap<>(140);
    private static final Map<String, List<List<String>>> snomedToSENDPartialMap = new HashMap<>(140);
    private static final Map<Concept, Set<Mapping>> preclincalToSnomedMap = new HashMap<>(3600);
    private static final Map<String, List<ManualMapping>> meddraToSnomedMap = new HashMap<>(39200);

    private final ConceptRelationshipRepo relationshipRepo;
    private final ConceptService conceptService;

    public MappingCache(ConceptRelationshipRepo relationshipRepo, ConceptService conceptService) {
        this.relationshipRepo = relationshipRepo;
        this.conceptService = conceptService;
    }

    @PostConstruct
    private synchronized void loadSnomedToPreclinical() {
        relationshipRepo.findAllMappingsTo(PRECLINICAL).stream()
                .collect(groupingBy(ConceptRelationship::invalidReason))
                .forEach((group, mappings) -> {
                    var description = mappings.get(0).relationshipId();
                    String key = key(mappings);
                    var items = new MappingItem(mappings.stream().map(ConceptRelationship::conceptTwo).collect(toSet()));
                    var mm = new ManualMapping(items, description);
                    var maps = snomedToPreclinicalMap.getOrDefault(key, new ArrayList<>());
                    maps.add(mm);
                    snomedToPreclinicalMap.put(key, maps);
                    if (key.contains("-")) {
                        mappings.forEach(part -> {
                            if (items.concepts().stream().anyMatch(c -> ETOX.contains(c.vocabulary()))) {
                                var repeat = snomedToETOXPartialMap.getOrDefault(part.conceptOne().code(), new ArrayList<>());
                                repeat.add(mappings.stream().map(ConceptRelationship::conceptOne).map(Concept::code).toList());
                                snomedToETOXPartialMap.put(part.conceptOne().code(), repeat);
                            }
                            if (items.concepts().stream().anyMatch(c -> SEND.contains(c.vocabulary()))) {
                                var repeat = snomedToSENDPartialMap.getOrDefault(part.conceptOne().code(), new ArrayList<>());
                                repeat.add(mappings.stream().map(ConceptRelationship::conceptOne).map(Concept::code).toList());
                                snomedToSENDPartialMap.put(part.conceptOne().code(), repeat);
                            }
                        });
                    }

                });
        log.info("{} items in the snomed to preclinical mapping map", snomedToPreclinicalMap.size());
        log.info("{} items in the SEND partial mapping map", snomedToSENDPartialMap.size());
        log.info("{} items in the ETOX partial mapping map", snomedToETOXPartialMap.size());

    }

    @PostConstruct
    private synchronized void loadPreclinicalToSnomed() {
        PRECLINICAL.forEach(voc -> {
            var concepts = conceptService.byVocabularyAndDomain(voc, null, true);
            concepts.forEach(concept -> {
                // Not per se efficient but we will let it be for now as it only takes 5 seconds to load everything
                var mapping = preclinicalToSnomedFromDB(concept);
                preclincalToSnomedMap.put(concept, mapping);
            });
        });
        log.info("Added {} items in the preclinical to SNOMED map", preclincalToSnomedMap.size());
    }

    public Set<Mapping> preclinicalToSnomed(Concept concept) {
        return preclincalToSnomedMap.getOrDefault(concept, emptySet());
    }

    public Set<Mapping> preclinicalToSnomedFromDB(Concept concept) {
        if (concept == null) {
            return emptySet();
        }
        Set<Mapping> result = new HashSet<>();
        EnumMap<ConceptRelationship.Identifier, Set<MappingItem>> items = new EnumMap<>(ConceptRelationship.Identifier.class);
        relationshipRepo.findMappings(List.of(concept), EnumSet.of(SNOMED))
                .stream()
                .collect(groupingBy(ConceptRelationship::invalidReason))
                .forEach((group, mappings) -> {
                    var rel = mappings.get(0).relationshipId();
                    var i = new MappingItem(mappings.stream().map(ConceptRelationship::conceptTwo).collect(toSet()));
                    var maps = items.getOrDefault(rel, new HashSet<>());
                    maps.add(i);
                    items.put(rel, maps);
                });
        items.forEach((pred, mappingItems) -> {
            var penalty = pred.equals(EXACT) ? 0 : 0.1;
            var description = concept.name() + " " + pred.value() + " to " + mappingItems.stream().map(MappingItem::humanReadableSimple).distinct().collect(Collectors.joining(" OR "));
            var m = new Mapping().from(concept).to(mappingItems).description(description).penalty(penalty);
            result.add(m);
        });
        return result;
    }


    @PostConstruct
    private synchronized void loadMedDRAToSnomed() {
        relationshipRepo.findAllMappingsFromTo(CLINICAL, INTERMEDIARY)
                .forEach(mapping -> {
                    var source = mapping.source() == null || mapping.source().isBlank() ? "Athena" : mapping.source();
                    var key = mapping.conceptOne().code();
                    var items = new MappingItem(mapping.conceptTwo());
                    var mm = new ManualMapping(items, mapping.relationshipId(), source);
                    var maps = meddraToSnomedMap.getOrDefault(key, new ArrayList<>());
                    if (!maps.isEmpty()) {
                        var optionalDupe = maps.stream().filter(manualMapping -> manualMapping.items().equals(items)).findAny();
                        if (optionalDupe.isPresent()) {
                            var firstSource = optionalDupe.get().source().replace(" and", ",");
                            optionalDupe.get().source(firstSource + " and " + source);
                        } else {
                            maps.add(mm);
                            meddraToSnomedMap.put(key, maps);
                        }
                    } else {
                        maps.add(mm);
                        meddraToSnomedMap.put(key, maps);
                    }
                });
        log.info("Loaded {} MedDRA terms for which we have a mapping to SNOMED", meddraToSnomedMap.size());
    }

    public List<Mapping> meddraToSNOMED(Concept meddra) {
        return meddraToSnomedMap.getOrDefault(meddra.code(), emptyList())
                .stream()
                .map(m -> new Mapping()
                        .from(meddra)
                        .to(m.items())
                        .description(m.predicate().value() + " (" + m.source() + ")"))
                .toList();
    }


    public List<Mapping> snomedToPreclinical(Set<Concept> snomed, Mapping preceding, Set<Vocabulary.Identifier> vocabularies) {
        String key = key(snomed);
        return snomedToPreclinicalMap.getOrDefault(key, emptyList())
                .stream()
                .filter(m -> m.items().concepts().stream().anyMatch(c -> vocabularies.contains(c.vocabulary())))
                .map(m ->
                        new Mapping()
                                .to(m.items())
                                .precedingMapping(preceding)
                                .description(snomed.stream().map(Concept::name).collect(Collectors.joining(" AND ")) + " " + m.predicate().value() + " to " + m.items().humanReadableSimple())
                                .penalty(!m.predicate().equals(ConceptRelationship.Identifier.EXACT) ? 0.1 : 0))
                .toList();
    }


    public List<Mapping> snomedToPreclinical(Mapping preceding, Set<Vocabulary.Identifier> vocabularies) {
        return snomedToPreclinical(preceding.toConcepts(), preceding, vocabularies);
    }

    public Set<Mapping> snomedToPreclinicalItems(Concept snomed, Vocabulary.Identifier vocabulary) {
        return snomedToPreclinicalMap.getOrDefault(snomed.code(), emptyList()).stream()
                .filter(m -> m.items().concepts().stream().anyMatch(c -> c.vocabulary().equals(vocabulary)))
                .map(m ->
                        new Mapping()
                                .to(m.items())
                                .from(snomed)
                                .description(snomed.name() + " " + m.predicate().value() + " to " + m.items().humanReadableSimple())
                                .penalty(!m.predicate().equals(ConceptRelationship.Identifier.EXACT) ? 0.1 : 0))
                .collect(toSet());
    }


    public boolean isMappedToPreclinical(Concept c, Set<Vocabulary.Identifier> vocabularies) {
        return (snomedToPreclinicalMap.containsKey(c.code()) &&
                snomedToPreclinicalMap.get(c.code()).stream()
                        .anyMatch(m -> m.items().concepts().stream()
                                .anyMatch(preclin -> vocabularies.contains(preclin.vocabulary())))) ||
                (vocabularies.equals(ETOX) ? snomedToETOXPartialMap.containsKey(c.code()) : snomedToSENDPartialMap.containsKey(c.code()));
    }


    // This is some mind-blowing stuff, tread with care
    public List<List<List<Mapping>>> partial(Collection<Concept> concepts, Mapping preceding, Set<Vocabulary.Identifier> vocabularies) {
        List<List<List<Mapping>>> result = new ArrayList<>();
        var partialMap = vocabularies.equals(ETOX) ? snomedToETOXPartialMap : snomedToSENDPartialMap;
        for (Concept c : concepts) {
            var hits = partialMap.get(c.code());
            if (hits != null) {
                hits.forEach(hit -> {
                    List<List<Mapping>> one = new ArrayList<>();
                    var grouped = concepts.stream().collect(groupingBy(cncpt -> hit.contains(cncpt.code())));
                    var matches = grouped.get(true);
                    var misses = grouped.get(false);
                    String key = key(hit.stream());
                    var deviation = hit.size() - matches.size();
                    one.add(snomedToPreclinicalMap.get(key)
                            .stream()
                            .filter(m -> m.items().concepts().stream().anyMatch(preclin -> vocabularies.contains(preclin.vocabulary())))
                            .filter(mm -> !(mm.items().size() == 1 && !mm.items().getSingleConcept().domain().equals(c.domain())))
                            .map(m ->
                                    new Mapping()
                                            .to(m.items())
                                            .precedingMapping(preceding)
                                            .description(c.name() + " " + m.predicate().value() + " to " + m.items().humanReadableSimple() + " neglecting " + deviation + " terms")
                                            .penalty((!m.predicate().equals(ConceptRelationship.Identifier.EXACT) ? 0.1 : 0) + deviation))
                            .toList());
                    result.add(one);
                });
            }
        }
        return result;
    }

    private String key(List<ConceptRelationship> mappings) {
        return key(mappings.stream()
                .map(ConceptRelationship::conceptOne)
                .map(Concept::code));
    }

    private String key(Set<Concept> snomed) {
        return key(snomed.stream().map(Concept::code));
    }

    private String key(Stream<String> stream) {
        return stream.sorted().collect(Collectors.joining("-"));
    }

    public List<List<Mapping>> singleConcepts(Collection<Concept> concepts, Mapping preceding, Set<Vocabulary.Identifier> vocabularies) {
        return concepts.stream()
                .map(miss -> preceding.isToSingleConcept() ? preceding : new Mapping().precedingMapping(preceding).to(miss).description("Get single concept"))
                .map(c -> snomedToPreclinical(c, vocabularies))
                .filter(l -> !isEmpty(l))
                .toList();
    }
}
