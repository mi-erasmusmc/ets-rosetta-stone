package eu.etransafe.controller.dto;

import eu.etransafe.domain.Mapping;
import eu.etransafe.domain.MappingItem;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

public record ToxHubOrganResponse(String organCode, String organName) {


    public static List<ToxHubOrganResponse> fromMapping(Mapping mapping) {
        if (mapping == null || mapping.to() == null || mapping.to().isEmpty()) {
            return Collections.emptyList();
        }
        return mapping.to().stream()
                .map(MappingItem::concepts)
                .flatMap(Collection::stream)
                .map(c -> new ToxHubOrganResponse(c.code(), c.name()))
                .distinct()
                .toList();
    }

}
