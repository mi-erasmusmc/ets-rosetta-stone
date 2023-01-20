package eu.etransafe.service.mappings;

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

@SpringBootTest
@Slf4j
class Clinical2PreclinicalTest {

    @Autowired
    Clinical2Preclinical clinical2Preclinical;

    @Autowired
    ConceptService conceptService;

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


}
