package eu.etransafe.controller;

import eu.etransafe.domain.Vocabulary;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.EnumSet;

import static eu.etransafe.domain.Vocabulary.Identifier.MEDDRA;


@SpringBootTest
class VocabulariesControllerTest {


    @Autowired
    VocabulariesController controller;


    @Test
    void testClinicalVocabularies() {
        var clinical = controller.getVocabularies(Vocabulary.Type.CLINICAL);
        Assertions.assertEquals(MEDDRA, clinical.get(0).id());
    }

    @Test
    void testPreclinicalVocabularies() {
        var preclinical = controller.getVocabularies(Vocabulary.Type.PRECLINICAL);
        Assertions.assertEquals(6, preclinical.size());
    }

    @Test
    void testAllVocabularies() {
        var all = controller.getVocabularies(Vocabulary.Type.ALL);
        System.out.println(all);
        // 'ILO' and 'Other' are not returned, that is why -2
        Assertions.assertEquals(EnumSet.allOf(Vocabulary.Identifier.class).size() - 2, all.size());
    }
    
}
