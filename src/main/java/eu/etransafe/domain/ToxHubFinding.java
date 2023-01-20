package eu.etransafe.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
public class ToxHubFinding implements Serializable {

    @Serial
    private static final long serialVersionUID = 5558495762837L;


    @JsonProperty("organ")
    String organ;
    @JsonProperty("finding")
    String finding;
    @JsonProperty("observation")
    String observation;

}
