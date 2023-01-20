package eu.etransafe.controller;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Slf4j
class ConceptServiceControllerTest {

    @Autowired
    ConceptsController controller;

    @Test
    void testEdemaSNOMEDExpand() {
        var resp = controller.getConceptExpand(433595, 3, 0);
        assertEquals(1, resp.concepts().size());
        assertFalse(resp.concepts().get(0).children().isEmpty());
    }

    @Test
    void testCachexiaSNOMEDExpand() {
        var resp = controller.getConceptExpand(134765, 2, 0);
        assertEquals(1, resp.concepts().size());
        assertFalse(resp.concepts().get(0).children().isEmpty());
    }

    @Test
    void testCachexiaSNOMEDExpandHasChild() {
        var resp = controller.getConceptExpand(134765, 2, 0);
        assertNotNull(resp.concepts().get(0).children().get(0).domain());
    }

    @Test
    void testExpandUsingCacheForSlowRequests() {
        long start1 = System.currentTimeMillis();
        controller.getConceptExpand(4271678, 20, 20);
        long diff1 = (System.currentTimeMillis() - start1);
        log.info("First call {}", diff1);

        long start2 = System.currentTimeMillis();
        controller.getConceptExpand(4271678, 20, 20);
        long diff2 = (System.currentTimeMillis() - start2);
        log.info("Second call {}", diff2);

        assertTrue(diff1 > 500, diff1 + " should be greater than 500 milliseconds");
        assertTrue(diff2 < 100, diff2 + " should be smaller than 100 milliseconds");
    }

    @Test
    void testNotFound() {
        assertThrows(ResponseStatusException.class, () -> controller.getConceptExpand(1234567890, 2, -1));
    }
}
