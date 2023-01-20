package eu.etransafe.service.mappings;

import eu.etransafe.controller.dto.ExpandResponse;
import eu.etransafe.domain.Concept;
import eu.etransafe.domain.Mapping;
import eu.etransafe.domain.MappingItem;
import eu.etransafe.domain.SironaMapping;
import eu.etransafe.domain.ToxHubFinding;
import eu.etransafe.domain.Vocabularies;
import eu.etransafe.domain.Vocabulary;
import eu.etransafe.service.concepts.ConceptExpander;
import eu.etransafe.service.concepts.ConceptLookup;
import eu.etransafe.service.concepts.ConceptService;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static eu.etransafe.domain.Domain.MEASUREMENT;
import static eu.etransafe.domain.Domain.OBSERVATION;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;

@Service
public class SironaService {

    private static final int MAX_PENALTY = 2;
    private final ConceptService conceptService;
    private final ConceptExpander expander;
    private final Preclinical2Clinical mapping;
    private final Clinical2Preclinical eToxMapping;
    private final OrganService organs;
    private final ConceptLookup lookup;

    public SironaService(ConceptService conceptService, ConceptExpander expander, Preclinical2Clinical mapping,
                         Clinical2Preclinical eToxMapping, OrganService organs, ConceptLookup lookup) {
        this.conceptService = conceptService;
        this.expander = expander;
        this.mapping = mapping;
        this.eToxMapping = eToxMapping;
        this.organs = organs;
        this.lookup = lookup;
    }

    @Cacheable(value = "sironaFromLab")
    public SironaMapping clinicalFromClinicalChemistry(ToxHubFinding from, String liquid) {
        if (isEmpty(from.finding())) {
            return null;
        }

        var preferredLookupTerms = new ArrayList<String>(3);
        preferredLookupTerms.add(liquid + " " + from.finding() + " " + (isEmpty(from.observation()) ? "" : from.observation()));
        preferredLookupTerms.add(from.finding() + " " + (isEmpty(from.observation()) ? "" : from.observation()));
        for (String term : preferredLookupTerms) {
            var guesses = lookup.lookup(term, Vocabularies.CLINICAL, null, false, 1, 5, "PT");
            if (!guesses.isEmpty()) {
                var to = guesses.stream().map(g -> new ToxHubFinding().finding(g)).toList();
                return new SironaMapping(from, to);
            }
        }

        var regularMapping = clinicalFromHistopathology(from);
        if (!isEmpty(from.observation()) && regularMapping != null && regularMapping.to() != null && regularMapping.to().stream().anyMatch(findingContainsObservations(from))) {
            var correctDirection = regularMapping.to().stream().filter(findingContainsObservations(from)).toList();
            return new SironaMapping(from, correctDirection);
        } else {
            return regularMapping;
        }
    }

    private Predicate<ToxHubFinding> findingContainsObservations(ToxHubFinding from) {
        return f -> f.finding().toLowerCase().contains(from.observation().toLowerCase());
    }

    @Cacheable(value = "sironaFromHistopathology")
    public SironaMapping clinicalFromHistopathology(ToxHubFinding from) {
        if (isEmpty(from.finding()) || from.finding().equalsIgnoreCase("no abnormalities detected")) {
            return null;
        }
        var organ = findOrgan(from);
        var finding = findFinding(from.finding());
        Set<Mapping> res = new HashSet<>();
        if (finding != null) {
            res = mapping.map(finding, organ, MAX_PENALTY);
        }

        if (finding == null || res.isEmpty()) {
            var directTranslation = conceptService.meddraByName(from.finding());
            if (directTranslation != null) {
                return new SironaMapping(from, List.of(
                        new ToxHubFinding()
                                .finding(from.finding())
                                .organ(from.organ())));
            }
        }
        var end = sortAndFilter(res, 2);
        return SironaMapping.fromMappingsToMedDRA(end, from);
    }


    private List<Mapping> sortAndFilter(Collection<Mapping> maps, int maxPenalty) {
        final Predicate<Mapping> underThreeItems = m -> m.to().stream().findAny().orElse(new MappingItem(emptySet())).size() < 3;
        var mappings = maps.stream()
                // At present +0.4 is a feature not a bug ;-)
                .filter(m -> m.totalPenalty() < (maxPenalty + 0.4) && m.totalPenalty() > (-maxPenalty - 1))
                .sorted(Comparator.comparing(m -> Math.abs(m.totalPenalty())))
                .toList();
        boolean hasPositives = mappings.stream()
                .filter(m -> m.to() != null)
                .filter(underThreeItems)
                .anyMatch(m -> m.totalPenalty() >= 0);
        double minScore = mappings.stream()
                .filter(m -> m.to() != null)
                .filter(underThreeItems)
                .filter(m -> m.totalPenalty() >= 0)
                .mapToDouble(Mapping::totalPenalty)
                .min().orElse(0);
        double maxScore = mappings.stream()
                .filter(m -> m.to() != null)
                .filter(underThreeItems)
                .filter(m -> m.totalPenalty() <= 0)
                .mapToDouble(Mapping::totalPenalty)
                .max().orElse(0);
        double best = hasPositives ? minScore : maxScore;
        return mappings.stream()
                .filter(m -> m.to() != null)
                .filter(underThreeItems)
                .sorted(Comparator.comparing(m -> Math.abs(m.totalPenalty())))
                .filter(m -> m.totalPenalty() == best)
                .toList();
    }

    private Concept findOrgan(ToxHubFinding pair) {
        if (pair.organ() == null || pair.organ().isBlank()) {
            return null;
        }
        return conceptService.byName(pair.organ(), Vocabularies.PRECLINICAL_ORGANS)
                .stream()
                .findAny()
                .orElse(null);
    }

    private Concept findFinding(String finding) {
        return conceptService.byName(finding, Vocabularies.PRECLINICAL_FINDINGS)
                .stream()
                .findAny()
                .orElse(null);
    }


    public List<ExpandResponse> bulkExpand(List<String> terms) {
        List<ExpandResponse> resp = new ArrayList<>(terms.size());
        conceptService.meddraByNameIn(terms)
                .forEach(meddra -> {
                    var conceptTrees = List.of(expander.expandMeddraPrimary(meddra));
                    int conceptCount = conceptService.count(conceptTrees);
                    resp.add(new ExpandResponse(conceptTrees, 0, 4, conceptCount));
                });
        return resp;
    }

    @Cacheable(value = "sironaFromMeddra")
    public SironaMapping getETox(ToxHubFinding from) {
        var meddra = conceptService.meddraByName(from.finding());
        if (meddra == null) {
            return null;
        }
        List<Mapping> result = emptyList();
        int i = 1;
        while (result.isEmpty() && i <= MAX_PENALTY) {
            var voc = List.of(MEASUREMENT, OBSERVATION).contains(meddra.domain()) ? EnumSet.of(Vocabulary.Identifier.LABORATORY_TEST_NAME) : Vocabularies.ETOX;
            var m = eToxMapping.map(meddra, voc, false, i);
            if (m.size() == 1 && m.stream().findAny().get().description().startsWith("No mappings available for ")) {
                break;
            }
            if (!m.isEmpty()) {
                result = sortAndFilter(m, i);
            }
            i++;
        }

        if (result.isEmpty()) {
            result = mapToSingleEToxOrgan(meddra);
        }

        if (result != null && !result.isEmpty()) {
            var end = sortAndFilter(result, MAX_PENALTY);
            return SironaMapping.fromMappingsToETOX(end, from);
        }
        return null;
    }

    private List<Mapping> mapToSingleEToxOrgan(Concept meddra) {
        var m = organs.fromPT(meddra);
        if (!m.isEmpty() && !m.stream().findAny().get().to().isEmpty()) {
            var os = m.stream()
                    .map(Mapping::to)
                    .flatMap(Collection::stream)
                    .map(MappingItem::concepts)
                    .flatMap(Collection::stream)
                    .toList();
            var r = os.stream()
                    .map(o -> organs.map(o, Vocabulary.Identifier.MA, MAX_PENALTY))
                    .flatMap(Collection::stream)
                    .collect(Collectors.toSet());
            return sortAndFilter(r, MAX_PENALTY);
        }
        return emptyList();
    }

    private boolean isEmpty(String string) {
        return string == null || string.isEmpty() || string.isBlank();
    }
}
