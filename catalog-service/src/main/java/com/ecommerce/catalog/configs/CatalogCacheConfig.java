package com.ecommerce.catalog.configs;

import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

@Configuration
@EnableCaching
public class CatalogCacheConfig implements CachingConfigurer {
  private static final Logger log = LoggerFactory.getLogger(CatalogCacheConfig.class);

  @Bean
  RedisCacheManager cacheManager(
      RedisConnectionFactory connectionFactory,
      @Value("${app.catalog.cache-ttl-seconds:300}") long ttlSeconds) {
    RedisCacheConfiguration configuration = RedisCacheConfiguration.defaultCacheConfig()
        .entryTtl(Duration.ofSeconds(ttlSeconds))
        .computePrefixWith(name -> "catalog:" + name + ":")
        .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(
            new GenericJackson2JsonRedisSerializer()));
    return RedisCacheManager.builder(connectionFactory).cacheDefaults(configuration).build();
  }

  @Bean
  @Override
  public CacheErrorHandler errorHandler() {
    return new CacheErrorHandler() {
      @Override public void handleCacheGetError(RuntimeException e, Cache cache, Object key) {
        log.warn("catalog cache get failed cache={} key={}", cache.getName(), key);
      }
      @Override public void handleCachePutError(RuntimeException e, Cache cache, Object key, Object value) {
        log.warn("catalog cache put failed cache={} key={}", cache.getName(), key);
      }
      @Override public void handleCacheEvictError(RuntimeException e, Cache cache, Object key) {
        log.warn("catalog cache eviction failed cache={} key={}", cache.getName(), key);
      }
      @Override public void handleCacheClearError(RuntimeException e, Cache cache) {
        log.warn("catalog cache clear failed cache={}", cache.getName());
      }
    };
  }
}
