package eu.etransafe.service.mappings;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
class MeddraServiceTest {

    @Autowired
    MeddraService meddraService;


    @Test
    void testSocForTwoPtsReturnsTwoSOCS() {
        List<String> pt = List.of("10051222", "10066877", "10081580", "10081578");
        var resp = meddraService.ptToPrimarySOC(pt);
        assertEquals(4, resp.size());
        resp.forEach(r -> {
            System.out.println(r.toString());
            assertEquals(1, r.to().size());
            assertEquals("SOC", r.singleToConcept().conceptClass());
        });
    }

}
