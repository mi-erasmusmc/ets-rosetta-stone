package eu.etransafe;

import eu.etransafe.domain.Concept;
import eu.etransafe.domain.Domain;
import eu.etransafe.domain.Vocabulary;
import eu.etransafe.service.concepts.ConceptService;
import eu.etransafe.service.mappings.MeddraService;
import eu.etransafe.service.mappings.OrganService;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;


@SpringBootTest
@Disabled
class GenerateData {

    private static final String TAB = "\t";
    @Autowired
    OrganService organs;
    @Autowired
    MeddraService meddraService;
    @Autowired
    ConceptService conceptService;

    @Test
    @Disabled
    void createFileWithSnomedOrgansForAllPts() {
        var pts = conceptService.byClass("PT");

        File file = new File("pt2snomed.tsv");
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(file, true))) {
            pts.forEach(c -> {
                var res = organs.fromPT(c);
                res.forEach(r -> r.to().forEach(t -> t.concepts().forEach(o -> {
                    try {
                        bw.write(conceptLine(c) + TAB + conceptLine(o));
                        bw.newLine();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                })));
            });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    @Test
    @Disabled
    void createFileWithAllSOCsForMouseAnatomyOrgans() {
        var pts = conceptService.byVocabularyAndDomain(Vocabulary.Identifier.MA, Domain.SPEC_ANATOMIC_SITE, false);
        File file = new File("ma2soc.tsv");
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(file, true))) {
            pts.forEach(c -> {
                var res = meddraService.mouseAnatomyToSystemOrganClass(c, 3);
                if (res.isEmpty()) {
                    System.out.println(c);
                } else {
                    res.stream()
                            .filter(r -> r.to() != null)
                            .forEach(r -> r.to().forEach(t -> t.concepts().forEach(o -> {
                                try {
                                    bw.write(conceptLine(c) + TAB + conceptLine(o));
                                    bw.newLine();
                                } catch (IOException e) {
                                    throw new RuntimeException(e);
                                }
                            })));
                }
            });

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private String conceptLine(Concept c) {
        return c.code() + TAB + c.name();
    }
}
