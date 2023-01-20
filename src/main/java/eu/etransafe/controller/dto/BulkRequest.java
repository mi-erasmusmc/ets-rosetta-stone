package eu.etransafe.controller.dto;

import eu.etransafe.domain.MappingAlgorithm;

import java.util.List;

public record BulkRequest(MappingAlgorithm algorithm, List<String> conceptCodes) {

}

