package eu.etransafe.controller;


import eu.etransafe.controller.dto.ExpandResponse;
import eu.etransafe.controller.dto.LookupResponse;
import eu.etransafe.controller.dto.NormalizeResponse;
import eu.etransafe.domain.Concept;
import eu.etransafe.domain.Domain;
import eu.etransafe.domain.Vocabulary;
import eu.etransafe.service.concepts.ConceptExpander;
import eu.etransafe.service.concepts.ConceptLookup;
import eu.etransafe.service.concepts.ConceptService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@RestController
@CrossOrigin(origins = "*")
@RequestMapping(path = "/concepts")
@Tag(name = "Concepts", description = "Find, normalize and expand concepts")
public class ConceptsController {

    private final ConceptService conceptService;
    private final ConceptExpander expander;
    private final ConceptLookup lookup;

    public ConceptsController(ConceptService conceptService, ConceptExpander expander, ConceptLookup lookup) {
        this.conceptService = conceptService;
        this.expander = expander;
        this.lookup = lookup;
    }

    @GetMapping
    public List<Concept> getConcepts(@RequestParam(required = false) String conceptClass,
                                     @RequestParam(required = false) Vocabulary.Identifier vocabulary,
                                     @RequestParam(required = false) Domain domain,
                                     @RequestParam(required = false) Boolean excludeUnmapped) {
        if (conceptClass != null && conceptClass.equalsIgnoreCase("SMQ")) {
            return conceptService.byClass(conceptClass);
        } else if (vocabulary != null) {
            return conceptService.byVocabularyAndDomain(vocabulary, domain, excludeUnmapped).stream()
                    .sorted(Comparator.comparing(c -> c.name().toLowerCase()))
                    .toList();
        } else {
            throw new ResponseStatusException(BAD_REQUEST, "Please only search things we like, everything else will topple the db");
        }
    }

    @Operation(summary = "Get a concept by id")
    @GetMapping("/{id}")
    public Concept getConcept(@PathVariable Integer id) {
        return conceptService.byId(id).orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Concept with id: [" + id + "] not found"));
    }


    @GetMapping("/{id}/expand")
    public ExpandResponse getConceptExpand(@PathVariable Integer id, @RequestParam(required = false) Integer childlevels,
                                           @RequestParam(required = false) Integer parentlevels) {
        var concept = conceptService.byId(id).orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Concept with id: [" + id + "] not found"));
        var conceptTrees = expander.expand(concept, childlevels, parentlevels);
        int conceptCount = conceptService.count(conceptTrees);
        return new ExpandResponse(conceptTrees, childlevels, parentlevels, conceptCount);
    }

    @Operation(summary = "Lookup terms based on partial match", description = """
            Lookup terms that partially match given input, can be used for autocomplete functionality.<br/>
            Can supply filters and supports pagination.<br/>Includes some nice cleverness
            """)
    @GetMapping("/lookup")
    public LookupResponse getConceptLookup(@RequestParam String query,
                                           @RequestParam(required = false) Set<Vocabulary.Identifier> vocabularies,
                                           @RequestParam(required = false) Set<Domain> domains,
                                           @RequestParam(required = false) boolean nonpreferred,
                                           @RequestParam(required = false, defaultValue = "1") int page,
                                           @RequestParam(required = false, defaultValue = "25") int count,
                                           @RequestParam(required = false) String conceptClass) {
        List<String> terms;
        if (query.length() < 4) {
            terms = Collections.emptyList();
        } else {
            terms = lookup.lookup(query, vocabularies, domains, nonpreferred, page, count, conceptClass);
        }
        return new LookupResponse(terms, count, page);
    }

    @Operation(summary = "Convert a string to a concept", description = "Returns concepts (with code, vocabulary, domain etc.) for a given string, can filter by vocabulary.<br/>Supports pagination")
    @GetMapping("/normalize")
    public NormalizeResponse getConceptNormalize(@RequestParam String term,
                                                 @RequestParam(required = false) Set<Vocabulary.Identifier> vocabularies,
                                                 @RequestParam(required = false) boolean nonpreferred,
                                                 @RequestParam(required = false) Integer page,
                                                 @RequestParam(required = false) Integer count) {
        var c = conceptService.normalize(term, vocabularies, nonpreferred);
        return new NormalizeResponse(c, count, page);
    }

}
