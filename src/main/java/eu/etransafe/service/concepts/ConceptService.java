package eu.etransafe.service.concepts;


import eu.etransafe.domain.Concept;
import eu.etransafe.domain.Domain;
import eu.etransafe.domain.Vocabulary;
import eu.etransafe.exception.RosettaException;
import eu.etransafe.repo.ConceptRepo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static eu.etransafe.domain.ConceptRelationship.Identifier.HAS_DIR_PROC_SITE;
import static eu.etransafe.domain.ConceptRelationship.Identifier.HAS_FINDING_SITE;
import static eu.etransafe.domain.ConceptRelationship.Identifier.MED_DRA_SNOMED_EQ;
import static eu.etransafe.domain.ConceptRelationship.Identifier.SNOMED_MED_DRA_EQ;
import static org.springframework.util.CollectionUtils.isEmpty;

@Service
@Slf4j
public class ConceptService {


    private final ConceptRepo conceptRepo;

    public ConceptService(ConceptRepo conceptRepo) {
        this.conceptRepo = conceptRepo;
    }

    public Optional<Concept> byId(Integer id) {
        return conceptRepo.findById(id);
    }

    public List<Concept> byClass(String conceptClassId) {
        return conceptRepo.findDistinctByConceptClass(conceptClassId);
    }

    public Concept byCode(String code, Set<Vocabulary.Identifier> vocabularies) {
        if (code == null || code.isBlank()) {
            return null;
        }
        var result = conceptRepo.findDistinctByCodeAndVocabularyIn(code, vocabularies);
        if (isEmpty(result)) {
            log.warn("Concept with code: [" + code + "] not found");
            return null;
        } else if (result.size() != 1) {
            throw new RosettaException("Found " + result.size() + " concepts for code: [" + code + "] when only one was expected");
        }
        return result.get(0);
    }

    public List<Concept> byCodes(List<String> codes, Set<Vocabulary.Identifier> vocabularies) {
        if (codes.isEmpty()) {
            return Collections.emptyList();
        }
        return conceptRepo.findDistinctByCodeInAndVocabularyIn(codes, vocabularies);
    }

    public Concept meddraByName(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        return byName(name, EnumSet.of(Vocabulary.Identifier.MEDDRA)).stream().findAny().orElse(null);
    }

    public Set<Concept> byName(String name, Set<Vocabulary.Identifier> vocabularies) {
        if (isEmpty(vocabularies)) {
            return conceptRepo.findDistinctByName(name);
        }
        return conceptRepo.findDistinctByNameAndVocabularyIn(name, vocabularies);
    }

    @Cacheable(value = "children")
    public List<Concept> children(Concept parent) {
        return conceptRepo.findChildrenById(parent.id());
    }

    @Cacheable(value = "parents")
    public List<Concept> parents(Concept concept) {
        return parents(concept, 1, 1, false);
    }


    public List<Concept> parents(Concept concept, Integer level, Integer maxLevels, boolean appendChild) {
        if (maxLevels == -1 || level <= maxLevels) {
            var parents = conceptRepo.findParentsById(concept.id());
            if (appendChild) {
                parents.forEach(parent -> parent.addChild(concept));
            }
            var tops = parents.stream()
                    .flatMap(parent -> {
                        if (appendChild) {
                            parent.level(level);
                        }
                        return parents(parent, level + 1, maxLevels, appendChild).stream();
                    }).toList();
            if (!isEmpty(tops)) {
                return tops;
            }
        }
        return List.of(concept);
    }

    public Set<Concept> findingSites(Concept concept) {
        return conceptRepo.findFindingSites(concept, EnumSet.of(HAS_FINDING_SITE, HAS_DIR_PROC_SITE));
    }

    public Set<Concept> normalize(String term, Set<Vocabulary.Identifier> vocabularies, boolean nonPreferred) {
        Set<Concept> concepts = new HashSet<>();
        concepts.addAll(byName(term, vocabularies));
        if (nonPreferred) {
            concepts.addAll(bySynonym(term, vocabularies));
        }
        var equalConcepts = conceptRepo.findEqualConcepts(concepts, vocabularies, EnumSet.of(MED_DRA_SNOMED_EQ, SNOMED_MED_DRA_EQ));
        concepts.addAll(equalConcepts);
        if (concepts.size() > 1) {
            var valid = concepts.stream().filter(c -> c.invalidReason() == null || c.invalidReason().isEmpty() || c.invalidReason().isBlank()).collect(Collectors.toSet());
            if (!isEmpty(valid)) {
                return valid;
            }
        }
        return concepts;
    }

    private Set<Concept> bySynonym(String term, Set<Vocabulary.Identifier> vocabularies) {
        return conceptRepo.findDistinctBySynonymName(term).stream().filter(c -> isEmpty(vocabularies) || vocabularies.contains(c.vocabulary())).collect(Collectors.toSet());
    }

    public List<String> wildcardSearchSynonyms(String query, Set<Domain> domains, Set<Vocabulary.Identifier> vocabularies, Integer limit, Integer offset, String conceptClass) {
        return conceptRepo.matchConceptSynonyms(query, Domain.convert(domains), Vocabulary.Identifier.convert(vocabularies), limit, offset, conceptClass);
    }

    public List<String> wildcardSearchNames(String query, Set<Domain> domains, Set<Vocabulary.Identifier> vocabularies, Integer limit, Integer offset, String conceptClass) {
        return conceptRepo.matchConceptNames(query, Domain.convert(domains), Vocabulary.Identifier.convert(vocabularies), limit, offset, conceptClass);
    }

    public int count(List<Concept> conceptTrees) {
        AtomicInteger count = new AtomicInteger(0);
        conceptTrees.forEach(parent -> {
            int cnt = count(parent);
            count.addAndGet(cnt);
        });
        return count.get();
    }

    private int count(Concept concept) {
        if (isEmpty(concept.children())) {
            return 1;
        }
        var count = new AtomicInteger(1);
        concept.children().forEach(child -> count.set(count.addAndGet(count(child))));
        return count.get();
    }

    @Cacheable(value = "conceptsByVocAndDom")
    public List<Concept> byVocabularyAndDomain(Vocabulary.Identifier vocabulary, Domain domain, boolean excludeUnmapped) {
        var domainString = domain == null ? "" : " for the [" + domain + "] domain";
        log.info("Loading concepts from the [{}] vocabulary{}", vocabulary, domainString);
        if (excludeUnmapped) {
            return conceptRepo.findAllByVocabularyAndDomainMappedForETransafe(vocabulary, domain);
        } else {
            return conceptRepo.findAllByVocabularyAndDomain(vocabulary, domain);
        }
    }

    public List<Concept> findingsForSite(Concept site) {
        return conceptRepo.findingSitesOf(site, EnumSet.of(HAS_FINDING_SITE, HAS_DIR_PROC_SITE));

    }

    public List<Concept> meddraByNameIn(Collection<String> names) {
        return conceptRepo.findAllByNameInAndVocabularyInAndConceptClassIn(names, EnumSet.of(Vocabulary.Identifier.MEDDRA), List.of("PT", "LLT"));
    }
}
