package eu.etransafe.service.concepts;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class ConceptExpanderTest {

    @Autowired
    ConceptExpander expander;

    @Autowired
    ConceptService conceptService;


    @Test
    void testExpandHeadachePrimary() {
        var pt = conceptService.byId(35305453).orElseThrow();
        var soc = expander.expandMeddraPrimary(pt);
        System.out.println(soc.string());
        Assertions.assertEquals(35300000, soc.id());
    }

    @Test
    void testExpandHeadacheAll() {
        var pt = conceptService.byId(35305453).orElseThrow();
        var soc = expander.expand(pt, 0, 4);
        soc.forEach(s -> System.out.println(s.string()));
        Assertions.assertEquals(5, soc.size());
    }
}
