package eu.etransafe.controller.dto;

import eu.etransafe.domain.Concept;

import java.util.Set;

public record NormalizeResponse(Set<Concept> concepts, Integer count, Integer page) {

}

