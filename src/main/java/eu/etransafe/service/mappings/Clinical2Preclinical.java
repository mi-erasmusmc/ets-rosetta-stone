package eu.etransafe.service.mappings;

import com.google.common.collect.Sets;
import eu.etransafe.domain.Concept;
import eu.etransafe.domain.Domain;
import eu.etransafe.domain.Mapping;
import eu.etransafe.domain.MappingItem;
import eu.etransafe.domain.Vocabulary;
import eu.etransafe.service.concepts.ConceptService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static eu.etransafe.domain.Mapping.DESCR_TO_SINGLE_OR;
import static eu.etransafe.domain.Mapping.Direction.DOWNHILL;
import static eu.etransafe.domain.Mapping.Direction.UPHILL;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toSet;
import static org.springframework.util.CollectionUtils.isEmpty;

@Service
@Slf4j
public class Clinical2Preclinical {

    public static final Set<Concept> DO_NOT_EXPAND = Set.of(new Concept().id(40481827), new Concept().id(4034052), new Concept().id(4237366), new Concept().id(70002975), new Concept().id(70000004), new Concept().id(4175951));
    private final ConceptService conceptService;
    private final MappingService mappingService;
    private final MappingCache mappingCache;


    public Clinical2Preclinical(ConceptService conceptService, MappingService mappingService, MappingCache mappingCache) {
        this.conceptService = conceptService;
        this.mappingService = mappingService;
        this.mappingCache = mappingCache;
    }

    public Set<Mapping> map(Concept source, Set<Vocabulary.Identifier> targetVocabularies, boolean explain, int maxPenalty) {
        if (source == null) {
            log.error("Provided concept was null mapping clinical to preclinical");
            return emptySet();
        }
        log.debug("Mapping {} [{}] to {}. maxPenalty: {}", source.name(), source.code(), targetVocabularies, maxPenalty);
        List<Mapping> mappingsToSnomed = toSnomed(source);
        if (mappingsToSnomed.isEmpty()) {
            return Set.of(Mapping.noMapping(source));
        }

        Set<Mapping> result = new HashSet<>();
        Map<Set<Concept>, Double> alreadyDone = new HashMap<>();


        mappingsToSnomed.stream()
                .map(snomed -> mapSnomedToPreclinical(targetVocabularies, snomed, maxPenalty, alreadyDone))
                .flatMap(Collection::stream)
                .filter(m -> m.to() != null && !m.to().stream().allMatch(Objects::isNull))
                .map(this::splitOrToSingleMapping)
                .flatMap(Collection::stream)
                .map(this::reduceOverTwoItemsToPairs)
                .flatMap(Collection::stream)
                .filter(Objects::nonNull)
                .map(this::removeNulls)
                .filter(m -> m.to() != null && !m.to().stream().allMatch(Objects::isNull))
                .map(this::negativelyScoreOnlyOrgans)
                .collect(Collectors.groupingBy(Mapping::to))
                .forEach((to, maps) -> {
                    var winner = mappingService.findOneBestMapping(maps);
                    result.add(winner);
                });

        if (result.isEmpty()) {
            var deadEnd = new Mapping().from(source).description("Mapped source concepts to " + mappingsToSnomed.stream()
                    .map(Mapping::to)
                    .flatMap(Collection::stream)
                    .distinct()
                    .map(MappingItem::humanReadable)
                    .collect(Collectors.joining("  OR  ")) + " but failed to reach a suitable preclinical translation");
            return Set.of(deadEnd);
        }
        return explain ? result : mappingService.squash(source, result);
    }

    private Mapping negativelyScoreOnlyOrgans(Mapping mapping) {
        boolean shouldBeNegative = mapping.toConcepts().stream().allMatch(c -> c.domain().equals(Domain.SPEC_ANATOMIC_SITE));
        if (shouldBeNegative) {
            double negative = -mapping.penalty() - 1;
            mapping.penalty(negative);
        }
        return mapping;
    }

    // I don't know where these nulls are coming from, but they are wrecking havock in the UI
    private Mapping removeNulls(Mapping m) {
        var filtered = m.to().stream().filter(i -> i.concepts() != null && !i.concepts().isEmpty()).collect(toSet());
        m.to(filtered);
        return m;
    }

    private Set<Mapping> reduceOverTwoItemsToPairs(Mapping m) {
        var item = m.singleToMappingItem();
        Set<Mapping> reduced = new HashSet<>();
        reduced.add(m);
        if (item.size() > 2 && item.moreThanOneDomain()) {
            Set<Set<Concept>> combinations = Sets.combinations(item.concepts(), 2);
            combinations.stream()
                    .filter(c -> c.stream().map(Concept::domain).distinct().count() == 2)
                    .forEach(pair -> {
                        var n = new Mapping().to(new MappingItem(pair))
                                .precedingMapping(m)
                                .description("Reduced mapping of more than two concepts to a pair, for practical purposes");
                        reduced.add(n);
                    });
        }
        return reduced;
    }


    private Set<Mapping> splitOrToSingleMapping(Mapping r) {
        Set<Mapping> ors = new HashSet<>();
        if (r.to().size() > 1) {
            r.to().forEach(t -> {
                var m = new Mapping().to(t)
                        .description(DESCR_TO_SINGLE_OR)
                        .precedingMapping(r)
                        .penalty((r.to().size() - 1) * 0.1);
                ors.add(m);

            });
        } else {
            ors.add(r);
        }

        return ors;
    }

    private List<Mapping> toSnomed(Concept source) {
        List<Mapping> intermediate = new ArrayList<>();
        var direct = mappingCache.meddraToSNOMED(source);
        intermediate.addAll(direct);

        conceptService.children(source).forEach(meddra -> {
            var snomedItems = mappingCache.meddraToSNOMED(meddra);
            if (snomedItems != null && !snomedItems.isEmpty()) {
                var llt = new Mapping()
                        .from(source)
                        .to(meddra)
                        .expanded(source, meddra, Mapping.Direction.DOWNHILL)
                        .penalty(0.1);

                snomedItems.forEach(i -> {
                    i.precedingMapping(llt);
                    intermediate.add(i);
                });
            }
        });
        if (intermediate.isEmpty()) {
            return emptyList();
        }
        List<Mapping> result = new ArrayList<>();
        intermediate.stream()
                .map(this::splitOrToSingleMapping)
                .flatMap(Collection::stream)
                .collect(groupingBy(Mapping::to))
                .forEach((to, maps) -> {
                    var winner = mappingService.findOneBestMapping(maps);
                    result.add(winner);
                });
        return result;
    }

    private Set<Mapping> mapSnomedToPreclinical(Set<Vocabulary.Identifier> targetVocabularies, Mapping mappingToSnomed,
                                                int maxPenalty, Map<Set<Concept>, Double> alreadyDone) {
        log.debug("Mapping {}", mappingToSnomed.toConcepts().stream().map(Concept::string).collect(Collectors.joining(", ")));
        Set<Mapping> result = new HashSet<>();
        if (mappingToSnomed.totalPenalty() > maxPenalty) {
            return result;
        }
        var directMappings = directMapping(mappingToSnomed, targetVocabularies);
        if (!directMappings.isEmpty()) {
            result.addAll(directMappings);
        }

        var split = mappingService.splitSnomed(mappingToSnomed, targetVocabularies);
        if (!split.isEmpty()) {
            var indirectMappings = snomedPartsToPreclinical(split, targetVocabularies, alreadyDone);
            if (!indirectMappings.isEmpty()) {
                result.addAll(indirectMappings);
            }

            split.forEach(m -> {
                if (m.totalPenalty() < maxPenalty) {
                    // If we already have results and there are lots of concepts, we will skip expansion it can get a bit crazy with things like CLOVE syndrome (3 morph + 4 finding sites with many children)
                    if (!(result.size() > 5 && m.toConcepts().size() > 5)) {
                        var expMaps = expandAndMap(m, new HashSet<>(), null, maxPenalty, targetVocabularies, alreadyDone);
                        result.addAll(expMaps);
                    }
                }
            });
        }
        return result;
    }

    private Set<Mapping> snomedPartsToPreclinical(Set<Mapping> split, Set<Vocabulary.Identifier> vocabularies,
                                                  Map<Set<Concept>, Double> alreadyDone) {
        Set<Mapping> mappings = new HashSet<>();
        split.forEach(s -> s.to().forEach(item -> {
            var snomedConceptsGroupedByMappedOrNot = item.concepts().stream().collect(groupingBy(m -> mappingCache.isMappedToPreclinical(m, vocabularies)));
            List<Concept> removedItems = snomedConceptsGroupedByMappedOrNot.getOrDefault(false, emptyList());
            Set<Concept> mappableSnomed = new HashSet<>(snomedConceptsGroupedByMappedOrNot.getOrDefault(true, emptyList()));
            if (!mappableSnomed.isEmpty() && (alreadyDone.get(mappableSnomed) == null || alreadyDone.get(mappableSnomed) > s.totalPenalty())) {
                alreadyDone.put(mappableSnomed, s.totalPenalty());
                log.debug("Concepts in SNOMED split {}", s.toConcepts().stream().map(Concept::string).collect(Collectors.joining(", ")));

                // Only makes sense if we have the original set completely mappable
                if (removedItems.isEmpty()) {
                    var prefect = mappingCache.snomedToPreclinical(item.concepts(), s, vocabularies);
                    if (!isEmpty(prefect)) {
                        log.debug("Adding a perfect mapping");
                        mappings.addAll(prefect);
                    }
                }

                // If was one, then above perfect match would have been sufficient
                if (item.size() > 1) {
                    Mapping preceding = createPrecedingMapping(s, item, removedItems);
                    mappingCache.partial(mappableSnomed, preceding, vocabularies).forEach(p -> {
                        var mergers = merge(p, preceding);
                        mappings.addAll(mergers);
                    });
                    log.debug("mappableSnomed size {}", mappableSnomed.size());
                    List<List<Mapping>> individualConceptMappings = mappingCache.singleConcepts(mappableSnomed, preceding, vocabularies);
                    log.debug("Mapped {} individual SNOMED concepts to individual preclinical terms", individualConceptMappings.size());

                    if (mappableSnomed.size() > 1) {
                        individualConceptMappings.forEach(m -> m.forEach(inner -> {
                            var intermediate = new Mapping().precedingMapping(preceding).to(inner.from()).penalty(mappableSnomed.size() - 1.0).description("Removed AND mappings");
                            var singleItemMapping = new Mapping().precedingMapping(intermediate).to(inner.to()).description(inner.description()).penalty(inner.penalty());
                            log.debug("Adding a single mapping {}", singleItemMapping.singleToMappingItem().humanReadableSimple());
                            mappings.add(singleItemMapping);
                            mappingCache.partial(inner.from().stream().findAny().get().concepts(), intermediate, vocabularies).forEach(p -> {
                                var mergers = merge(p, preceding);
                                mappings.addAll(mergers);
                            });
                        }));
                    }
                    var acquisitions = merge(individualConceptMappings, preceding);
                    mappings.addAll(acquisitions);
                }
            }
        }));
        return mappings;
    }

    private Mapping createPrecedingMapping(Mapping s, MappingItem item, List<Concept> removedItems) {
        Mapping preceding;
        if (!removedItems.isEmpty()) {
            preceding = new Mapping().precedingMapping(s)
                    .to(new MappingItem(item.concepts().stream().filter(c -> !removedItems.contains(c)).collect(toSet())))
                    .penalty(removedItems.size())
                    .description("Removed AND mappings " + removedItems.stream().map(Concept::name).collect(Collectors.joining(", ")));
        } else {
            preceding = s;
        }
        return preceding;
    }

    private Mapping createMergedMapping(List<List<Mapping>> mappings, Mapping preceding) {
        var flattened = mappings.stream().flatMap(Collection::stream).toList();
        var penalty = flattened.stream().mapToDouble(Mapping::penalty).sum();
        var description = penalty == 0 ? "Exact match" : flattened.stream().map(Mapping::description).collect(Collectors.joining(" | "));
        var items = flattened.stream().map(Mapping::to).flatMap(Collection::stream).map(MappingItem::concepts).flatMap(Collection::stream).collect(toSet());
        var combinedItem = new MappingItem(items);
        return new Mapping().to(combinedItem).precedingMapping(preceding).description(description).penalty(penalty);
    }

    // Squash set of mappings into a single item, accounts for ands and ors and exact, broad, narrow
    private Set<Mapping> merge(List<List<Mapping>> mappings, Mapping preceding) {
        var singles = mappings.stream().filter(m -> m != null && !m.isEmpty()).allMatch(m -> m.size() == 1);
        Set<Mapping> result = new HashSet<>();
        if (singles) {
            result.add(createMergedMapping(mappings, preceding));
        } else {
            List<Mapping> multiple = mappings.stream().filter(m -> m != null && m.size() > 1).findAny().get();
            var excludingMultiple = mappings.stream().filter(m -> !m.equals(multiple)).toList();
            multiple.forEach(m -> {
                var copy = new ArrayList<>(excludingMultiple);
                copy.add(List.of(m));
                result.addAll(merge(copy, preceding));
            });
        }
        return result;
    }

    private Set<Mapping> directMapping(Mapping sts, Set<Vocabulary.Identifier> vocabularies) {
        return sts.to().stream()
                .map(mi -> mappingCache.snomedToPreclinical(mi.concepts(), sts, vocabularies))
                .flatMap(Collection::stream)
                .collect(toSet());
    }

    private Set<Mapping> expandAndMap(Mapping mapping, Set<Concept> exclude, Mapping.Direction direction,
                                      int maxPenalty,
                                      Set<Vocabulary.Identifier> targetVocabulary, Map<Set<Concept>, Double> alreadyDone) {
        log.debug("Expand and map {}", mapping.toConcepts().stream().map(Concept::string).collect(Collectors.joining(", ")));
        Set<Mapping> mappings = new HashSet<>();
        if (mapping.totalPenalty() < maxPenalty) {
            splitOrToSingleMapping(mapping)
                    .forEach(m -> m.singleToMappingItem().concepts()
                            .forEach(snomed -> {
                                if (!(DOWNHILL.equals(direction) && exclude.contains(snomed))) {
                                    var parents = conceptService.parents(snomed);
                                    if (parents.stream().noneMatch(DO_NOT_EXPAND::contains)) {
                                        mappings.addAll(mapExpansion(m, snomed, parents, UPHILL, maxPenalty, targetVocabulary, alreadyDone, exclude));
                                    }
                                }
                                if (!(UPHILL.equals(direction) && exclude.contains(snomed))) {
                                    if (!DO_NOT_EXPAND.contains(snomed)) {
                                        var children = conceptService.children(snomed);
                                        mappings.addAll(mapExpansion(m, snomed, children, DOWNHILL, maxPenalty, targetVocabulary, alreadyDone, exclude));
                                    }
                                }
                            }));
        }
        return mappings;
    }

    private Set<Mapping> mapExpansion(Mapping input, Concept expandedConcept, List<Concept> relatives,
                                      Mapping.Direction direction, int maxPenalty, Set<Vocabulary.Identifier> targetVoc,
                                      Map<Set<Concept>, Double> alreadyDone, Set<Concept> exclude) {
        log.debug("Map expansion of {}", expandedConcept.string());
        Set<Mapping> result = new HashSet<>();
        relatives.forEach(p -> {
            Mapping expanded = createHierarchicTraversalMappingItem(input, expandedConcept, p, direction);
            if (mappingCache.isMappedToPreclinical(p, targetVoc)) {
                var mappings = snomedPartsToPreclinical(Set.of(expanded), targetVoc, alreadyDone);
                result.addAll(mappings);
            }                                           // If we have already checked 120 expanded terms we will stop expanding, arbitrary number, it has been enough.
            if (expanded.totalPenalty() < maxPenalty && exclude.size() < 120) {
                exclude.add(p);
                var expandedMappings = expandAndMap(expanded, exclude, direction, maxPenalty, targetVoc, alreadyDone);
                result.addAll(expandedMappings);
            }
        });
        return result;
    }

    private Mapping createHierarchicTraversalMappingItem(Mapping precedingMapping, Concept
            conceptThatWasExpanded, Concept resultOfExpansion, Mapping.Direction direction) {
        Set<Concept> concepts = new HashSet<>();
        // remove concept that was expanded
        var remaining = precedingMapping.singleToMappingItem().concepts().stream()
                .filter(concept -> !concept.equals(conceptThatWasExpanded))
                .collect(toSet());
        concepts.addAll(remaining);
        // add result of concept expansion
        concepts.add(resultOfExpansion);
        return new Mapping()
                .to(new MappingItem(concepts))
                .from(precedingMapping)
                .expanded(conceptThatWasExpanded, resultOfExpansion, direction);
    }

}
