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

import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static eu.etransafe.domain.ConceptRelationship.Identifier.*;
import static eu.etransafe.domain.Domain.CONDITION;
import static eu.etransafe.domain.Domain.DRUG;
import static eu.etransafe.domain.Domain.MEASUREMENT;
import static eu.etransafe.domain.Domain.PROCEDURE;
import static eu.etransafe.domain.Domain.SPEC_ANATOMIC_SITE;
import static eu.etransafe.domain.Mapping.DESCR_TO_SINGLE_OR;
import static eu.etransafe.domain.Mapping.Direction.DOWNHILL;
import static eu.etransafe.domain.Mapping.Direction.UPHILL;
import static eu.etransafe.domain.Vocabularies.CLINICAL;
import static eu.etransafe.domain.Vocabularies.INTERMEDIARY;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toSet;
import static org.springframework.util.CollectionUtils.isEmpty;


@Slf4j
@Service
public class Preclinical2Clinical {

    public static final Set<Concept> BODY_STRUCTURE = Set.of(new Concept().id(40481827), new Concept().id(4034052), new Concept().id(4237366), new Concept().id(70002975), new Concept().id(70000004));
    private final ConceptService conceptService;
    private final MappingService mappingService;


    public Preclinical2Clinical(ConceptService conceptService, MappingService mappingService) {
        this.conceptService = conceptService;
        this.mappingService = mappingService;
    }

    public Set<Mapping> map(Concept sourceFinding, Concept sourceOrgan, int maxPenalty) {
        var source = getSource(sourceFinding, sourceOrgan);
        var sendOrgans = mappingService.preclinicalToSnomed(sourceOrgan);
        var sendFindings = mappingService.preclinicalToSnomed(sourceFinding);
        if (isEmpty(sendFindings)) {
            return emptySet();
        }
        var snomed = merge(sendOrgans, sendFindings, source);
        if (snomed.isEmpty()) {
            return emptySet();
        }
        var isLab = sourceFinding.vocabulary().equals(Vocabulary.Identifier.LABORATORY_TEST_NAME);
        var mappings = snomed.stream().map(s -> mapSnomedToMeddra(s, maxPenalty, isLab)).flatMap(Collection::stream).toList();
        return cleanUp(snomed, mappings);
    }

    private MappingItem getSource(Concept sourceFinding, Concept sourceOrgan) {
        Set<Concept> source = new HashSet<>(2);
        if (sourceOrgan != null) {
            source.add(sourceOrgan);
        }
        if (sourceFinding != null) {
            source.add(sourceFinding);
        }
        return new MappingItem(source);
    }

    private Set<Mapping> merge(Set<Mapping> organs, Set<Mapping> findings, MappingItem source) {
        // No need to merge if there is only one set
        if (findings.isEmpty()) {
            return emptySet();
        } else if (organs.isEmpty()) {
            if (source.concepts().size() != 1) {
                findings.forEach(f -> {
                    f.penalty(-Math.abs(f.penalty()) - 1);
                    f.description(f.description() + ". Could not map provided organ");
                });
            }
            return findings;
        } else {
            return doMerge(organs, findings, source);
        }
    }

    private Set<Mapping> doMerge(Collection<Mapping> one, Collection<Mapping> two, MappingItem preceding) {
        Set<Mapping> result = new HashSet<>();
        one.forEach(o -> two.forEach(t -> {
            var description = o.penalty() == 0 && t.penalty() == 0 ? "Exact match" : o.description() + " | " + t.description();
            var combinedToMapping = new HashSet<MappingItem>();
            o.to().forEach(oi -> t.to().forEach(ti -> {
                var combinedConcepts = new HashSet<>(oi.concepts());
                combinedConcepts.addAll(ti.concepts());
                combinedToMapping.add(new MappingItem(combinedConcepts));
            }));
            var map = new Mapping().to(combinedToMapping).from(preceding).description(description).penalty(o.penalty() + t.penalty());
            result.add(map);
        }));
        return result;
    }


    private Set<Mapping> cleanUp(Set<Mapping> fromSourceToSnomed, Collection<Mapping> mappings) {
        var mappingsToPt = new HashSet<Mapping>();
        mappings.stream()
                .map(this::lltToPt)
                .flatMap(Collection::stream)
                .collect(groupingBy(Mapping::to))
                .forEach((to, maps) -> {
                    var winner = mappingService.findOneBestMapping(maps);
                    mappingsToPt.add(winner);
                });

        if (mappingsToPt.isEmpty()) {
            var any = fromSourceToSnomed.stream().findAny().get();
            var reached = fromSourceToSnomed.stream().map(Mapping::to).flatMap(Collection::stream).map(MappingItem::humanReadableSimple).collect(Collectors.joining());
            var deadEnd = new Mapping().from(any.from()).description("Mapped source concepts to " + reached + " but failed to find MedDRA terms");
            return Set.of(deadEnd);
        }
        return mappingsToPt;
    }

    private Set<Mapping> mapSnomedToMeddra(Mapping fromSourceToSnomed, int maxPenalty, boolean isLab) {
        Set<Mapping> mappings = new HashSet<>();
        Map<Integer, Set<Integer>> options = new HashMap<>();

        var mappingsDirectFromSnomed = mapToMedDRA(fromSourceToSnomed);
        mappings.addAll(mappingsDirectFromSnomed);

        var mappingsWithSnomedTraversal = templateMapping(fromSourceToSnomed, options, isLab);
        mappings.addAll(mappingsWithSnomedTraversal);

        Map<Set<MappingItem>, Double> alreadyDone = new HashMap<>();
        var expandedResults = expandAndMap(fromSourceToSnomed, new HashSet<>(), null, maxPenalty, alreadyDone, options, isLab);
        mappings.addAll(expandedResults);
        fromSourceToSnomed.to().forEach(item -> reducedSet(fromSourceToSnomed, maxPenalty, mappings, options, alreadyDone, item, isLab));

        return mappings;
    }

    private void reducedSet(Mapping fromSourceToSnomed, int maxPenalty, Set<Mapping> mappings,
                            Map<Integer, Set<Integer>> options, Map<Set<MappingItem>, Double> alreadyDone, MappingItem item, boolean isLab) {
        if (item.size() > 2 && item.moreThanOneDomain()) {
            Set<Set<Concept>> combinations = Sets.combinations(item.concepts(), item.size() - 1);
            combinations.stream()
                    .filter(c -> c.stream().map(Concept::domain).distinct().count() == 2)
                    .forEach(reducedMapping -> {
                        var newItem = new MappingItem(reducedMapping);
                        var n = new Mapping().to(newItem)
                                .precedingMapping(fromSourceToSnomed)
                                .description("Removed AND mapping")
                                .penalty(1);
                        var tm = templateMapping(n, options, isLab);
                        mappings.addAll(tm);
                        var exp = expandAndMap(n, new HashSet<>(), null, maxPenalty, alreadyDone, options, isLab);
                        mappings.addAll(exp);
                        reducedSet(n, maxPenalty, mappings, options, alreadyDone, newItem, isLab);
                    });
        }
    }


    private Set<Mapping> expandAndMap(Mapping mapping, Set<Concept> exclude, Mapping.Direction direction,
                                      int maxPenalty, Map<Set<MappingItem>, Double> alreadyDone, Map<Integer, Set<Integer>> options, boolean isLab) {
        Set<Mapping> mappings = new HashSet<>();
        mapping.to().forEach(to -> to.concepts()
                .forEach(snomed -> {
                    if (!(DOWNHILL.equals(direction) && exclude.contains(snomed))) {
                        var parents = conceptService.parents(snomed);
                        mappings.addAll(mapExpansion(mapping, to, snomed, parents, UPHILL, maxPenalty, exclude, alreadyDone, options, isLab));
                    }
                    if (!(UPHILL.equals(direction) && exclude.contains(snomed))) {
                        var children = conceptService.children(snomed);
                        mappings.addAll(mapExpansion(mapping, to, snomed, children, DOWNHILL, maxPenalty, exclude, alreadyDone, options, isLab));
                    }
                }));
        return mappings;
    }

    private Set<Mapping> mapExpansion(Mapping inputMapping, MappingItem mappingItem, Concept snomed,
                                      List<Concept> relatives, Mapping.Direction direction, int maxPenalty,
                                      Set<Concept> exclude, Map<Set<MappingItem>, Double> alreadyDone, Map<Integer, Set<Integer>> options, boolean isLab) {
        Set<Mapping> mappings = new HashSet<>();
        relatives.forEach(p -> {
            Mapping expanded = createHierarchicTraversalMappingItem(inputMapping, mappingItem, snomed, p, direction);
            if (alreadyDone.getOrDefault(expanded.to(), maxPenalty + 1.0) > expanded.totalPenalty()) {
                alreadyDone.put(expanded.to(), expanded.totalPenalty());
                mappings.addAll(templateMapping(expanded, options, isLab));
                if (!p.domain().equals(SPEC_ANATOMIC_SITE)) {
                    mappings.addAll(mapToMedDRA(expanded));
                }
                if (Math.abs(expanded.totalPenalty()) < maxPenalty) {
                    exclude.add(p);
                    mappings.addAll(expandAndMap(expanded, exclude, direction, maxPenalty, alreadyDone, options, isLab));
                }
            }
        });
        return mappings;
    }

    private Mapping createHierarchicTraversalMappingItem(Mapping previous, MappingItem mappingItem, Concept
            snomed, Concept ancestor, Mapping.Direction direction) {
        Set<Concept> concepts = new HashSet<>();
        var remaining = mappingItem.concepts().stream()
                .filter(concept -> !concept.equals(snomed))
                .collect(toSet());
        concepts.addAll(remaining);
        concepts.add(ancestor);
        return new Mapping()
                .to(new MappingItem(concepts))
                .from(previous)
                .expanded(snomed, ancestor, direction);
    }

    private Set<Mapping> lltToPt(Mapping mapping) {
        Concept result = mapping.singleToConcept();
        if (result.conceptClass().equals("LLT")) {
            var parents = conceptService.parents(result);
            return parents.stream()
                    .map(parent -> new Mapping()
                            .from(mapping)
                            .to(parent)
                            .expanded(result, parent, UPHILL)
                            .penalty(0.1))
                    .collect(toSet());

        } else if (!result.conceptClass().equals("PT")) {
            return emptySet();
        }
        return Set.of(mapping);
    }

    private Set<Mapping> mapToMedDRA(Mapping toSnomed) {
        Set<Mapping> results = new HashSet<>();
        var orMappings = toSnomed.to();
        orMappings.forEach(mapping -> {
            var groupedOrgansAndRest = mapping.concepts().stream().collect(groupingBy(c -> c.domain().equals(SPEC_ANATOMIC_SITE)));
            var organs = groupedOrgansAndRest.getOrDefault(true, emptyList());
            var findings = groupedOrgansAndRest.getOrDefault(false, emptyList());
            findings.forEach(f -> {
                // These are WebRADR and UMLS mappings, for UMLS in theory the concepts are fully equal <-->
                var perfectMeddra = mappingService.map(f, Set.of(MAPS_TO, SNOMED_MED_DRA_EQ), CLINICAL, null);
                // If we have Clinical findings or test or observations then we don't care for organ and won't give negative penalties
                var noOrganRequired = !f.conceptClass().equals("Morph Abnormality") || organs.isEmpty();
                var organPenalty = organPenalty(f, organs);
                var penalty = noOrganRequired ? (findings.size() - 1 + organPenalty) : -(organPenalty + findings.size() - 1);
                results.addAll(perfectMeddra.stream().map(meddra -> meddraConceptToMapping(toSnomed, penalty, f, meddra)).toList());
                // Athena mapping was only conducted on way, WebRADR in other direction could be good enough
                var imperfectMeddra = mappingService.map(f, Set.of(MAPPED_FROM), CLINICAL, null).stream()
                        .filter(imp -> !perfectMeddra.contains(imp))
                        .toList();
                imperfectMeddra.stream().map(meddra -> meddraConceptToMapping(toSnomed, penalty, f, meddra)).forEach(m -> {
                    var increased = m.penalty() + 0.1;
                    m.penalty(increased);
                    m.description("Inverted MedDRA -> SNOMED mapping, may be imprecise");
                    results.add(m);
                });
            });
        });
        return results;
    }

    private long organPenalty(Concept c, List<Concept> organs) {
        if (organs.isEmpty()) {
            return 0;
        } else if (c.conceptClass().equals("Morph Abnormality")) {
            return organs.size();
        }
        var embeddedOrgans = mappingService.map(c, EnumSet.of(HAS_FINDING_SITE, HAS_DIR_PROC_SITE), INTERMEDIARY, EnumSet.of(SPEC_ANATOMIC_SITE));
        return organs.stream().filter(o -> !embeddedOrgans.contains(o)).count();
    }

    private Mapping meddraConceptToMapping(Mapping toSnomed, long penalty, Concept f, Concept meddra) {
        Mapping preceding;
        if (penalty == 0) {
            preceding = toSnomed;
        } else {
            preceding = new Mapping().precedingMapping(toSnomed).to(f).description("Remove AND mappings").penalty(penalty);
        }
        return new Mapping()
                .to(meddra)
                .precedingMapping(preceding)
                .description(MAPS_TO.value());
    }

    private Set<Mapping> templateMapping(Mapping fromSourceToSnomed, Map<Integer, Set<Integer>> options, boolean isLab) {
        Set<Mapping> results = new HashSet<>();
        Set<Concept> organs = getToOrgans(fromSourceToSnomed);
        fromSourceToSnomed.to().forEach(initialSnomed -> {
            // Don't bother with template mapping if already included clinical findings, because that is what we are looking for :-)
            if (initialSnomed.concepts().stream().noneMatch(c -> c.conceptClass().equals("Clinical Finding"))) {
                Set<Concept> snomedCombinationConcepts = mapFromMultiple(initialSnomed, options, isLab);
                Mapping preceding = precedingMapping(fromSourceToSnomed, initialSnomed);
                var isBodyStructureMapping = initialSnomed.concepts().stream().anyMatch(BODY_STRUCTURE::contains);
                for (Concept c : snomedCombinationConcepts) {
                    var extraSites = extraFindingSites(organs, c);
                    var penalty = extraSites.size();
                    var extraSitesExplanation = penalty == 0 ? "" :
                            ". Penalty for extra finding sites: " + extraSites.stream()
                                    .map(Concept::name)
                                    .collect(Collectors.joining(", "));
                    penalty = isBodyStructureMapping ? (-1 * penalty) - 1 : penalty;
                    var bodyStructureExplanation = penalty < 0 ? ". Negative score because entire body is not an organ" : "";
                    var cm = new Mapping()
                            .to(c)
                            .from(preceding)
                            .penalty(penalty)
                            .description(initialSnomed.concepts().stream()
                                    .map(Concept::domain)
                                    .distinct()
                                    .map(Domain::value)
                                    .sorted()
                                    .collect(Collectors.joining(" AND ")) + " TO " + c.domain().value() +
                                    extraSitesExplanation + bodyStructureExplanation);
                    results.addAll(mapToMedDRA(cm));
                }
            }
        });
        return results;
    }

    private Set<Concept> getToOrgans(Mapping fromSourceToSnomed) {
        return fromSourceToSnomed.to()
                .stream()
                .map(MappingItem::concepts)
                .flatMap(Collection::stream)
                .filter(c -> c.domain().equals(SPEC_ANATOMIC_SITE))
                .collect(toSet());
    }

    private List<Concept> extraFindingSites(Set<Concept> organs, Concept c) {
        return conceptService.findingSites(c)
                .stream()
                .filter(f -> !organs.contains(f))
                .filter(f -> !f.invalidReason().equalsIgnoreCase("U") && !f.invalidReason().equalsIgnoreCase("D"))
                .toList();
    }

    private Set<Concept> mapFromMultiple(MappingItem from, boolean isLab) {
        // Only one concept doing the mapping from multiple returns strange results, but for lab tests it is ok.
        if (from.size() == 1 && !isLab) {
            return emptySet();
        }
        return mappingService.map(from.concepts(),
                EnumSet.of(ASSO_MORPH_OF, FINDING_SITE_OF, DIR_PROC_SITE_OF, HAS_ASSO_MORPH, HAS_DIR_PROC_SITE,
                        HAS_FINDING_SITE, HAS_CAUSATIVE_AGENT, CAUSATIVE_AGENT_OF, PATHOLOGY_OF, OCCURRENCE_OF,
                        INTERPRETS_OF, COMPONENT_OF, DISPOSITION_OF),
                INTERMEDIARY, EnumSet.of(CONDITION, PROCEDURE, MEASUREMENT, DRUG));
    }

    private Set<Concept> mapFromMultiple(MappingItem from, Map<Integer, Set<Integer>> availableOptions, boolean isLab) {
        var ids = from.concepts().stream().map(Concept::id).toList();
        if (ids.isEmpty()) {
            return emptySet();
        }
        var possibleCombos = ids.stream()
                .map(availableOptions::get)
                .filter(Objects::nonNull)
                .findAny();
        if (possibleCombos.isPresent()) {
            var match = possibleCombos.get().containsAll(ids);
            return match ? mapFromMultiple(from, isLab) : emptySet();
        } else {
            var anyId = ids.get(0);
            var options = mappingService.findMappingOptionsSnomed(anyId);
            availableOptions.put(anyId, options);
            var match = options.containsAll(ids);
            return match ? mapFromMultiple(from, isLab) : emptySet();
        }
    }


    private Mapping precedingMapping(Mapping from, MappingItem to) {
        if (from.to().size() > 1) {
            return new Mapping()
                    .to(to)
                    .description(DESCR_TO_SINGLE_OR)
                    .from(from);
        }
        return from;
    }

}
