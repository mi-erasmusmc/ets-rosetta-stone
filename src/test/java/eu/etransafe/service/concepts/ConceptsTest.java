package eu.etransafe.service.concepts;

import eu.etransafe.domain.Concept;
import eu.etransafe.domain.Vocabulary;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class ConceptsTest {

    @Autowired
    ConceptService conceptService;


    @Test
    void testNormalizeAbdomen() {
        var response = conceptService.normalize("Abdomen", null, false);
        response.forEach(System.out::println);
        assertEquals(2, response.size());
    }

    @Test
    void testNormalizeAccesses() {
        var response = conceptService.normalize("Abscess(es)", null, true);
        System.out.println(response);
        assertEquals(1, response.size());
    }

    @Test
    void testNormalizeLiverEnzymes() {
        var response = conceptService.normalize("Enzymes - liver", null, true);
        var found = response.stream().map(Concept::name).peek(System.out::println).anyMatch(name -> name.contains("Liver enzymes"));
        assertTrue(found);
    }

    @Test
    void testNormalizeInflammation() {
        var response = conceptService.normalize("Inflammation", null, false);
        assertEquals(5, response.size());
    }

    @Test
    void testNormalizeHepaticFibrosisMedDRA() {
        var response = conceptService.normalize("Hepatic Fibrosis", EnumSet.of(Vocabulary.Identifier.MEDDRA), false);
        assertEquals(1, response.size());
    }

}
