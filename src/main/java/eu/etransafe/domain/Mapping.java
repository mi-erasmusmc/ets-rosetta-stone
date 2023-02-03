package eu.etransafe.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import eu.etransafe.exception.RosettaException;
import eu.etransafe.util.StringUtils;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import static eu.etransafe.domain.Mapping.Direction.DOWNHILL;

@Data
@Slf4j
public class Mapping implements Serializable {
    public static final String DESCR_TO_SINGLE_OR = "Multiple ORs to one mapping";
    public static final String ENTIRE = "entire";
    public static final String STRUCTURE = "structure";
    public static final String PART = "part";
    @Serial
    private static final long serialVersionUID = 1238495742837L;
    private static final Collector<CharSequence, ?, String> JOINING_OR = Collectors.joining("\n OR \n");
    @JsonProperty("to")
    private Set<MappingItem> to = new HashSet<>();
    @JsonProperty("from")
    private Set<MappingItem> from;
    @JsonProperty("description")
    private String description;
    @JsonProperty(value = "penalty")
    @JsonInclude() // Always include
    private double penalty = 0;
    @JsonProperty("preceding_mapping")
    private Mapping precedingMapping;
    private double totalPenalty;

    public static Mapping noMapping(Concept... from) {
        var item = new MappingItem(Set.of(from));
        return noMapping(item);
    }

    public static Mapping noMapping(MappingItem from) {
        return new Mapping()
                .from(from)
                .description("No mappings available for " + from.humanReadable());
    }

    // Return best positive mapping, if no positive mapping best negative, if no mappings emptyList
    public static List<Mapping> bestMappings(Collection<Mapping> mappings) {
        double bestScore = bestScore(mappings);
        return mappings.stream()
                .filter(r -> r.totalPenalty() == bestScore)
                .toList();
    }

    // Return best positive mapping, if no positive mapping best negative, if no mappings 0
    public static double bestScore(Collection<Mapping> mappings) {
        return mappings.stream().anyMatch(r -> r.totalPenalty() >= 0) ?
                mappings.stream().filter(r -> r.totalPenalty() >= 0).mapToDouble(Mapping::totalPenalty).min().orElse(0) :
                mappings.stream().filter(r -> r.totalPenalty() <= 0).mapToDouble(Mapping::totalPenalty).max().orElse(0);
    }


    // use for debugging
    public String explanationString() {
        return "\nRESULT:\n" +
                (to == null ? "No mapping" : to.stream().map(MappingItem::humanReadable).collect(JOINING_OR)) +
                "\nTotal penalty: " + totalPenalty() +
                "\nStep: " +
                description +
                (penalty != 0 ? (" (penalty: " + penalty) + ")" : "") +
                "\n" +
                precedingStepString();
    }

    private String precedingStepString() {
        if (precedingMapping != null) {
            return "\nFROM:\n" +
                    precedingMapping.to.stream().map(MappingItem::humanReadable).collect(JOINING_OR)
                    + "\n\nStep: " + precedingMapping.description + (precedingMapping.penalty != 0 ? (" (penalty: " + precedingMapping.penalty) + ")" : "") +
                    "\n"
                    + precedingMapping.precedingStepString();
        }
        return "\nFROM INPUT:\n"
                + from.stream().map(MappingItem::humanReadable).collect(JOINING_OR);
    }

    public Mapping from(Mapping preceding) {
        if (preceding == null) {
            log.warn("Passing a preceding null into Mapping, you should not do this (but I will let it pass for now)");
            return this;
        }
        this.precedingMapping = preceding;
        this.from = preceding.to();
        return this;
    }

    public Mapping from(Collection<MappingItem> from) {
        if (from instanceof Set<MappingItem> set) {
            this.from = set;
        } else {
            this.from = new HashSet<>(from);
        }
        return this;
    }

    public Mapping from(MappingItem from) {
        this.from = Set.of(from);
        return this;
    }

    public Mapping from(Concept from) {
        this.from = Set.of(new MappingItem(from));
        return this;
    }

    public Mapping to(Concept concept) {
        this.to = Set.of(new MappingItem(Set.of(concept)));
        return this;
    }

    public Mapping to(Set<MappingItem> to) {
        this.to = to;
        return this;
    }

    public Mapping to(MappingItem mappingItem) {
        this.to = Set.of(mappingItem);
        return this;
    }

    public Mapping precedingMapping(Mapping precedingMapping) {
        this.precedingMapping = precedingMapping;
        this.from = precedingMapping.to();
        return this;
    }

    public Mapping expanded(Concept from, Concept to, Direction direction) {
        this.description = StringUtils.capitalize(direction.toString().toLowerCase()) + " mapping of " + from.name();
        this.penalty = calculatePenalty(from, to, direction);
        return this;
    }

    private double calculatePenalty(Concept from, Concept to, Mapping.Direction direction) {
        double specialPenalty = 0.1;
        var fromName = from.name().toLowerCase();
        var toName = to.name().toLowerCase();
        if (direction.equals(DOWNHILL)) {
            if (toName.contains(ENTIRE) && fromName.contains(PART)) {
                return specialPenalty;
            }
            return fromName.contains(STRUCTURE) && toName.contains(PART) ? specialPenalty : 1;
        }
        if (toName.contains(STRUCTURE) && fromName.contains(ENTIRE)) {
            return specialPenalty;
        }
        return fromName.contains(PART) && toName.contains(STRUCTURE) ? specialPenalty : 1;
    }

    @JsonProperty(value = "total_penalty")
    @JsonInclude() //Always include even when 0
    public double totalPenalty() {
        if (precedingMapping != null) {
            if (precedingMapping.totalPenalty() < 0 || penalty < 0) {
                return -(Math.abs(penalty) + Math.abs(precedingMapping.totalPenalty()));
            } else {
                return penalty + precedingMapping.totalPenalty();
            }
        } else {
            return penalty;
        }
    }

    public Set<Concept> toConcepts() {
        return to.stream().map(MappingItem::concepts).flatMap(Collection::stream).collect(Collectors.toSet());
    }

    @JsonIgnore
    public Concept singleToConcept() {
        return singleToMappingItem().getSingleConcept();
    }

    @JsonIgnore
    public boolean isToSingleConcept() {
        if (to.size() == 1) {
            return singleToMappingItem().size() == 1;
        }
        return false;
    }

    @JsonIgnore
    public MappingItem singleToMappingItem() {
        if (to.size() != 1) {
            throw new RosettaException("Found " + to.size() + " 'to' MappingItems when 1 was expected");
        }
        return to.stream().findAny()
                .orElseThrow(() -> new RosettaException("Found 0 'to' MappingItems when 1 was expected"));
    }

    public int levels() {
        if (precedingMapping != null) {
            return precedingMapping.levels() + 1;
        } else {
            return 1;
        }
    }

    public enum Direction {
        UPHILL,
        DOWNHILL
    }

}
