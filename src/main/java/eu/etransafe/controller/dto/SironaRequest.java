package eu.etransafe.controller.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import eu.etransafe.domain.ToxHubFinding;
import lombok.Data;

import java.util.List;

@Data
public class SironaRequest {

    @JsonProperty("findings")
    List<ToxHubFinding> findings;


}
