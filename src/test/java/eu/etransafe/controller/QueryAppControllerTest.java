package eu.etransafe.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class QueryAppControllerTest {


    @Autowired
    QueryAppController controller;


    @Test
    void testTranslateOrgans() {
        var codes = List.of("MA:0000023", "MA:0000025", "10200004", "MA:0000358", "69536005", "66019005");
        var resp = controller.convertOrgans(codes);
        resp.forEach(System.out::println);
        assertEquals(8, resp.size());
    }

    @Test
    void testFindingLookup() {
        var result1 = controller.getFindingLookup("hypoka");
        assertTrue(result1.terms().contains("Hypokalaemia"));

        var result2 = controller.getFindingLookup("potassium");
        assertTrue(result2.terms().contains("Potassium"));
    }

    @Test
    void testFindingLookupHyphen() {
        var result1 = controller.getFindingLookup("drug-induced");
        assertTrue(result1.terms().contains("Drug-induced liver injury"));

        var result2 = controller.getFindingLookup("drug induced");
        assertTrue(result2.terms().contains("Drug-induced liver injury"));
    }


}
