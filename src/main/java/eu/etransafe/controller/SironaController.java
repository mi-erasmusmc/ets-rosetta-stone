package eu.etransafe.controller;


import eu.etransafe.controller.dto.ExpandResponse;
import eu.etransafe.controller.dto.SironaRequest;
import eu.etransafe.domain.SironaMapping;
import eu.etransafe.service.mappings.SironaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;

@RestController
@CrossOrigin(origins = "*")
@RequestMapping(path = "/sirona")
@Tag(name = "Sirona", description = "Custom endpoints for the eTransafe Sirona app")
@Slf4j
public class SironaController {

    private final SironaService sironaService;

    public SironaController(SironaService sironaService) {
        this.sironaService = sironaService;
    }


    @Operation(summary = "Map eTox histopathology to clinical")
    @PostMapping("/etox/histopathology")
    public List<SironaMapping> getHPATH2MeddraMapping(@RequestBody SironaRequest request) {
        var start = System.currentTimeMillis();
        var resp = request.findings()
                .stream()
                .distinct()
                .map(sironaService::clinicalFromHistopathology)
                .filter(Objects::nonNull)
                .filter(s -> s.to() != null && !s.to().isEmpty())
                .toList();
        var duration = System.currentTimeMillis() - start;
        log.info("Translated {} histopathology concepts for Sirona in {} millis", request.findings().size(), duration);
        return resp;
    }

    @Operation(summary = "Map eTox clinical chemistry to clinical")
    @PostMapping("/etox/clinicalchemical")
    public List<SironaMapping> getCC2MeddraMapping(@RequestBody SironaRequest request) {
        return request.findings().stream()
                .distinct()
                .map(f -> sironaService.clinicalFromClinicalChemistry(f, "blood"))
                .filter(Objects::nonNull)
                .filter(s -> s.to() != null && !s.to().isEmpty())
                .toList();
    }

    @Operation(summary = "Map eTox urinalisys to clinical")
    @PostMapping("/etox/urinalysis")
    public List<SironaMapping> getUrinalysis2MeddraMapping(@RequestBody SironaRequest request) {
        return request.findings().stream()
                .distinct()
                .map(f -> sironaService.clinicalFromClinicalChemistry(f, "urine"))
                .filter(Objects::nonNull)
                .filter(s -> s.to() != null && !s.to().isEmpty())
                .toList();
    }

    @Operation(summary = "Map from clinical to preclinical terminology")
    @PostMapping("/clinical")
    public List<SironaMapping> getEToxMapping(@RequestBody SironaRequest request) {
        var start = System.currentTimeMillis();
        var resp = request.findings().stream()
                .map(sironaService::getETox)
                .filter(Objects::nonNull)
                .filter(s -> s.to() != null && !s.to().isEmpty())
                .toList();
        var duration = System.currentTimeMillis() - start;
        log.info("Translated {} clinical concepts for Sirona in {} millis", request.findings().size(), duration);
        return resp;
    }


    @Operation(summary = "Expand MedDRA terms upto SOCs")
    @PostMapping("/expand")
    public List<ExpandResponse> getSocMapping(@RequestBody List<String> terms) {
        return sironaService.bulkExpand(terms);
    }

}
