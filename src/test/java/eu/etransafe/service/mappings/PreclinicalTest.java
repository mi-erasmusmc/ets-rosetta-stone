package eu.etransafe.service.mappings;

import eu.etransafe.domain.MappingItem;
import eu.etransafe.service.concepts.ConceptService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Collection;

import static eu.etransafe.domain.Vocabularies.CLINICAL;
import static eu.etransafe.domain.Vocabularies.ETOX;
import static eu.etransafe.domain.Vocabularies.SEND;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
class PreclinicalTest {

    @Autowired
    Clinical2Preclinical preclinical;
    @Autowired
    ConceptService conceptService;


    @Test
    void testAllergicHepatitis() {
        var c = conceptService.byCode("10071198", CLINICAL);
        var response = preclinical.map(c, SEND, false, 2);
        assertEquals(5, response.size());
    }

    @Test
    void testHepaticFibrosis() {
        var c = conceptService.byCode("10019668", CLINICAL);
        var response = preclinical.map(c, ETOX, false, 2);
        assertEquals(19, response.size());
    }

    @Test
    void testAmoebicColitis() {
        var c = conceptService.byCode("10001985", CLINICAL);
        var response = preclinical.map(c, SEND, false, 2);
        assertEquals(true, response.size() > 5);
    }

    @Test
    void testMeasurementReturnsNoOrgans() {
        var c = conceptService.byCode("10005362", CLINICAL);
        var response = preclinical.map(c, SEND, false, 2);
        int maxAmountOfConceptsInMappingResult = response.stream().mapToInt(r -> r.to().stream().map(MappingItem::concepts).flatMap(Collection::stream).toList().size()).max().orElseThrow();
        assertEquals(1, maxAmountOfConceptsInMappingResult);
    }

}
