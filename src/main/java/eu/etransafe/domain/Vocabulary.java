package eu.etransafe.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import eu.etransafe.exception.RosettaException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Transient;
import lombok.Getter;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.springframework.util.CollectionUtils.isEmpty;

@Entity
@Getter
public class Vocabulary {

    @Column(name = "vocabulary_id")
    @JsonProperty("id")
    private Identifier id;
    @Id //Putting the id on the name column because otherwise hibernate gets in a mess with the enum
    @Column(name = "vocabulary_name")
    @JsonProperty("name")
    private String name;
    @JsonProperty("reference")
    @Column(name = "vocabulary_reference")
    private String reference;
    @Column(name = "vocabulary_version")
    @JsonProperty("version")
    private String version;
    @Column(name = "vocabulary_concept_id")
    @JsonProperty("concept_id")
    private Integer conceptId;
    @Transient
    @JsonProperty("type")
    private Type type;


    public enum Type {
        ALL,
        CLINICAL,
        PRECLINICAL,
        INTERMEDIARY;

        public static Type toType(Identifier vocabulary) {

            if (Vocabularies.CLINICAL.contains(vocabulary)) {
                return Type.CLINICAL;
            }
            if (Vocabularies.PRECLINICAL.contains(vocabulary)) {
                return Type.PRECLINICAL;
            }
            if (Vocabularies.INTERMEDIARY.contains(vocabulary)) {
                return Type.INTERMEDIARY;
            }
            return Type.ALL;
        }
    }

    public enum Identifier implements CDMEnum<Identifier> {
        MEDDRA("MedDRA"),
        ILO("ILO"),
        SNOMED("SNOMED"),
        MA("MA"),
        SPECIMEN("Specimen"),
        HPATH("HPATH"),
        NON_NEOPLASTIC_FINDING("Non-Neoplastic Findi"),
        NEOPLASM_TYPE("Neoplasm Type"),
        LABORATORY_TEST_NAME("Laboratory Test Name"),
        OTHER("Other");

        private final String value;

        Identifier(String value) {
            this.value = value;
        }

        public static List<String> convert(Set<Identifier> vocabularies) {
            if (isEmpty(vocabularies)) {
                return EnumSet.allOf(Identifier.class).stream().map(Identifier::value).toList();
            }
            return vocabularies.stream().map(Identifier::value).toList();
        }

        public String value() {
            return this.value;
        }

        @Override
        public <E extends Enum<E> & CDMEnum<E>> E other(Class<E> type) {
            var other = OTHER;
            if (type.isInstance(other)) {
                return type.cast(other);
            } else {
                throw new RosettaException("This should not happen");
            }
        }
    }

}

