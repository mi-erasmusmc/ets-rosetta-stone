package eu.etransafe.service.mappings;

import eu.etransafe.domain.Mapping;
import eu.etransafe.service.concepts.ConceptService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Comparator;

import static eu.etransafe.domain.Vocabularies.SEND;

@SpringBootTest
@Slf4j
class Preclinical2ClinicalTest {

    @Autowired
    Preclinical2Clinical preclinical2Clinical;

    @Autowired
    ConceptService conceptService;


    @Test
    void mapBrainInflammationPenaltyHepatitis() {
        var cc = conceptService.byCode("C3137", SEND);
        var oc = conceptService.byCode("C12439", SEND);

        var explained = preclinical2Clinical.map(cc, oc, 2);
        explained.forEach(r -> System.out.println(r.explanationString()));

        var simple = preclinical2Clinical.map(cc, oc, 2);
        simple.stream()
                .sorted(Comparator.comparing(Mapping::totalPenalty))
                .forEach(cm -> cm.to()
                        .forEach(t -> System.out.println(t.humanReadable() + cm.totalPenalty())));

    }

    @Test
    void mapLungMineralization() {
        var cc = conceptService.byCode("C120899", SEND);
        var oc = conceptService.byCode("C12468", SEND);


        var explained = preclinical2Clinical.map(cc, oc, 2);
        explained.forEach(r -> System.out.println(r.explanationString()));

        var simple = preclinical2Clinical.map(cc, oc, 2);
        simple.stream()
                .sorted(Comparator.comparing(Mapping::totalPenalty))
                .forEach(cm -> cm.to()
                        .forEach(t -> System.out.println(t.humanReadable() + cm.totalPenalty())));

    }

    @Test
    void testWildCombination() {
        var f = conceptService.byCode("C120889", SEND);
        var o = conceptService.byCode("C77669", SEND);
        preclinical2Clinical.map(f, o, 2)
                .forEach(m -> System.out.println(m.explanationString()));

    }
}
