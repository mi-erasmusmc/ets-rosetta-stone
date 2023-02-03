package eu.etransafe.domain;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public record SironaMapping(ToxHubFinding from, List<ToxHubFinding> to) implements Serializable {
    @Serial
    private static final long serialVersionUID = 6663076576L;


    public static SironaMapping fromMappingsToMedDRA(Collection<Mapping> mappings, ToxHubFinding from) {
        var to = mappings.stream()
                .map(Mapping::to)
                .flatMap(Collection::stream)
                .map(MappingItem::concepts)
                .flatMap(Collection::stream)
                .map(Concept::name)
                .map(name -> new ToxHubFinding().finding(name))
                .distinct()
                .toList();

        return new SironaMapping(from, to);

    }

    public static SironaMapping fromMappingsToETOX(Collection<Mapping> mappings, ToxHubFinding from) {
        var to = mappings.stream()
                .map(Mapping::to)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .map(mappingItem -> {
                    if (mappingItem != null && mappingItem.concepts() != null) {
                        var organs = mappingItem.concepts().stream().filter(c -> c.domain().equals(Domain.SPEC_ANATOMIC_SITE)).toList();
                        var findings = mappingItem.concepts().stream().filter(c -> !c.domain().equals(Domain.SPEC_ANATOMIC_SITE)).toList();
                        if (organs.size() == 1 || findings.size() == 1) {
                            var pair = new ToxHubFinding();
                            if (organs.size() == 1) {
                                pair.organ(organs.get(0).name());
                            }
                            if (findings.size() == 1) {
                                pair.finding(findings.get(0).name());
                            }
                            return pair;
                        }
                    }
                    return null;
                })
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        var shouldRemoveSingle = to.stream().anyMatch(t -> t.finding != null && t.organ != null);

        if (shouldRemoveSingle) {
            var pairs = to.stream().filter(t -> t.finding != null && t.organ != null).toList();
            return new SironaMapping(from, pairs);
        }

        return new SironaMapping(from, to);
    }

    @Override
    public String toString() {
        return "from: " + from + "\n to: " + to.stream().map(ToxHubFinding::toString).collect(Collectors.joining("\n  "));
    }

}
