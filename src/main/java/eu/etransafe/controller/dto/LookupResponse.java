package eu.etransafe.controller.dto;

import java.util.List;

public record LookupResponse(List<String> terms, Integer count, Integer page) {


}

