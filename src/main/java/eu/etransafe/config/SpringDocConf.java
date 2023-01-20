package eu.etransafe.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import io.swagger.v3.oas.models.media.Schema;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;
import java.util.TreeMap;


@OpenAPIDefinition(
        info = @Info(title = "eTransafe Rosetta Stone API",
                description = "The eTransafe Rosetta Stone facilitates lookup of terms and translations between terminologies",
                version = "2.3.0",
                license = @License(name = "Apache 2.0", url = "https://www.apache.org/licenses/LICENSE-2.0"),
                contact = @Contact(name = "Rowan Parry",
                        email = "r.parry@erasmusmc.nl",
                        url = "https://biosemantics.erasmusmc.nl/")))
@Configuration
public class SpringDocConf {

    @Bean
    public OpenApiCustomizer sortSchemasAlphabetically() {
        return openApi -> {
            Map<String, Schema> schemas = openApi.getComponents().getSchemas();
            openApi.getComponents().setSchemas(new TreeMap<>(schemas));
        };
    }

}
