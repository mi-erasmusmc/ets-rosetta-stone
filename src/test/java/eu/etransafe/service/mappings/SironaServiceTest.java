package eu.etransafe.service.mappings;

import eu.etransafe.controller.SironaController;
import eu.etransafe.controller.dto.SironaRequest;
import eu.etransafe.domain.ToxHubFinding;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

@SpringBootTest
class SironaServiceTest {

    @Autowired
    SironaService sironaService;

    @Autowired
    SironaController controller;


    @Test
    void testExpand() {
        var input = List.of("Hypotension", "Apoptosis", "Thrombocytopenia", "18q minus syndrome",
                "Nephrocalcinosis", "Cough", "Pseudofolliculitis");
        var resp = sironaService.bulkExpand(input);
        resp.forEach(er -> {
            er.concepts().forEach(c -> System.out.println(c.string()));
        });
        Assertions.assertEquals(input.size(), resp.size());
    }

    @Test
    void testClinical() {
        var request = List.of(
                new ToxHubFinding().finding("mineralization"),
                new ToxHubFinding().finding("inflammation").organ("lung"),
                new ToxHubFinding().finding("extramedullary hematopoiesis").organ("spleen")
        );
        controller.getHPATH2MeddraMapping(new SironaRequest().findings(request)).forEach(System.out::println);
    }

    @Test
    void testMeddraToETox() {
        var request = List.of(
                new ToxHubFinding().finding("Nephrocalcinosis"),
                new ToxHubFinding().finding("Thrombocytopenia"),
                new ToxHubFinding().finding("Haemorrhage"),
                new ToxHubFinding().finding("Cough"),
                new ToxHubFinding().finding("Apoptosis"),
                new ToxHubFinding().finding("Atrioventricular block second degree"),
                new ToxHubFinding().finding("Pseudofolliculitis")
        );
        var resp = controller.getEToxMapping(new SironaRequest().findings(request));
        resp.forEach(System.out::println);
    }

    @Test
    void testCrazyQuery() {
        var request = List.of(
                new ToxHubFinding().finding("Oculomucocutaneous syndrome")
        );
        var resp = controller.getEToxMapping(new SironaRequest().findings(request));
        resp.forEach(System.out::println);
    }

    @Test
    void testUrinalisys() {
        var request = List.of(
                new ToxHubFinding().finding("Cholesterol").observation("decreased"),
                new ToxHubFinding().finding("Aspartate aminotransferase").observation("increased"),
                new ToxHubFinding().finding("Calcium").observation("decreased"),
                new ToxHubFinding().finding("Potassium"),
                new ToxHubFinding().finding("Sodium"),
                new ToxHubFinding().finding("Chloride"),
                new ToxHubFinding().finding("Gamma globulin").observation("increased")

        );
        var resp = controller.getUrinalysis2MeddraMapping(new SironaRequest().findings(request));
        resp.forEach(System.out::println);
    }

    @Test
    void testClinicalChemistry() {
        var body = new SironaRequest().findings(List.of(
                new ToxHubFinding().finding("Cholesterol").observation("decreased"),
                new ToxHubFinding().finding("Aspartate aminotransferase").observation("increased"),
                new ToxHubFinding().finding("Calcium").observation("decreased"),
                new ToxHubFinding().finding("Potassium"),
                new ToxHubFinding().finding("Sodium"),
                new ToxHubFinding().finding("Chloride"),
                new ToxHubFinding().finding("Gamma globulin").observation("increased")
        ));
        var resp = controller.getCC2MeddraMapping(body);
        resp.forEach(System.out::println);
    }

}
