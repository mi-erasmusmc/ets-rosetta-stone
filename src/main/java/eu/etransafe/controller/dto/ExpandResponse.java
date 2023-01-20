package eu.etransafe.controller.dto;

import eu.etransafe.domain.Concept;

import java.util.List;

public record ExpandResponse(List<Concept> concepts, Integer childlevel, Integer parentlevel, Integer count) {

}

