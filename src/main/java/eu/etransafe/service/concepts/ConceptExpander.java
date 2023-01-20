package eu.etransafe.service.concepts;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import eu.etransafe.domain.Concept;
import eu.etransafe.service.mappings.MappingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static org.springframework.util.CollectionUtils.isEmpty;

@Service
@Slf4j
public class ConceptExpander {


    public static final String SMQ = "SMQ";
    // Cache for slow expand responses, to keep memory use low, will only store very few requests
    // WARNING! Only use getIfPresent(), the get() method will automatically populate it with an empty response (due to the build() part)
    private static final LoadingCache<Integer, List<Concept>> expandCache = Caffeine.newBuilder()
            .maximumSize(32)
            .expireAfterWrite(10, TimeUnit.DAYS)
            .build(h -> new ArrayList<>());
    private final MappingService mappingService;
    private final ConceptService conceptService;

    public ConceptExpander(MappingService mappingService, ConceptService conceptService) {
        this.mappingService = mappingService;
        this.conceptService = conceptService;
    }

    @Cacheable(value = "expandMeddraPrimary")
    public Concept expandMeddraPrimary(Concept concept) {
        if (concept == null) {
            return null;
        }
        if (concept.conceptClass().equals("LLT")) {
            return processLLTForPrimaryExpand(concept);
        }
        Concept parent = mappingService.mapPrimaryParent(concept);
        if (parent == null) {
            log.warn("Received request for primary parent of {}, but parent could not be found", concept.string());
            return concept;
        }
        parent.addChild(concept);
        if (parent.conceptClass().equals("SOC")) {
            return parent;
        } else {
            return expandMeddraPrimary(parent);
        }
    }

    private Concept processLLTForPrimaryExpand(Concept concept) {
        List<Concept> parents = conceptService.parents(concept);
        if (parents.size() == 1) {
            var p = parents.get(0);
            p.addChild(concept);
            return expandMeddraPrimary(p);
        } else {
            log.warn("Expanding LLT {} we found {} parents, it should have exactly 1", concept.string(), parents.size());
            var p = parents.stream()
                    .filter(c -> c.conceptClass().equalsIgnoreCase("PT"))
                    .findAny().orElse(null);
            if (p != null) {
                p.addChild(concept);
                return expandMeddraPrimary(p);
            } else {
                return concept;
            }
        }
    }


    public List<Concept> expand(Concept concept, Integer childLevels, Integer parentLevels) {
        long start = System.currentTimeMillis();
        int requestHash = Objects.hash(concept, childLevels, parentLevels);
        var responseFromCache = expandCache.getIfPresent(requestHash);
        if (responseFromCache != null) {
            return responseFromCache;
        }

        List<Concept> conceptTrees;

        if (concept.conceptClass().equalsIgnoreCase(SMQ)) {
            concept.children(new ArrayList<>(mappingService.mapSmqToPts(concept)));
            conceptTrees = List.of(concept);
        } else {
            conceptTrees = doExpand(concept, childLevels, parentLevels);
        }

        // Put cycles that last over 0.5 second in cache
        if (System.currentTimeMillis() - start > 500) {
            expandCache.put(requestHash, conceptTrees);
        }
        return conceptTrees;
    }

    /*
     * parentLevels = null indicates all parent levels, parentLevels = 0 indicates no parentLevels
     */
    private List<Concept> doExpand(Concept concept, Integer childLevels, Integer parentLevels) {
        var conceptWithChildren = attachChildren(concept, childLevels);
        if (conceptWithChildren == null) {
            return Collections.emptyList();
        }
        return conceptService.parents(conceptWithChildren, 1, parentLevels, true);
    }

    private Concept attachChildren(Concept concept, Integer maxLevels) {
        List<Concept> result = getConceptByIds(List.of(concept), 1, maxLevels);
        return !isEmpty(result) ? result.get(0) : null;
    }

    private List<Concept> getConceptByIds(List<Concept> concepts, Integer level, Integer maxLevels) {
        List<Concept> result = new ArrayList<>();
        if (!isEmpty(concepts)) {
            concepts.forEach(concept -> {
                concept.level(level);
                if (maxLevels != null && level <= maxLevels) {
                    concept.children(getConceptByIds(conceptService.children(concept), level + 1, maxLevels));
                } else {
                    concept.children(new ArrayList<>());
                }
                result.add(concept);
            });
        }
        return result;
    }

}
