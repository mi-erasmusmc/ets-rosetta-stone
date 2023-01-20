package eu.etransafe.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Transient;
import lombok.Getter;
import lombok.Setter;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Setter
@Getter
@Entity
public class Concept implements Serializable {
    @Serial
    private static final long serialVersionUID = 398745685L;

    @Id
    @Column(name = "concept_id")
    @JsonProperty("id")
    private Integer id;
    @Column(name = "concept_name")
    @JsonProperty("name")
    private String name;
    @Column(name = "domain_id")
    @JsonProperty("domain")
    private Domain domain;
    @Column(name = "vocabulary_id")
    @JsonProperty("vocabulary")
    private Vocabulary.Identifier vocabulary;
    @Column(name = "concept_class_id")
    @JsonProperty("concept_class")
    private String conceptClass;
    @Column(name = "concept_code")
    @JsonProperty("code")
    private String code;
    @JsonProperty("invalid_reason")
    private String invalidReason;
    @Transient
    @JsonProperty("level")
    private int level;
    @Transient
    @JsonProperty("children")
    private List<Concept> children = new ArrayList<>();


    public Concept addChild(Concept childrenItem) {
        if (this.children == null) {
            this.children = new ArrayList<>();
        }
        this.children.add(childrenItem);
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Concept concept = (Concept) o;
        return id.equals(concept.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return id + (children == null || children.isEmpty() ? "" : children.stream().map(Concept::toString).collect(Collectors.joining(",")));
    }

    // toString was used by Redis as key for cache trying to keep that small, this method is for debugging and logging
    public String string() {
        return name + " (" + vocabulary + ": " + code + ")"
                + (children == null || children.isEmpty() ? "" : "\n children: [" + children.stream().map(Concept::string).collect(Collectors.joining(", ")) + "] ");
    }
}

