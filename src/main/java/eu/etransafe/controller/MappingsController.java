package eu.etransafe.controller;

import eu.etransafe.controller.dto.BulkRequest;
import eu.etransafe.domain.Concept;
import eu.etransafe.domain.Mapping;
import eu.etransafe.domain.MappingAlgorithm;
import eu.etransafe.domain.MappingItem;
import eu.etransafe.domain.Vocabulary;
import eu.etransafe.exception.RosettaException;
import eu.etransafe.service.concepts.ConceptService;
import eu.etransafe.service.mappings.Clinical2Preclinical;
import eu.etransafe.service.mappings.Hpath2Send;
import eu.etransafe.service.mappings.MappingService;
import eu.etransafe.service.mappings.MeddraService;
import eu.etransafe.service.mappings.OrganService;
import eu.etransafe.service.mappings.Preclinical2Clinical;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static eu.etransafe.domain.MappingAlgorithm.ETOX2MEDDRAPT;
import static eu.etransafe.domain.MappingAlgorithm.MEDDRAPT2MEDDRASOC;
import static eu.etransafe.domain.MappingAlgorithm.SEND2MEDDRAPT;
import static eu.etransafe.domain.Vocabularies.CLINICAL;
import static eu.etransafe.domain.Vocabularies.ETOX;
import static eu.etransafe.domain.Vocabularies.ORGANS;
import static eu.etransafe.domain.Vocabularies.PRECLINICAL_FINDINGS;
import static eu.etransafe.domain.Vocabularies.PRECLINICAL_ORGANS;
import static eu.etransafe.domain.Vocabularies.SEND;
import static eu.etransafe.domain.Vocabulary.Identifier.MA;
import static eu.etransafe.domain.Vocabulary.Identifier.SNOMED;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.NOT_IMPLEMENTED;
import static org.springframework.util.CollectionUtils.isEmpty;

@Slf4j
@RestController
@CrossOrigin(origins = "*")
@RequestMapping(path = "/mappings")
@Tag(name = "Mappings", description = "Map between terminologies")
public class MappingsController {

    private final Preclinical2Clinical clinical;
    private final MeddraService meddraService;
    private final OrganService organs;
    private final Hpath2Send hpath2Send;
    private final Clinical2Preclinical clinical2Preclinical;
    private final MappingService mappingService;
    private final ConceptService conceptService;


    public MappingsController(Preclinical2Clinical clinical, MeddraService meddraService, OrganService organs, Hpath2Send hpath2Send,
                              Clinical2Preclinical clinical2Preclinical, MappingService mappingService, ConceptService conceptService) {
        this.clinical = clinical;
        this.meddraService = meddraService;
        this.organs = organs;
        this.hpath2Send = hpath2Send;
        this.clinical2Preclinical = clinical2Preclinical;
        this.mappingService = mappingService;
        this.conceptService = conceptService;
    }

    @GetMapping("")
    @Operation(summary = "Map between terminologies", description = """
            This endpoint allows to map between terminologies.<br/>Pass a list of conceptCodes, with either one or two codes,
            depending on whether you would like to translate a combination (e.g. the SEND concepts liver and necrosis)
            or a single term (e.g. the MedDRA concept Hepatocellular injury). Also pass the mapping algorithm to use
            for example SEND2MEDDRAPT to translate from SEND to MedDRA Preferred Term.
            """)
    public List<Mapping> map(
            @Parameter(description = """
                    List of concept codes to map from. Supply either one or two codes.Two codes are used to map a combination
                    of terms and is supported when mapping from preclinical terminologies (SEND and eTox) to clinical terminologies (MedDRA)
                    """) @RequestParam List<String> conceptCodes,
            @RequestParam MappingAlgorithm algorithm,
            @Parameter(description = """
                    When set to true the entire path that has been traversed to reach the mapping is returned,
                    when set to false only the result is provided.
                    """) @RequestParam(required = false) boolean explain,
            @Parameter(description = """
                    Maximum amount of traversals allowed to find results,
                    a higher value will result in more and less precise mappings.
                    Increasing the value will also lead to slower performance on the mapping
                    """) @RequestParam(required = false, defaultValue = "2") int maxPenalty) {
        var start = System.currentTimeMillis();
        Collection<Mapping> mappings = switch (algorithm) {
            case HPATH2SEND, SEND2HPATH, SEND2MA, MA2SEND -> preclinical2preclinical(conceptCodes, explain);
            case SEND2MEDDRAPT, ETOX2MEDDRAPT -> preclinical2clinical(conceptCodes, explain, maxPenalty);
            case MA2MEDDRASOC -> ma2soc(conceptCodes, maxPenalty, explain);
            case MEDDRAPT2MEDDRASOC -> meddraService.ptToPrimarySOC(conceptCodes);
            case MA2SNOMED -> mapOrgans(conceptCodes, SNOMED, explain, maxPenalty);
            case SNOMED2MA -> mapOrgans(conceptCodes, MA, explain, maxPenalty);
            case MEDDRAPT2ETOX -> clinical2preclinical(conceptCodes, explain, ETOX, maxPenalty);
            case MEDDRAPT2SEND -> clinical2preclinical(conceptCodes, explain, SEND, maxPenalty);
            case MEDDRAPT2SNOMED -> meddraPT2snomed(conceptCodes, explain);
        };
        var end = System.currentTimeMillis();
        log.info("Mapped {} to {} in {} milliseconds", conceptCodes, algorithm.name().split("2")[1], end - start);
        return sortAndFilter(mappings, maxPenalty);
    }

    @Operation(summary = "Do mappings in bulk", description = """
            Works the same as the regular /mapping endpoint but accepts a lists of individual codes to be mapped.
            This endpoint does not support the SEND2MEDDRAPT and ETOX2MEDDRAPT algorithms
            """)
    @PostMapping("/bulk")
    public List<Mapping> bulk(@RequestBody BulkRequest socMappingRequest) {
        var algorithm = socMappingRequest.algorithm();
        if (List.of(SEND2MEDDRAPT, ETOX2MEDDRAPT).contains(algorithm)) {
            throw new ResponseStatusException(NOT_IMPLEMENTED, "SEND2MEDDRAPT and ETOX2MEDDRAPT have not been implemented");
        }

        List<String> conceptCodes = socMappingRequest.conceptCodes();
        validate(conceptCodes);

        if (MEDDRAPT2MEDDRASOC.equals(socMappingRequest.algorithm())) {
            return meddraService.ptToPrimarySOC(conceptCodes);
        }

        return conceptCodes.stream()
                .distinct()
                .map(c -> map(List.of(c), algorithm, false, 2))
                .flatMap(Collection::stream)
                .toList();
    }

    private Set<Mapping> clinical2preclinical(List<String> conceptCodes, boolean explain,
                                              Set<Vocabulary.Identifier> voc, int maxPenalty) {
        var input = getSingleConcept(conceptCodes, CLINICAL);
        var result = clinical2Preclinical.map(input, voc, explain, maxPenalty);
        return explain ? result : mappingService.squash(input, result);
    }

    private Set<Mapping> preclinical2preclinical(List<String> conceptCodes, boolean explain) {
        var input = getSingleConcept(conceptCodes, PRECLINICAL_FINDINGS);
        var result = hpath2Send.map(input);
        return explain ? result : mappingService.squash(input, result);
    }

    private Set<Mapping> preclinical2clinical(List<String> conceptCodes, boolean explain, int maxPenalty) {
        if (conceptCodes.size() == 1 || conceptCodes.size() == 2) {
            var organ = getSourceOrgan(conceptCodes);
            Concept finding;
            try {
                finding = getSourceFinding(conceptCodes);
            } catch (RosettaException e) {
                throw new ResponseStatusException(BAD_REQUEST, e.getMessage());
            }
            var from = organ == null ? new MappingItem(Set.of(finding)) : new MappingItem(Set.of(finding, organ));
            Set<Mapping> result;
            result = clinical.map(finding, organ, maxPenalty);
            return explain ? result : mappingService.squash(Set.of(from), result);
        }
        throw new ResponseStatusException(BAD_REQUEST, "You must supply one or two conceptCodes, anything else I will not accept");
    }


    private Set<Mapping> mapOrgans(List<String> conceptCodes, Vocabulary.Identifier voc, boolean explain, int maxPenalty) {
        var input = getSingleConcept(conceptCodes, ORGANS);
        var result = organs.map(input, voc, maxPenalty);
        return explain ? result : mappingService.squash(input, result);
    }


    private Set<Mapping> meddraPT2snomed(List<String> conceptCodes, boolean explain) {
        var input = getSingleConcept(conceptCodes, CLINICAL);
        var result = organs.fromPT(input);
        if (!isEmpty(result)) {
            return explain ? result : mappingService.squash(input, result);
        } else {
            String msg = String.format("SNOMED not found for %s", conceptCodes.get(0));
            log.error(msg);
            throw new ResponseStatusException(NOT_FOUND, msg);
        }
    }


    private List<Mapping> ma2soc(List<String> conceptCodes, int maxPenalty, boolean explain) {
        var input = getSingleConcept(conceptCodes, EnumSet.of(MA));
        List<Mapping> response = meddraService.mouseAnatomyToSystemOrganClass(input, maxPenalty);
        if (!isEmpty(response)) {
            return explain ? response : new ArrayList<>(mappingService.squash(input, response));
        } else {
            return List.of(Mapping.noMapping(input));
        }
    }


    private void validate(List<String> conceptCodes) {
        if (isEmpty(conceptCodes)) {
            throw new ResponseStatusException(BAD_REQUEST, "Please provide conceptCodes");
        }
    }

    private List<Mapping> sortAndFilter(Collection<Mapping> mappings, int maxPenalty) {
        return mappings.stream()
                .sorted(Comparator.comparing(m -> Math.abs(m.totalPenalty())))
                // At present +0.9 is a feature when not many mappings are returned, not a bug ;-)
                .filter(m -> m.totalPenalty() < (maxPenalty + (mappings.size() > 10 ? 0 : 0.9)) && m.totalPenalty() > (-maxPenalty - 1))
                .toList();
    }

    private Concept getSingleConcept(List<String> codes, Set<Vocabulary.Identifier> vocabularies) {
        if (codes.size() != 1) {
            throw new ResponseStatusException(BAD_REQUEST, "Please supply exactly one conceptCode");
        } else {
            return conceptService.byCode(codes.get(0), vocabularies);
        }
    }

    private Concept getSourceOrgan(List<String> conceptCodes) {
        List<Concept> sourceOrgan = conceptService.byCodes(conceptCodes, PRECLINICAL_ORGANS);
        if (sourceOrgan.isEmpty()) {
            return null;
        } else if (sourceOrgan.size() != 1) {
            throw new RosettaException("More than one preclinical finding concept code supplied, this is not supported");
        }
        return sourceOrgan.get(0);

    }

    private Concept getSourceFinding(List<String> conceptCodes) {
        List<Concept> sourceFindings = conceptService.byCodes(conceptCodes, PRECLINICAL_FINDINGS);
        if (sourceFindings.isEmpty()) {
            log.warn("conceptCodes {}", conceptCodes);
            throw new RosettaException("No valid preclinical finding concept code supplied");
        } else if (sourceFindings.size() != 1) {
            throw new RosettaException("More than one preclinical finding concept code supplied, this is not supported");
        }
        return sourceFindings.get(0);
    }

}
