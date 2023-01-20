package eu.etransafe.service.concepts;

import eu.etransafe.domain.Domain;
import eu.etransafe.domain.Vocabulary;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Collections;
import java.util.Set;

import static eu.etransafe.domain.Vocabularies.CLINICAL;
import static eu.etransafe.domain.Vocabularies.PRECLINICAL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class ConceptLookupTest {

    @Autowired
    ConceptLookup conceptLookup;


    @Test
    void testNecrosisClinicalLookup() {
        var resp = conceptLookup.lookup("necros", CLINICAL, Collections.emptySet(), false, 1, 300, null);
        assertEquals(250, resp.size());
    }

    @Test
    void testTwoWordTerm() {
        var resp = conceptLookup.lookup("Electrocardiogram qt interval", CLINICAL, Collections.emptySet(), false, 1, 10, null);
        assertEquals(9, resp.size());
    }

    @Test
    void testNecrosisPreclinicalLookup() {
        var resp = conceptLookup.lookup("necros", PRECLINICAL, Collections.emptySet(), false, 1, 100, null);
        assertEquals(53, resp.size());
    }

    @Test
    void testInflammationPreclinicalLookup() {
        var resp = conceptLookup.lookup("inflammation", PRECLINICAL, Collections.emptySet(), false, 1, 20, null);
        assertEquals(18, resp.size());
    }

    @Test
    void testOrganLookup() {
        var resp = conceptLookup.lookup("Liver", Set.of(Vocabulary.Identifier.MA), Set.of(Domain.SPEC_ANATOMIC_SITE), false, 1, 20, null);
        assertEquals(20, resp.size());
        assertTrue(resp.contains("hepatic duct"));
    }

    @Test
    void testLiverLookup() {
        var resp = conceptLookup.lookup("Liver", null, Set.of(Domain.SPEC_ANATOMIC_SITE), false, 1, 20, null);
        assertTrue(resp.contains("liver"));
        assertTrue(resp.contains("hepatic duct"));
    }

    @Test
    void testHeartLookup() {
        var resp = conceptLookup.lookup("Heart", null, Set.of(Domain.SPEC_ANATOMIC_SITE), false, 1, 100, null);
        assertTrue(resp.contains("heart"));
        assertTrue(resp.contains("Cardiac plexus"));
        // non-pref term
        assertFalse(resp.contains("Heart/BW"));
    }


    @Test
    void testNonPrefLookup() {
        var resp = conceptLookup.lookup("Heart", null, Set.of(Domain.SPEC_ANATOMIC_SITE), true, 1, 100, null);
        resp.forEach(System.out::println);
        assertTrue(resp.contains("Small cardiac vein"));
        assertTrue(resp.contains("Heart/BW"));
    }

    @Test
    void testTwoCharacterLookup() {
        var resp = conceptLookup.lookup("He", null, null, false, 1, 100, null);
        assertTrue(resp.isEmpty());
    }


    @Test
    void testThyroidLookupContainsDrugWithoutDomainFilter() {
        var response = conceptLookup.lookup("thyroid", Set.of(Vocabulary.Identifier.MA, Vocabulary.Identifier.SNOMED), null, false, 1, 100, null);
        assertTrue(response.contains("Thyroiditis"));
    }

    @Test
    void testThyroidLookupDoesNotContainDrugs() {
        var response = conceptLookup.lookup("thyroid", Set.of(Vocabulary.Identifier.MA, Vocabulary.Identifier.SNOMED), Set.of(Domain.SPEC_ANATOMIC_SITE), false, 1, 100, null);
        assertFalse(response.contains("Thyroiditis"));
    }

    @Test
    void testHypen() {
        var response = conceptLookup.lookup("drug-induced liver", Set.of(Vocabulary.Identifier.MEDDRA), null, false, 1, 100, null);
        assertTrue(response.contains("Drug-induced liver injury"));
    }

}
