package eu.etransafe.service.mappings;

import eu.etransafe.domain.Mapping;
import eu.etransafe.service.concepts.ConceptService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static eu.etransafe.domain.Vocabularies.ETOX;

@SpringBootTest
@Slf4j
class Hpath2SendTest {

    @Autowired
    Hpath2Send hpath2Send;

    @Autowired
    ConceptService conceptService;


    @Test
    void map() {
        var c = conceptService.byCode("MC:0000077", ETOX);
        var res = hpath2Send.map(c);
        res.stream().map(Mapping::explanationString).forEach(System.out::println);
    }

}