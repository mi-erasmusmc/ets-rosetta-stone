package eu.etransafe.config;

import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;

import java.time.Duration;

@Configuration
@EnableCaching
public class CachingConf {

    @Bean
    public RedisCacheManagerBuilderCustomizer redisCacheManagerBuilderCustomizer() {
        return builder -> builder
                .withCacheConfiguration("expandMeddraPrimary", cacheConfiguration())
                .withCacheConfiguration("sironaFromMeddra", cacheConfiguration())
                .withCacheConfiguration("sironaFromLab", cacheConfiguration())
                .withCacheConfiguration("sironaFromHistopathology", cacheConfiguration())
                .withCacheConfiguration("children", cacheConfiguration().entryTtl(Duration.ofDays(3)))
                .withCacheConfiguration("parents", cacheConfiguration().entryTtl(Duration.ofDays(3)))
                .withCacheConfiguration("snomedOptions", cacheConfiguration().entryTtl(Duration.ofDays(3)))
                .withCacheConfiguration("conceptsByVocAndDom", cacheConfiguration().entryTtl(Duration.ofDays(5)));
    }

    @Bean
    public RedisCacheConfiguration cacheConfiguration() {
        return RedisCacheConfiguration.defaultCacheConfig().entryTtl(Duration.ofDays(21));
    }
}
