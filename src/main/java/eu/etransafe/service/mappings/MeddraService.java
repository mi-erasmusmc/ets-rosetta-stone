package eu.etransafe.service.mappings;


import eu.etransafe.domain.Concept;
import eu.etransafe.domain.Mapping;
import eu.etransafe.domain.MappingItem;
import eu.etransafe.domain.Vocabulary;
import eu.etransafe.service.concepts.ConceptService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static eu.etransafe.domain.Vocabulary.Identifier.SNOMED;
import static org.springframework.util.CollectionUtils.isEmpty;

@Service
public class MeddraService {

    private static final EnumSet<Vocabulary.Identifier> MEDDRA = EnumSet.of(Vocabulary.Identifier.MEDDRA);

    private final MappingService mappingService;
    private final OrganService organService;
    private final ConceptService conceptService;

    private MeddraService(MappingService mappingService, OrganService organService, ConceptService conceptService) {
        this.mappingService = mappingService;
        this.organService = organService;
        this.conceptService = conceptService;
    }

    private Set<Concept> toMeddra(Concept c) {
        return mappingService.map(c, null, MEDDRA, null);
    }

    public List<Mapping> mouseAnatomyToSystemOrganClass(Concept concept, int maxPenalty) {
        List<Mapping> resp = new ArrayList<>();
        var maToSnomed = organService.map(concept, SNOMED, maxPenalty);
        maToSnomed.stream()
                .filter(mts -> mts.to() != null)
                .forEach(mts -> mts.to().stream()
                        .map(MappingItem::concepts)
                        .flatMap(Collection::stream)
                        .forEach(site -> {
                            var findings = conceptService.findingsForSite(site);
                            var findingsExplanation = "Found " + findings.size() + " SNOMED findings with " + site.name() + " as finding site,";
                            var ids = findings.stream().map(this::toMeddra).flatMap(Collection::stream).map(Concept::id).toList();
                            var meddraExplanation = " translated these findings to " + ids.size() + " MedDRA terms.";
                            var map = mappingService.getSocsForMEDDRAs(ids, false);
                            List<Concept> socs = new ArrayList<>();
                            map.forEach((k, v) -> socs.addAll(v));
                            var winner = socs.stream().collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
                                    .entrySet()
                                    .stream()
                                    .max(Map.Entry.comparingByValue())
                                    .orElse(null);
                            if (winner != null) {
                                var explanation = " The most frequent SOC for these MedDRA terms occurred " + winner.getValue() + " times";
                                var res = new Mapping().from(mts).to(winner.getKey()).description(findingsExplanation + meddraExplanation + explanation);
                                resp.add(res);
                            }
                        }));
        return resp;
    }


    public List<Mapping> ptToPrimarySOC(List<String> conceptCodes) {
        List<Mapping> result = new ArrayList<>();
        if (isEmpty(conceptCodes)) {
            return result;
        }
        var concepts = conceptService.byCodes(conceptCodes, MEDDRA);

        // Former PTs might have been deprecated to LLTs, we will not bother end users with this and give them their socks
        var socs = lltToPrimarySoc(concepts);

        List<Integer> meddraIds = concepts.stream()
                .filter(c -> c.conceptClass().equals("PT"))
                .map(Concept::id)
                .distinct()
                .toList();
        Map<Integer, List<Concept>> ptSocs = mappingService.getSocsForMEDDRAs(meddraIds, true);
        socs.putAll(ptSocs);
        var ptToSoc = concepts.stream().map(source -> buildFromMap(socs, source)).toList();
        result.addAll(ptToSoc);
        return result;
    }

    private Map<Integer, List<Concept>> lltToPrimarySoc(List<Concept> concepts) {
        Map<Integer, List<Concept>> result = new HashMap<>();
        concepts.stream()
                .filter(c -> c.conceptClass().equals("LLT"))
                .forEach(llt -> {
                    var ptIds = conceptService.parents(llt).stream()
                            .filter(c -> c.conceptClass().equalsIgnoreCase("PT"))
                            .map(Concept::id).toList();
                    Map<Integer, List<Concept>> lltSocs = mappingService.getSocsForMEDDRAs(ptIds, true);
                    ptIds.forEach(i -> {
                        var soc = lltSocs.get(i);
                        result.put(llt.id(), soc);
                    });
                });
        return result;
    }

    private Mapping buildFromMap(Map<Integer, List<Concept>> ptSocs, Concept source) {
        var concepts = ptSocs.getOrDefault(source.id(), null);
        if (concepts == null) {
            return Mapping.noMapping(source);
        } else {
            var mi = concepts.stream()
                    .map(MappingItem::new)
                    .collect(Collectors.toSet());
            return new Mapping().from(source).to(mi);
        }
    }
}
