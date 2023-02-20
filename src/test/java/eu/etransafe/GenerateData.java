package eu.etransafe;

import eu.etransafe.domain.Concept;
import eu.etransafe.domain.Domain;
import eu.etransafe.domain.Mapping;
import eu.etransafe.domain.MappingItem;
import eu.etransafe.domain.Vocabulary;
import eu.etransafe.service.concepts.ConceptService;
import eu.etransafe.service.mappings.Clinical2Preclinical;
import eu.etransafe.service.mappings.MappingService;
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
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static eu.etransafe.domain.Vocabularies.ETOX;
import static eu.etransafe.domain.Vocabularies.PRECLINICAL;
import static org.apache.commons.lang3.SystemUtils.USER_DIR;


@SpringBootTest
@Disabled
// These are not tests, but methods to generate overviews of mappings, some may run a long time (5 hours), have fun!
class GenerateData {

    public static final String PIPE = " | ";
    public static final String TAB = "\t";
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
    @Autowired
    MappingService mappingService;

    @Test
    @Disabled
    void createFileWithSnomedOrgansForAllPts() {
        File file = new File("pt2snomed.tsv");
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(file, true))) {
            pts().forEach(c -> {
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
        var organs = conceptService.byVocabularyAndDomain(Vocabulary.Identifier.MA, Domain.SPEC_ANATOMIC_SITE, false);
        File file = new File("ma2soc.tsv");
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(file, true))) {
            organs.forEach(c -> {
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
        boolean skipAlreadyDone = true;
        Set<String> alreadyDone = new HashSet<>();

        if (skipAlreadyDone) {
            String excelFile = USER_DIR + "/" + fileName;
            try (InputStream is = new FileInputStream(excelFile);
                 BufferedReader br = new BufferedReader(new InputStreamReader(is))) {
                String line;
                while ((line = br.readLine()) != null) {
                    String[] values = line.split(TAB);
                    alreadyDone.add(values[0]);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        AtomicInteger counter = new AtomicInteger();
        File file = new File(fileName);
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(file, true))) {
            pts().forEach(c -> {
                System.out.println(counter.getAndIncrement() + " " + c.string());
                if (!alreadyDone.contains(c.name())) {
                    var res = clinical2Preclinical.map(c, target, false, 2);
                    double best = mappingService.bestScore(res);

                    StringBuilder s1 = new StringBuilder(c.name() + TAB + String.valueOf(best).substring(0, 3) + TAB);
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
            });

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @Disabled
    void createSEND2PTOverview() {
        // file with list of most combinations in eTox database
        String excelFile = USER_DIR + "/pcdb.tsv";
        List<String[]> terms = new ArrayList<>();
        try (InputStream is = new FileInputStream(excelFile);
             BufferedReader br = new BufferedReader(new InputStreamReader(is))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] values = line.split(TAB);
                terms.add(values);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        File file = new File("send2pt.tsv");
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(file, true))) {
            terms.sort(Comparator.comparing((String[] arr) -> Double.valueOf(arr[2])).reversed());
            DecimalFormat df = new DecimalFormat("#.#");

            for (String[] t : terms) {
                final var finding = conceptService.byCode(t[0], PRECLINICAL);
                final var organ = conceptService.byCode(t[1], PRECLINICAL);
                if (organ != null && finding != null) {
                    final var res = preclinical2Clinical.map(finding, organ, 2);
                    final double bestScore = mappingService.bestScore(res);
                    final String bestMappings = res.stream()
                            .filter(r -> r.totalPenalty() == bestScore)
                            .map(Mapping::to)
                            .flatMap(Collection::stream)
                            .map(MappingItem::humanReadableSimple)
                            .collect(Collectors.joining(" | "));
                    final String line = t[2] + TAB + organ.name() + TAB + finding.name() + TAB + df.format(bestScore) + TAB + bestMappings;
                    bw.write(line);
                    bw.newLine();
                    System.out.println(line);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @Disabled
    void createETox2PTOverview() {
        // file with list of most combinations in eTox database
        String excelFile = USER_DIR + "/copy.tsv";
        List<String[]> terms = new ArrayList<>();
        try (InputStream is = new FileInputStream(excelFile);
             BufferedReader br = new BufferedReader(new InputStreamReader(is))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] values = line.split(TAB);
                terms.add(values);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        File file = new File("etox2pt.tsv");
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(file, true))) {
            terms.sort(Comparator.comparing((String[] arr) -> Double.valueOf(arr[2])).reversed());
            DecimalFormat df = new DecimalFormat("#.#");

            for (String[] t : terms) {
                final var organ = conceptService.byName(t[0], ETOX).stream().findAny().orElse(null);
                final var finding = conceptService.byName(t[1], ETOX).stream().findAny().orElse(null);
                if (organ != null && finding != null) {
                    final var res = preclinical2Clinical.map(finding, organ, 2);
                    final double bestScore = mappingService.bestScore(res);
                    final String bestMappings = res.stream()
                            .filter(r -> r.totalPenalty() == bestScore)
                            .map(Mapping::to)
                            .flatMap(Collection::stream)
                            .map(MappingItem::humanReadableSimple)
                            .collect(Collectors.joining(" | "));
                    final String line = t[2] + TAB + organ.name() + TAB + finding.name() + TAB + df.format(bestScore) + TAB + bestMappings;
                    bw.write(line);
                    bw.newLine();
                    System.out.println(line);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @Disabled
    void createLAB2PTOverview() {
        // file with list of most combinations in eTox database
        String excelFile = USER_DIR + "/eToxCC.tsv";
        List<String[]> terms = new ArrayList<>();
        try (InputStream is = new FileInputStream(excelFile);
             BufferedReader br = new BufferedReader(new InputStreamReader(is))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] values = line.split(TAB);
                terms.add(values);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        File file = new File("labetox2pt.tsv");
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(file, true))) {
//            terms.sort(Comparator.comparing((String[] arr) -> Double.valueOf(arr[2])).reversed());
            DecimalFormat df = new DecimalFormat("#.#");

            for (String[] t : terms) {
                final var finding = conceptService.byName(t[0], Set.of(Vocabulary.Identifier.LABORATORY_TEST_NAME)).stream().findAny().orElse(null);
                if (finding != null) {
                    final var res = preclinical2Clinical.map(finding, null,2);
                    final double bestScore = mappingService.bestScore(res);
                    final String bestMappings = res.stream()
                            .filter(r -> r.totalPenalty() == bestScore)
                            .map(Mapping::to)
                            .flatMap(Collection::stream)
                            .map(MappingItem::humanReadableSimple)
                            .collect(Collectors.joining(" | "));
                    final String line = finding.code() + TAB + finding.name() + TAB + df.format(bestScore) + TAB + bestMappings;
                    bw.write(line);
                    bw.newLine();
                    System.out.println(line);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private List<Concept> pts() {
        return conceptService.byClass("PT");
    }
}
