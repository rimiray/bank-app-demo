package com.bankapp.cardservice.config

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.databind.JavaType
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.ObjectMapper.DefaultTyping
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator
import com.fasterxml.jackson.databind.jsontype.TypeResolverBuilder
import org.springframework.cache.CacheManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.cache.RedisCacheConfiguration
import org.springframework.data.redis.cache.RedisCacheManager
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer
import org.springframework.data.redis.serializer.RedisSerializationContext
import org.springframework.data.redis.serializer.StringRedisSerializer
import java.time.Duration

@Configuration
class RedisConfig {

    @Bean
    fun redisTemplate(
        connectionFactory: RedisConnectionFactory,
        objectMapper: ObjectMapper,
    ): RedisTemplate<String, Any> {
        val jsonSerializer = redisJsonSerializer(objectMapper)
        val stringSerializer = StringRedisSerializer()

        return RedisTemplate<String, Any>().apply {
            setConnectionFactory(connectionFactory)
            keySerializer = stringSerializer
            valueSerializer = jsonSerializer
            hashKeySerializer = stringSerializer
            hashValueSerializer = jsonSerializer
            afterPropertiesSet()
        }
    }

    @Bean
    fun cacheManager(
        connectionFactory: RedisConnectionFactory,
        objectMapper: ObjectMapper,
    ): CacheManager {
        val jsonSerializer = redisJsonSerializer(objectMapper)
        val cacheConfig = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofMinutes(10))
            .computePrefixWith { cacheName -> "card-service::$cacheName::" }
            .serializeKeysWith(
                RedisSerializationContext.SerializationPair.fromSerializer(StringRedisSerializer()),
            )
            .serializeValuesWith(
                RedisSerializationContext.SerializationPair.fromSerializer(jsonSerializer),
            )
            .disableCachingNullValues()

        return RedisCacheManager.builder(connectionFactory)
            .cacheDefaults(cacheConfig)
            .build()
    }

    /**
     * Copy of Boot's ObjectMapper (Kotlin + JavaTime modules) with Redis-only type info.
     * Kotlin data classes are final, so they are typed explicitly; HTTP JSON is untouched.
     */
    private fun redisJsonSerializer(objectMapper: ObjectMapper): GenericJackson2JsonRedisSerializer {
        val redisMapper = objectMapper.copy().apply {
            val typeValidator = BasicPolymorphicTypeValidator.builder()
                .allowIfBaseType(Any::class.java)
                .build()
            val typer: TypeResolverBuilder<*> = object : ObjectMapper.DefaultTypeResolverBuilder(
                DefaultTyping.NON_FINAL,
                typeValidator,
            ) {
                override fun useForType(t: JavaType): Boolean {
                    if (t.rawClass.name.startsWith("com.bankapp.")) {
                        return true
                    }
                    return super.useForType(t)
                }
            }.init(JsonTypeInfo.Id.CLASS, null)
                .inclusion(JsonTypeInfo.As.PROPERTY)
            setDefaultTyping(typer)
        }
        return GenericJackson2JsonRedisSerializer.builder()
            .objectMapper(redisMapper)
            .defaultTyping(false)
            .build()
    }
}
