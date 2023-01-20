package eu.etransafe.service.concepts;

import eu.etransafe.domain.Domain;
import eu.etransafe.domain.Pertaining;
import eu.etransafe.domain.Vocabulary;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toSet;
import static org.springframework.util.CollectionUtils.isEmpty;

@Slf4j
@Service
public class ConceptLookup {

    private final ConceptService conceptService;

    public ConceptLookup(ConceptService conceptService) {
        this.conceptService = conceptService;
    }


    public List<String> lookup(String query, Set<Vocabulary.Identifier> vocabularies, Set<Domain> domains,
                               boolean nonPreferred, int page, int count, String conceptClass) {
        int offset = (page - 1) * count;
        return lookup(query, vocabularies, domains, nonPreferred, count, offset, count, conceptClass);
    }

    public List<String> lookup(String query, Set<Vocabulary.Identifier> vocabularies, Set<Domain> domains,
                               boolean nonPreferred, int count, int offset, int originalCount, String conceptClass) {

        String[] searchTerms = query.split("\\s");

        String fixedTerm = fixTerms(searchTerms);
        fixedTerm = addPertaining(query, fixedTerm);

        // The match syntax does not work on words under 4 chars therefore we add some additional logic.
        Set<String> shortWords = shortWords(searchTerms);

        List<String> result = nonPreferred ?
                conceptService.wildcardSearchSynonyms(fixedTerm, domains, vocabularies, count, offset, conceptClass) :
                conceptService.wildcardSearchNames(fixedTerm, domains, vocabularies, count, offset, conceptClass);

        if (isEmpty(shortWords)) {
            return result;
        } else {
            List<String> filtered = result.stream()
                    .filter(t -> shortWords.stream().anyMatch(sw -> t.toLowerCase().contains(sw)))
                    .limit(originalCount)
                    .toList();
            if (result.size() == count && filtered.size() < originalCount && result.size() != filtered.size()) {
                // Not solving the offset issue but well whatever...
                log.warn("Making lookup query again due to short words issue");
                return lookup(query, vocabularies, domains, nonPreferred,
                        count + (result.size() - filtered.size()), offset, originalCount, conceptClass);
            }
            return filtered;
        }
    }

    private String addPertaining(String query, String fixedTerm) {
        StringBuilder sb = new StringBuilder();
        String lowerQuery = query.toLowerCase();
        Pertaining.TERM_PERTAININGS.forEach((k, v) -> {
            if (lowerQuery.contains(k)) {
                String fixedSynonym = Arrays.stream(lowerQuery.split("\\s")).map(t -> "+" + t + "*").collect(Collectors.joining(" "));
                v.forEach(pert -> sb.append(" <(").append(fixedSynonym.replace(k, pert)).append(")"));
            }
        });

        /* attach pertaining to search */
        fixedTerm += sb.toString();
        return fixedTerm;
    }

    private Set<String> shortWords(String[] searchTerms) {
        return Arrays.stream(searchTerms)
                .map(String::toLowerCase)
                .filter(t -> t.length() < 3)
                .collect(toSet());
    }

    private String fixTerms(String[] searchTerms) {
        return Arrays.stream(searchTerms)
                .filter(t -> t.length() > 2)
                .map(t -> {
                    if (t.contains("-")) {
                        return "+\"" + t + "\"";
                    }
                    return "+" + t + "*";
                })
                .collect(Collectors.joining(" "));
    }
}
