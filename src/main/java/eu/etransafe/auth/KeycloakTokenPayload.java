package eu.etransafe.auth;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class KeycloakTokenPayload {

    @JsonProperty("preferred_username")
    private String username;
    @JsonProperty("exp")
    private long expiration;
    @JsonProperty("iss")
    private String issuer;
}
