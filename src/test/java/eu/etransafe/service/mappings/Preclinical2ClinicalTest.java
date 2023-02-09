package eu.etransafe.service.mappings;

import eu.etransafe.domain.Mapping;
import eu.etransafe.service.concepts.ConceptService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Collection;

import static eu.etransafe.domain.Vocabularies.ETOX;
import static eu.etransafe.domain.Vocabularies.SEND;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Slf4j
class Preclinical2ClinicalTest {

    @Autowired
    Preclinical2Clinical preclinical2Clinical;

    @Autowired
    ConceptService conceptService;

    @Autowired
    MappingService mappingService;


    @Test
    void mapBrainInflammationHasPenalty() {
        var cc = conceptService.byCode("C3137", SEND);
        var oc = conceptService.byCode("C12439", SEND);
        var result = preclinical2Clinical.map(cc, oc, 2);
        var bestScore = mappingService.bestScore(result);
        assertTrue(bestScore > 0);
    }

    @Test
    void mapLungMineralizationHasPenalty() {
        var cc = conceptService.byCode("C120899", SEND);
        var oc = conceptService.byCode("C12468", SEND);
        var result = preclinical2Clinical.map(cc, oc, 2);
        var bestScore = mappingService.bestScore(result);
        assertTrue(bestScore > 0);
    }

    @Test
    void testWildCombinationShouldCompleteWithin60Seconds() {
        var now = System.currentTimeMillis();
        var f = conceptService.byCode("C120889", SEND);
        var o = conceptService.byCode("C77669", SEND);
        var result = preclinical2Clinical.map(f, o, 2);
        var isEmpty = result.stream().findAny().get().toConcepts().isEmpty();
        assertFalse(isEmpty);
        var finish = System.currentTimeMillis();
        assertTrue(finish - now < 60000);
    }

    @Test
    void testSkinUlcerShouldBePerfect() {
        var o = conceptService.byName("Skin", ETOX).stream().findAny().get();
        var f = conceptService.byName("Ulceration", ETOX).stream().findAny().get();
        var results = preclinical2Clinical.map(f, o, 2);
        var bestScore = mappingService.bestScore(results);
        assertEquals(0, bestScore);
        var bestMapping = mappingService.bestMappings(results);
        var hasInjectionSiteNecrosis = bestMapping.stream()
                .map(Mapping::toConcepts)
                .flatMap(Collection::stream)
                .anyMatch(c -> c.name().equalsIgnoreCase("Skin ulcer"));
        assertTrue(hasInjectionSiteNecrosis);
    }

    @Test
    void testInjectionSiteNecrosisShouldBePerfect() {
        var o = conceptService.byName("SITE, INJECTION", SEND).stream().findAny().get();
        var f = conceptService.byName("NECROSIS", SEND).stream().findAny().get();
        var results = preclinical2Clinical.map(f, o, 1);
        var bestScore = mappingService.bestScore(results);
        assertEquals(0, bestScore);
        var bestMapping = mappingService.bestMappings(results);
        var hasInjectionSiteNecrosis = bestMapping.stream()
                .map(Mapping::toConcepts)
                .flatMap(Collection::stream)
                .anyMatch(c -> c.name().equalsIgnoreCase("Injection site necrosis"));
        assertTrue(hasInjectionSiteNecrosis);
    }

    @Test
    void testEpididymititsShouldBePerfect() {
        var o = conceptService.byCode("C3137", SEND);
        var f = conceptService.byCode("C12328", SEND);
        var results = preclinical2Clinical.map(f, o, 2);
        var bestScore = mappingService.bestScore(results);
        assertEquals(0, bestScore);
        var bestMapping = mappingService.bestMappings(results);
        var hasEpididymitis = bestMapping.stream()
                .map(Mapping::toConcepts)
                .flatMap(Collection::stream)
                .anyMatch(c -> c.name().equalsIgnoreCase("Epididymitis"));
        assertTrue(hasEpididymitis);
    }
}
