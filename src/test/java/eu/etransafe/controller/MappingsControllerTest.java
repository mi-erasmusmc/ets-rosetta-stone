package eu.etransafe.controller;

import eu.etransafe.controller.dto.BulkRequest;
import eu.etransafe.domain.MappingAlgorithm;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class MappingsControllerTest {

    @Autowired
    MappingsController controller;

    @Test
    void mapETOX2MEDDRA() {
        var resp = controller.map(List.of("MC:0000421", "MA:0000340"), MappingAlgorithm.ETOX2MEDDRAPT, false, 2);
        System.out.println(resp);
        assertFalse(resp.isEmpty());
    }

    @Test
    void mapBulk() {
        var list = List.of("MA:0000404", "MA:0000333");
        var resp = controller.bulk(new BulkRequest(MappingAlgorithm.MA2MEDDRASOC, list));
        System.out.println(resp);
        assertEquals(resp.size(), list.size());
    }

    @Test
    void mapBadRequest() {
        assertThrows(ResponseStatusException.class, () -> controller.map(List.of("this is not valid"), MappingAlgorithm.MEDDRAPT2SNOMED, false, 2));
    }

}
