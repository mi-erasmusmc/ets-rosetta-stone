package eu.etransafe.domain;

import eu.etransafe.exception.RosettaException;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Entity
@Getter
@Setter
@ToString
public class ConceptRelationship {

    @Id
    Integer id;
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "concept_id_1")
    Concept conceptOne;
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "concept_id_2")
    Concept conceptTwo;
    String invalidReason;
    Identifier relationshipId;
    String source;


    public enum Identifier implements CDMEnum<Identifier> {

        HAS_FINDING_SITE("Has finding site"),
        MAPS_TO("Maps to"),
        ASSO_MORPH_OF("Asso morph of"),
        HAS_ASSO_MORPH("Has asso morph"),
        FINDING_SITE_OF("Finding site of"),
        HAS_DIR_PROC_SITE("Has dir proc site"),
        DIR_PROC_SITE_OF("Dir proc site of"),
        MAPPED_FROM("Mapped from"),
        SNOMED_MED_DRA_EQ("SNOMED - MedDRA eq"),
        MED_DRA_SNOMED_EQ("MedDRA - SNOMED eq"),
        SUBSUMES("Subsumes"),
        SMQ_MEDDRA("SMQ - MedDRA"),
        EXACT("Exact match"),
        NARROW("Narrow match"),
        BROAD("Broad match"),
        HAS_CAUSATIVE_AGENT("Has causative agent"),
        CAUSATIVE_AGENT_OF("Causative agent of"),
        PATHOLOGY_OF("Pathology of"),
        HAS_PATHOLOGY("Has pathology"),
        OCCURRENCE_OF("Occurrence of"),
        HAS_OCCURRENCE("Has occurrence"),
        INTERPRETS_OF("Interprets of"),
        HAS_INTERPRETS("Has interprets"),
        INTERPRETATION_OF("Interpretation of"),
        COMPONENT_OF("Component of"),
        HAS_DISPOSITION("Has Disposition"),
        DISPOSITION_OF("Disposition of"),
        HAS_COMPONENT("Has component"),
        RELATED("Related match"),
        OTHER("Other");

        private final String value;

        Identifier(String value) {
            this.value = value;
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
