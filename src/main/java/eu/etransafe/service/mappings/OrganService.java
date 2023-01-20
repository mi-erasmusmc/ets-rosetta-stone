package eu.etransafe.service.mappings;


import eu.etransafe.domain.Concept;
import eu.etransafe.domain.Domain;
import eu.etransafe.domain.Mapping;
import eu.etransafe.domain.MappingItem;
import eu.etransafe.domain.Vocabulary;
import eu.etransafe.service.concepts.ConceptService;
import eu.etransafe.util.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static eu.etransafe.domain.Mapping.Direction.UPHILL;
import static eu.etransafe.domain.Vocabularies.PRECLINICAL;
import static java.util.Collections.emptySet;
import static java.util.stream.Collectors.groupingBy;

@Service
@Slf4j
public class OrganService {

    private final MappingService mappingService;
    private final ConceptService conceptService;
    private final MappingCache mappingCache;

    private OrganService(final MappingService mappingService, ConceptService conceptService, MappingCache mappingCache) {
        this.mappingService = mappingService;
        this.conceptService = conceptService;
        this.mappingCache = mappingCache;
    }

    public Set<Mapping> map(Concept source, Vocabulary.Identifier targetVocabulary, int maxPenalty) {
        var mappings = map(source, targetVocabulary);
        if (mappings.isEmpty()) {
            var result = orMappingsToSeparateMappings(expandAndMap(source, targetVocabulary, null, maxPenalty));
            if (result.isEmpty()) {
                return Set.of(Mapping.noMapping(source));
            }
            return finalCleaning(result);
        } else {
            var unfiltered = orMappingsToSeparateMappings(mappings);
            var unclean = unfiltered.stream()
                    .map(m -> {
                        if (mapsToUnwantedConceptType(m)) {
                            var concept = m.singleToConcept();
                            return conceptService.parents(concept).stream()
                                    .filter(c -> c.domain().equals(Domain.SPEC_ANATOMIC_SITE))
                                    .filter(c -> !isUnwantedConceptType(c))
                                    .map(p -> new Mapping()
                                            .to(p)
                                            .precedingMapping(m)
                                            .description("Standardized to 'structure' concept"))
                                    .collect(Collectors.toSet());
                        } else {
                            return Set.of(m);
                        }
                    }).flatMap(Collection::stream)
                    .collect(Collectors.toSet());
            return finalCleaning(unclean);
        }
    }

    public Set<Mapping> finalCleaning(Set<Mapping> result) {
        Set<Mapping> best = new HashSet<>();
        result.stream()
                .collect(groupingBy(Mapping::to))
                .forEach((to, maps) -> {
                    var winner = mappingService.findOneBestMapping(maps);
                    best.add(winner);
                });
        return best;
    }

    private boolean mapsToUnwantedConceptType(Mapping mapping) {
        var concept = mapping.singleToConcept();
        return concept.vocabulary().equals(Vocabulary.Identifier.SNOMED) && isUnwantedConceptType(concept);
    }

    private boolean isUnwantedConceptType(Concept concept) {
        String name = concept.name().toLowerCase();
        return !name.contains("structure") && (name.contains("entire ") || name.contains(" entire") || name.contains("part ") || name.contains(" part"));
    }

    private Set<Mapping> orMappingsToSeparateMappings(Set<Mapping> mappings) {
        return mappings.stream()
                .map(m -> {
                    if (m.to().size() > 1) {
                        return m.to().stream()
                                .map(t -> new Mapping()
                                        .precedingMapping(m)
                                        .to(t)
                                        .description(Mapping.DESCR_TO_SINGLE_OR)
                                        .penalty(0.1))
                                .collect(Collectors.toSet());
                    } else {
                        return Set.of(m);
                    }
                }).flatMap(Collection::stream)
                .collect(Collectors.toSet());
    }

    public Set<MappingItem> removeAndRelations(Set<MappingItem> mappings) {
        return mappings.stream()
                .map(m -> m.concepts().stream()
                        .map(MappingItem::new)
                        .collect(Collectors.toSet()))
                .flatMap(Collection::stream)
                .collect(Collectors.toSet());
    }

    private Set<Mapping> expandAndMap(Concept source, Vocabulary.Identifier targetVocabulary, Mapping previous, int maxPenalty) {
        var parents = conceptService.parents(source);
        if (parents.contains(source)) {
            return emptySet();
        }
        Set<Mapping> mappings = new HashSet<>();
        for (Concept p : parents) {
            mappings.addAll(processParent(p, source, targetVocabulary, previous, maxPenalty));
        }
        // Figure out why empty mappings arrive here
        return mappings.stream()
                .filter(m -> m.to() != null && !m.to().isEmpty())
                .collect(Collectors.toSet());
    }

    private Set<Mapping> processParent(Concept parent, Concept source, Vocabulary.Identifier targetVocabulary, Mapping preceding, int maxPenalty) {
        if (preceding == null) {
            preceding = new Mapping().from(source).to(parent).expanded(source, parent, UPHILL);
        } else {
            preceding = new Mapping().precedingMapping(preceding).to(parent).expanded(source, parent, UPHILL);
        }
        var res = map(parent, targetVocabulary);
        if (!res.isEmpty()) {
            Mapping finalPreceding = preceding;
            res.forEach(m -> m.precedingMapping(finalPreceding));
            return res;
        } else if (preceding.totalPenalty() < maxPenalty) {
            return expandAndMap(parent, targetVocabulary, preceding, maxPenalty);
        }
        return emptySet();
    }

    private Set<Mapping> map(Concept concept, Vocabulary.Identifier vocabulary) {
        if (PRECLINICAL.contains(vocabulary)) {
            return mappingCache.snomedToPreclinicalItems(concept, vocabulary);
        } else {
            return mappingService.preclinicalToSnomed(concept);
        }
    }


    public Set<Mapping> fromPT(Concept concept) {
        if (concept == null) {
            return Collections.emptySet();
        }
        log.debug("Finding organs for {}", concept);

        Set<Mapping> results = new HashSet<>();

        mappingCache.meddraToSNOMED(concept).forEach(sourceToSnomed -> {
            var organs = getOrgansForSnomed(sourceToSnomed);
            if (!organs.isEmpty()) {
                results.addAll(organs);
            }
        });


        var organs = conceptService.byVocabularyAndDomain(Vocabulary.Identifier.SNOMED, Domain.SPEC_ANATOMIC_SITE, false);
        var stringMatch = stringMatch(concept, organs);
        if (stringMatch != null) {
            if (!results.isEmpty()) {
                // Prefer SNOMED traversal
                stringMatch.penalty(0.1);
            }
            results.add(stringMatch);
        }


        if (results.isEmpty()) {
            conceptService.parents(concept).forEach(parent -> {
                var parentString = stringMatch(parent, organs);
                if (parentString != null) {
                    var prev = new Mapping().from(concept).to(parent).expanded(concept, parent, UPHILL);
                    results.add(parentString.precedingMapping(prev));
                }
                mappingCache.meddraToSNOMED(parent).forEach(prev -> results.addAll(getOrgansForSnomed(prev)));
            });
        }

        if (results.isEmpty() && concept.conceptClass().equalsIgnoreCase("PT")) {
            conceptService.children(concept)
                    .forEach(child -> mappingCache.meddraToSNOMED(child)
                            .forEach(prev -> results.addAll(getOrgansForSnomed(prev))));
        }

        if (results.isEmpty()) {
            var socs = mappingService.getSocsForMEDDRAs(List.of(concept.id()), false).get(concept.id());
            if (socs != null) {
                socs.forEach(soc -> {
                    var prev = new Mapping().from(concept).to(soc).expanded(concept, soc, UPHILL);
                    var organ = organForSoc(soc);
                    if (organ != null) {
                        var end = new Mapping().precedingMapping(prev).to(organ);
                        results.add(end);
                    }
                });
            }
        }

        var toReturn = new HashSet<Mapping>();
        results.stream()
                .map(r -> r.to().stream().map(t -> {
                                    if (t.concepts().stream().anyMatch(this::isUnwantedConceptType)) {
                                        return t.concepts().stream().map(c -> {
                                                    if (isUnwantedConceptType(c)) {
                                                        return conceptService.parents(concept).stream()
                                                                .filter(p -> !isUnwantedConceptType(p))
                                                                .filter(p -> p.domain().equals(Domain.SPEC_ANATOMIC_SITE))
                                                                .map(p -> new Mapping()
                                                                        .to(p)
                                                                        .precedingMapping(r)
                                                                        .description("Standardized to 'structure' concept"))
                                                                .collect(Collectors.toSet());
                                                    } else {
                                                        return Set.of(new Mapping()
                                                                .to(c)
                                                                .precedingMapping(r)
                                                                .description("Removed non-'structure' concepts"));
                                                    }
                                                }).flatMap(Collection::stream)
                                                .collect(Collectors.toSet());
                                    }
                                    return Set.of(r);
                                })
                                .flatMap(Collection::stream)
                                .collect(Collectors.toSet())
                )
                .flatMap(Collection::stream)
                .collect(groupingBy(Mapping::to)).forEach((to, maps) -> {
                    var winner = mappingService.findOneBestMapping(maps);
                    toReturn.add(winner);
                });

        return toReturn;
    }

    private Mapping stringMatch(Concept concept, List<Concept> organs) {
        var match = organs.stream()
                .filter(o -> !StringUtils.isAllUpperCase(o.name()))
                .filter(o -> matches(concept, o))
                .max(Comparator.comparing((Concept c) -> c.name().length()))
                .orElse(null);

        if (match != null) {
            return new Mapping().from(concept).to(match).description("MedDRA term has Anatomic Structure in its name").penalty(0.1);
        }
        return null;
    }

    private boolean matches(Concept pt, Concept organ) {
        var ptName = pt.name().toLowerCase();
        var organName = organ.name().toLowerCase()
                .replace("structure of the ", "")
                .replace("structure of ", "")
                .replace(" structure", "")
                .trim();
        return ptName.endsWith(" " + organName) || ptName.contains(" " + organName + " ") || ptName.startsWith(organName + " ");
    }

    private Set<Mapping> getOrgansForSnomed(Mapping sourceToSnomed) {
        Set<Mapping> results = new HashSet<>();
        var snomed = sourceToSnomed.to();
        snomed.forEach(s -> log.debug("Mapped to {}", s.humanReadable()));
        var hasOrgan = containsOrgans(snomed);
        if (!hasOrgan) {
            snomed.forEach(items -> {
                var findingSites = items.concepts().stream().map(conceptService::findingSites).filter(fs -> !fs.isEmpty()).map(MappingItem::new).collect(Collectors.toSet());
                if (!findingSites.isEmpty()) {
                    findingSites.forEach(s -> log.debug(s.humanReadable()));
                    var m = new Mapping().from(sourceToSnomed).to(findingSites).description("Has finding site");
                    results.add(m);
                }
            });
        }
        return results;
    }

    private boolean containsOrgans(Set<MappingItem> snomed) {
        return snomed.stream()
                .map(MappingItem::concepts)
                .flatMap(Collection::stream)
                .anyMatch(c -> c.domain().equals(Domain.SPEC_ANATOMIC_SITE));
    }

    public Concept organForSoc(Concept soc) {
        Optional<Concept> optional = switch (soc.id()) {
            case 35200000 -> conceptService.byId(4217142);
            case 35400000 -> conceptService.byId(4037611);
            case 35600000 -> conceptService.byId(4305329);
            case 35700000 -> conceptService.byId(4046957);
            case 35900000 -> conceptService.byId(4009105);
            case 36000000 -> conceptService.byId(4021240);
            case 36500000 -> conceptService.byId(4095277);
            case 37000000 -> conceptService.byId(4271678);
            case 37100000 -> conceptService.byId(4076121);
            case 37300000 -> conceptService.byId(4132865);
            default -> Optional.empty();
        };
        return optional.orElse(null);
    }
}
