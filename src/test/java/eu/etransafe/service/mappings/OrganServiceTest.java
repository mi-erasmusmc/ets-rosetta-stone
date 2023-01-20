package eu.etransafe.service.mappings;

import eu.etransafe.domain.Vocabulary;
import eu.etransafe.service.concepts.ConceptService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static eu.etransafe.domain.Vocabularies.CLINICAL;
import static eu.etransafe.domain.Vocabularies.ETOX;
import static eu.etransafe.domain.Vocabularies.INTERMEDIARY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class OrganServiceTest {

    @Autowired
    OrganService organs;

    @Autowired
    ConceptService conceptService;

    @Test
    void testSnomed() {
        var c = conceptService.byCode("21793004", INTERMEDIARY);
        var response = organs.map(c, Vocabulary.Identifier.MA, 4);
        assertTrue(response.size() > 0);
    }

    @Test
    void testMa() {
        var c = conceptService.byCode("MA:0000368", ETOX);
        var response = organs.map(c, Vocabulary.Identifier.SNOMED, 2);
        System.out.println(response);
        assertTrue(response.size() > 0);
    }


    @Test
    void testPtCodeToSnomed() {
        var c = conceptService.byCode("10081220", CLINICAL);
        var response = organs.fromPT(c);
        System.out.println(response);
        assertEquals(1, response.size());
    }

    @Test
    void testLungMaToSnomedNew() {
        var concept = conceptService.byCode("MA:0000415", ETOX);
        var res = organs.map(concept, Vocabulary.Identifier.SNOMED, 2);
        System.out.println(res);
        assertTrue(res.stream().findAny().get().to()
                .stream()
                .findAny().get().concepts().stream()
                .anyMatch(c -> c.name().equalsIgnoreCase("Lung structure")));
    }

    @Test
    void testUphillMaToTwoSnomedNew() {
        var c = conceptService.byCode("MA:0000524", ETOX);
        var res = organs.map(c, Vocabulary.Identifier.SNOMED, 2);
        res.forEach(r -> System.out.println(r.explanationString()));
        assertEquals(1, res.size());
    }

    @Test
    void testUphillMaToTwoNoExpSnomedNew() {
        var c = conceptService.byCode("MA:0000524", ETOX);
        var res = organs.map(c, Vocabulary.Identifier.SNOMED, 4);
        res.forEach(r -> System.out.println(r.explanationString()));
        assertEquals(1, res.size());
    }

    @Test
    void ptToOrgan() {
        var c = conceptService.byCode("10019211", CLINICAL);
        organs.fromPT(c).forEach(d -> System.out.println(d.explanationString()));
    }

}
