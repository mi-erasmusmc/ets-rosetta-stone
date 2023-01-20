package eu.etransafe.auth;

import jakarta.servlet.Filter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class AuthConf {

    @Bean
    public Filter tokenFilter() {
        return new AuthFilter();
    }

    @Bean
    public FilterRegistrationBean<Filter> tenantFilterRegistration() {
        FilterRegistrationBean<Filter> result = new FilterRegistrationBean<>();
        result.setFilter(this.tokenFilter());
        result.setUrlPatterns(List.of("/*"));
        result.setName("Token Store Filter");
        result.setOrder(1);
        return result;
    }

}
