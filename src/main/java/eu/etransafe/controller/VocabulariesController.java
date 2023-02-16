package eu.etransafe.controller;

import eu.etransafe.domain.Vocabulary;
import eu.etransafe.repo.VocabularyRepo;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static eu.etransafe.domain.Vocabularies.CLINICAL;
import static eu.etransafe.domain.Vocabularies.INTERMEDIARY;
import static eu.etransafe.domain.Vocabularies.PRECLINICAL;

@RestController
@RequestMapping(path = "/vocabularies")
@CrossOrigin(origins = "*")
@Tag(name = "Vocabularies", description = "List vocabularies supported by the eTransafe Rosetta Stone")
public class VocabulariesController {

    private final VocabularyRepo repo;

    public VocabulariesController(VocabularyRepo repo) {
        this.repo = repo;
    }

    @GetMapping
    public List<Vocabulary> getVocabularies(@RequestParam(required = false, defaultValue = "ALL") Vocabulary.Type type) {
        var vocabularies = switch (type) {
            case PRECLINICAL -> PRECLINICAL;
            case CLINICAL -> CLINICAL;
            case INTERMEDIARY -> INTERMEDIARY;
            case ALL ->
                    Stream.of(PRECLINICAL, CLINICAL, INTERMEDIARY).flatMap(Collection::stream).collect(Collectors.toSet());
        };
        return repo.findByIds(vocabularies);
    }

}
