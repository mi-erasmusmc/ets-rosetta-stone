package eu.etransafe.domain;

import java.util.EnumSet;
import java.util.Set;

import static eu.etransafe.domain.Vocabulary.Identifier.HPATH;
import static eu.etransafe.domain.Vocabulary.Identifier.LABORATORY_TEST_NAME;
import static eu.etransafe.domain.Vocabulary.Identifier.MA;
import static eu.etransafe.domain.Vocabulary.Identifier.MEDDRA;
import static eu.etransafe.domain.Vocabulary.Identifier.NEOPLASM_TYPE;
import static eu.etransafe.domain.Vocabulary.Identifier.NON_NEOPLASTIC_FINDING;
import static eu.etransafe.domain.Vocabulary.Identifier.SPECIMEN;

public class Vocabularies {

    public static final Set<Vocabulary.Identifier> CLINICAL = EnumSet.of(MEDDRA);
    public static final Set<Vocabulary.Identifier> ETOX = EnumSet.of(HPATH, MA);
    public static final Set<Vocabulary.Identifier> INTERMEDIARY = EnumSet.of(Vocabulary.Identifier.SNOMED);
    public static final Set<Vocabulary.Identifier> ORGANS = EnumSet.of(Vocabulary.Identifier.SNOMED, MA, SPECIMEN);
    public static final Set<Vocabulary.Identifier> PRECLINICAL = EnumSet.of(HPATH, SPECIMEN, MA, NON_NEOPLASTIC_FINDING, NEOPLASM_TYPE, LABORATORY_TEST_NAME);
    public static final Set<Vocabulary.Identifier> PRECLINICAL_FINDINGS = EnumSet.of(HPATH, NON_NEOPLASTIC_FINDING, NEOPLASM_TYPE, LABORATORY_TEST_NAME);
    public static final Set<Vocabulary.Identifier> PRECLINICAL_ORGANS = EnumSet.of(MA, SPECIMEN);
    public static final Set<Vocabulary.Identifier> SEND = EnumSet.of(NON_NEOPLASTIC_FINDING, NEOPLASM_TYPE, LABORATORY_TEST_NAME, SPECIMEN);
    public static final Set<Vocabulary.Identifier> SNOMED = EnumSet.of(Vocabulary.Identifier.SNOMED);

    private Vocabularies() {
    }

}
