package eu.etransafe;

import eu.etransafe.domain.Concept;
import eu.etransafe.domain.Domain;
import eu.etransafe.domain.Mapping;
import eu.etransafe.domain.MappingItem;
import eu.etransafe.domain.Vocabulary;
import eu.etransafe.service.concepts.ConceptService;
import eu.etransafe.service.mappings.Clinical2Preclinical;
import eu.etransafe.service.mappings.MeddraService;
import eu.etransafe.service.mappings.OrganService;
import eu.etransafe.service.mappings.Preclinical2Clinical;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static eu.etransafe.domain.Vocabularies.ETOX;
import static eu.etransafe.domain.Vocabularies.SEND;
import static org.apache.commons.lang3.SystemUtils.USER_DIR;


@SpringBootTest
@Disabled
// These are not tests, but methods to generate overviews of mappings, some may run a long time (5 hours), have fun!
class GenerateData {

    private static final String TAB = "\t";
    public static final String PIPE = " | ";
    @Autowired
    OrganService organs;
    @Autowired
    MeddraService meddraService;
    @Autowired
    ConceptService conceptService;
    @Autowired
    Clinical2Preclinical clinical2Preclinical;
    @Autowired
    Preclinical2Clinical preclinical2Clinical;

    @Test
    @Disabled
    void createFileWithSnomedOrgansForAllPts() {
        var pts = pts();

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

    @Test
    @Disabled
    void createFileWithAllPtToPreclinicalMappings() {
        var target = ETOX; // SEND;
        var fileName = "pt2send.tsv";
        // In case we stopped half way and don't want to restart from scratch
        boolean skipAlreadyDone = false;
        Set<String> alreadyDone = new HashSet<>();

        if (skipAlreadyDone) {
            String excelFile = USER_DIR + "/" + fileName;
            try (InputStream is = new FileInputStream(excelFile);
                 BufferedReader br = new BufferedReader(new InputStreamReader(is))) {
                String line;
                while ((line = br.readLine()) != null) {
                    String[] values = line.split("\t");
                    alreadyDone.add(values[0]);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        AtomicInteger counter = new AtomicInteger();
        var pts = pts();
        Collections.shuffle(pts);
        File file = new File(fileName);
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(file, true))) {
            pts.stream().forEach(c -> {
                System.out.println(counter.getAndIncrement() + " " + c.string());
                if (!alreadyDone.contains(c.name())) {
                    var res = clinical2Preclinical.map(c, target, false, 2);
                    double best = Mapping.bestScore(res);

                    StringBuilder s1 = new StringBuilder(c.name() + "\t" + String.valueOf(best).substring(0, 3) + "\t");
                    res.stream()
                            .filter(r -> r.totalPenalty() == best)
                            .sorted(Comparator.comparing((Mapping m) -> m.to().stream().findAny().get().size()).reversed())
                            .forEach(r -> r.to().stream().sorted(Comparator.comparing(MappingItem::size).reversed()).forEach(t -> s1.append(t.humanReadableSimple()).append(PIPE)));
                    var l = s1.length() - 1;
                    s1.replace(l - 1, l, "");
                    var last = s1.toString().replace(" AND ", " + ");
                    try {
                        bw.write(last);
                        bw.newLine();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }

                // If we want to add the best pair use this

//            double bestPair = res.stream().filter(m -> m.to().stream().anyMatch(mi -> mi.size() > 1 && mi.concepts().stream().map(Concept::vocabulary).collect(Collectors.toSet()).size() > 1)).mapToDouble(Mapping::totalPenalty).min().orElse(best);
//            if (bestPair != best) {
//                StringBuilder s2 = new StringBuilder(c.name() + "\t" + bestPair + "\t");
//                res.stream()
//                        .filter(r -> r.totalPenalty() == bestPair)
//                        .sorted(Comparator.comparing((Mapping m) -> m.to().stream().findAny().get().size()).reversed())
//                        .forEach(r -> r.to().stream().filter(mi -> mi.size() > 1 && mi.concepts().stream().map(Concept::vocabulary).collect(Collectors.toSet()).size() > 1).sorted(Comparator.comparing(MappingItem::size).reversed()).forEach(t -> s2.append(t.humanReadableSimple()).append(" | ")));
//                var l2 = s2.length() - 1;
//                s2.replace(l2 - 1, l2, "");
//                var last2 = s2.toString().replace(" AND ", " + ");
//                System.out.println(last2);
//            }

            });

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @Disabled
    void createETox2PTOverview() {
        // file with list of most combinations in eTox database
        int limit = 500;
        String excelFile = USER_DIR + "/copy.tsv";
        List<String[]> terms = new ArrayList<>(limit);
        try (InputStream is = new FileInputStream(excelFile);
             BufferedReader br = new BufferedReader(new InputStreamReader(is))) {
            String line;
            while ((line = br.readLine()) != null && terms.size() < limit) {
                String[] values = line.split("\t");
                terms.add(values);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        File file = new File("etox2pt.tsv");
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(file, true))) {

            for (String[] t : terms) {
                var organ = conceptService.byName(t[0], ETOX).stream().findAny().get();
                var finding = conceptService.byName(t[1], ETOX).stream().findAny().get();
                var res = preclinical2Clinical.map(finding, organ, 2);
                double best = Mapping.bestScore(res);
                StringBuilder s1 = new StringBuilder(organ.name() + "\t" + finding.name() + "\t" + best + "\t");
                res.stream()
                        .filter(r -> r.totalPenalty() == best)
                        .forEach(r -> r.to().forEach(mi -> s1.append(mi.humanReadableSimple()).append(PIPE)));
                var l = s1.length() - 1;
                s1.replace(l - 1, l, "");
                bw.write(s1.toString());
                bw.newLine();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private List<Concept> pts() {
        return conceptService.byClass("PT");
    }
}
