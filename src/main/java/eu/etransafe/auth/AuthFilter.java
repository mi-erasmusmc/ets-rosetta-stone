package eu.etransafe.auth;

import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;

@Slf4j
public class AuthFilter implements Filter {

    public static final int EXTRA_TIME_FOR_SIRONA = 1000;
    private static final Set<String> PUBLIC_ENDPOINTS = Set.of("/v2/actuator/health/readiness", "/v2/actuator/health/liveness", "/v2/favicon.ico");
    private static final String AUTHENTICATION_SCHEME = "Bearer";
    private static final ObjectMapper mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private static final LoadingCache<String, Long> cache = Caffeine.newBuilder()
            .maximumSize(100)
            .expireAfterWrite(20, TimeUnit.MINUTES)
            .build(AuthFilter::getExpirationTimeFromHeader);
    private final HttpClient httpClient;


    @Value("${toxhub.auth.url}")
    String authUrl;
    @Value("${auth.enabled: false}")
    boolean authEnabled;

    public AuthFilter() {
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .connectTimeout(Duration.ofSeconds(120))
                .build();
    }

    private static long getExpirationTimeFromHeader(String header) {
        String token = header.replace(AUTHENTICATION_SCHEME, "").trim();
        DecodedJWT jwt = JWT.decode(token);
        String json;
        try {
            json = new String(Base64.getUrlDecoder().decode(jwt.getPayload()));
        } catch (IllegalArgumentException e) {
            json = new String(Base64.getDecoder().decode(jwt.getPayload()));
        }
        try {
            KeycloakTokenPayload payload = mapper.readValue(json, KeycloakTokenPayload.class);
            log.info("Welcome {}", payload.username());
            return payload.expiration();
        } catch (JsonProcessingException e) {
            log.error(e.getMessage());
            return 0;
        }
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain) throws IOException, ServletException {
        if (authEnabled) {
            authenticate(servletRequest, servletResponse, chain);
        } else {
            chain.doFilter(servletRequest, servletResponse);
        }
    }

    private void authenticate(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain) throws IOException, ServletException {
        if (servletRequest instanceof HttpServletRequest request) {
            String path = request.getRequestURI().toLowerCase();
            if (request.getMethod().equals("OPTIONS") || PUBLIC_ENDPOINTS.contains(path) || path.contains("swagger") || path.contains("api-docs")) {
                chain.doFilter(servletRequest, servletResponse);
            } else {
                String details = requestDetails(request);
                String authorizationHeader = request.getHeader(AUTHORIZATION);
                if (!isValid(authorizationHeader)) {
                    abortWithUnauthorized(servletResponse, details);
                } else {
                    log.info(details);
                    chain.doFilter(servletRequest, servletResponse);
                }
            }
        } else {
            log.warn("Received a request that was not an HttpServletRequest {}", servletRequest);
        }
    }

    private String requestDetails(HttpServletRequest request) {
        String path = request.getRequestURI();
        StringBuilder params = new StringBuilder();
        request.getParameterMap().forEach((k, v) -> params.append(k).append("=").append(v[0]).append(" "));
        return request.getMethod() + " " + path + " " + params.toString().trim();
    }


    private void abortWithUnauthorized(ServletResponse servletResponse, String path) throws IOException {
        HttpServletResponse response = (HttpServletResponse) servletResponse;
        log.warn("UNAUTHORIZED {}", path);
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        String errorJson = "{\"Unauthorized\": \"You did not say the magic word, if you are certain you did call the helpdesk\"}";
        response.getWriter().write(errorJson);
        response.getWriter().close();
        response.getWriter().flush();
    }

    private boolean isValid(String header) {
        if (header == null || !header.startsWith(AUTHENTICATION_SCHEME)) {
            return false;
        }
        Long cachedExpTime = cache.getIfPresent(header);
        log.debug("Loaded expiration timestamp from cache {}", cachedExpTime);
        if (cachedExpTime != null) {
            return cachedExpTime > System.currentTimeMillis() / 1000L;
        }

        // Check expiration
        long expirationTime = getExpirationTimeFromHeader(header);
        if (expirationTime < System.currentTimeMillis() / 1000L) {
            return false;
        }
        var request = buildGetRequest(authUrl + "/userinfo", header);
        if (isKeycloakInAgreement(request)) {
            cache.put(header, expirationTime + EXTRA_TIME_FOR_SIRONA);
            return true;
        }
        return false;
    }

    private HttpRequest buildGetRequest(String path, String token) {
        return HttpRequest.newBuilder()
                .GET()
                .uri(URI.create(path))
                .setHeader(AUTHORIZATION, token)
                .header(CONTENT_TYPE, "application/json")
                .build();
    }

    private boolean isKeycloakInAgreement(HttpRequest request) {
        try {
            HttpResponse<String> resp = httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString()).get();
            if (resp.statusCode() == 200) {
                return true;
            } else if (resp.statusCode() == 401) {
                log.warn("401 Unauthorized: {}", resp.body());
            } else {
                log.error("Request [{}] failed with status code {}", request.uri().toString(), resp.statusCode());
                log.error("Response was: {}", resp.body());
            }
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
            log.error("Something went bad authenticating with keycloak");
        }
        return false;
    }
}
