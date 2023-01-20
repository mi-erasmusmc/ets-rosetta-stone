package eu.etransafe.controller;

import eu.etransafe.controller.dto.LookupResponse;
import eu.etransafe.controller.dto.ToxHubOrganResponse;
import eu.etransafe.service.concepts.ConceptLookup;
import eu.etransafe.service.concepts.ConceptService;
import eu.etransafe.service.mappings.OrganService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

import static eu.etransafe.domain.Vocabulary.Identifier.HPATH;
import static eu.etransafe.domain.Vocabulary.Identifier.LABORATORY_TEST_NAME;
import static eu.etransafe.domain.Vocabulary.Identifier.MA;
import static eu.etransafe.domain.Vocabulary.Identifier.MEDDRA;
import static eu.etransafe.domain.Vocabulary.Identifier.SNOMED;
import static org.springframework.util.CollectionUtils.isEmpty;

@RestController
@CrossOrigin(origins = "*")
@RequestMapping(path = "/queryapp")
@Tag(name = "ToxHub Query App", description = "Custom endpoints for the eTransafe Query App")
public class QueryAppController {

    private final ConceptService conceptService;
    private final OrganService organs;
    private final ConceptLookup lookup;


    public QueryAppController(ConceptService conceptService, OrganService organs, ConceptLookup lookup) {
        this.conceptService = conceptService;
        this.organs = organs;
        this.lookup = lookup;
    }

    @Operation(summary = "Simple conversion between MA and SNOMED organs", description = """
            Supply a list of conceptCodes related to either MA terms and/or SNOMED anatomy terms.<br/>
            Endpoint returns a list of converted organs. Response provides no information about what organ mapped where.<br/>
            Algorithm from the mapping endpoint with MA2SNOMED / SNOMED2MA is used.
            """)
    @PostMapping("/organs")
    public List<ToxHubOrganResponse> convertOrgans(@RequestBody List<String> codes) {
        if (isEmpty(codes)) {
            return Collections.emptyList();
        }
        return conceptService.byCodes(codes, EnumSet.of(MA, SNOMED))
                .stream()
                .map(organ -> {
                    var targetVoc = organ.vocabulary().equals(MA) ? SNOMED : MA;
                    return organs.map(organ, targetVoc, 2);
                })
                .flatMap(Collection::stream)
                .map(ToxHubOrganResponse::fromMapping)
                .flatMap(Collection::stream)
                .toList();
    }

    @Operation(summary = "Preconfigured lookup function to do a finding search in the Query App", description = "Returns strings matching MedDRA PTs, HPATH or SEND LAB TESTS")
    @GetMapping("/lookup/findings")
    public LookupResponse getFindingLookup(@RequestParam String query) {
        List<String> terms = new ArrayList<>();
        if (query.length() < 4) {
            return new LookupResponse(Collections.emptyList(), null, null);
        } else {
            var meddra = lookup.lookup(query, EnumSet.of(MEDDRA), null, false, 1, 20, "PT");
            var preclinical = lookup.lookup(query, EnumSet.of(HPATH, LABORATORY_TEST_NAME), null, false, 1, 20, null);
            terms.addAll(meddra);
            terms.addAll(preclinical);
            var result = terms.stream().sorted().distinct().toList();
            return new LookupResponse(result, null, null);
        }
    }

}
