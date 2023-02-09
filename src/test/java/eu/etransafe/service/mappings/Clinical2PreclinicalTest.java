package eu.etransafe.service.mappings;

import eu.etransafe.domain.Concept;
import eu.etransafe.domain.Mapping;
import eu.etransafe.service.concepts.ConceptService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Comparator;
import java.util.Set;

import static eu.etransafe.domain.Vocabularies.CLINICAL;
import static eu.etransafe.domain.Vocabularies.ETOX;
import static eu.etransafe.domain.Vocabularies.SEND;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Slf4j
class Clinical2PreclinicalTest {

    @Autowired
    Clinical2Preclinical clinical2Preclinical;

    @Autowired
    ConceptService conceptService;

    @Autowired
    MappingService mappingService;

    @Test
    void mapComplex() {
        var c = conceptService.byCode("10031233", CLINICAL);
        var res = clinical2Preclinical.map(c, ETOX, false, 1);
        res.forEach(r -> System.out.println(r.explanationString()));
    }

    @Test
    void mapWithNulls() {
        var c = conceptService.byCode("10067737", CLINICAL);
        var res = clinical2Preclinical.map(c, ETOX, true, 1);
        res.forEach(r -> System.out.println(r.explanationString()));
    }

    @Test
    void mapLabTest() {
        var c = conceptService.byCode("10001546", CLINICAL);
        var res = clinical2Preclinical.map(c, SEND, true, 1);
        res.forEach(r -> System.out.println(r.explanationString()));
    }

    @Test
    void mapStomachMass() {
        var c = conceptService.byCode("10066792", CLINICAL);
        var res = clinical2Preclinical.map(c, SEND, true, 2);
        res.stream().sorted(Comparator.comparing((Mapping m) -> Math.abs(m.totalPenalty()))).forEach(r -> System.out.println(r.explanationString()));
    }

    @Test
    void mapHepatosplenomagaly() {
        var c = conceptService.byCode("10019847", CLINICAL);
        Set<Mapping> res = clinical2Preclinical.map(c, SEND, true, 3);
        res.stream().sorted(Comparator.comparing((Mapping m) -> Math.abs(m.totalPenalty()))).forEach(r -> System.out.println(r.explanationString()));
    }

    @Test
    void mapPotentialEternalMapping() {
        var c = conceptService.byCode("10081236", CLINICAL);
        Set<Mapping> res = clinical2Preclinical.map(c, SEND, true, 2);
        res.stream().sorted(Comparator.comparing((Mapping m) -> Math.abs(m.totalPenalty()))).forEach(r -> System.out.println(r.explanationString()));
    }

    @Test
    void mapPotentialEternalMapping2() {
        var c = conceptService.byCode("10058808", CLINICAL);
        Set<Mapping> res = clinical2Preclinical.map(c, SEND, true, 2);
        res.stream().sorted(Comparator.comparing((Mapping m) -> Math.abs(m.totalPenalty()))).forEach(r -> System.out.println(r.explanationString()));
    }


    @Test
    void mapHepatitisShouldHaveLiverPlusInflammationAsOnlyPerfectMapping() {
        var hepatitis = conceptService.byCode("10019717", CLINICAL);
        Set<Mapping> results = clinical2Preclinical.map(hepatitis, SEND, true, 2);
        var bestMapping = results.stream().filter(r -> r.totalPenalty() == 0).toList();
        // one mapping
        assertEquals(1, bestMapping.size());
        // two concepts
        assertEquals(2, bestMapping.get(0).toConcepts().size());
        // liver and inflammation
        assertTrue(bestMapping.get(0).toConcepts().stream().map(Concept::name).allMatch(n -> n.equalsIgnoreCase("liver") || n.equalsIgnoreCase("inflammation")));
    }

    @Test
    void mapNecrotisingUlcerativeGingivostomatitisShouldHaveMappingToThreeConcepts() {
        var necrotisingUlcerativeGingivostomatitis = conceptService.byCode("10055670", CLINICAL);
        Set<Mapping> results = clinical2Preclinical.map(necrotisingUlcerativeGingivostomatitis, SEND, true, 2);
        var bestMappings = mappingService.bestMappings(results);
                                            // Contains three-way mapping
        assertTrue(bestMappings.stream().anyMatch(m -> m.toConcepts().size() == 3));
    }

    @Test
    void mapPotentialEternalMapping3() {
        var c = conceptService.byCode("10065988", CLINICAL);
        Set<Mapping> res = clinical2Preclinical.map(c, SEND, true, 2);
        res.stream().sorted(Comparator.comparing((Mapping m) -> Math.abs(m.totalPenalty()))).forEach(r -> System.out.println(r.explanationString()));
    }
    


}
