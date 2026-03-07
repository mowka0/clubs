package com.clubs.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer
import org.springframework.data.redis.serializer.StringRedisSerializer

class RedisConfigTest {

    private val redisConfig = RedisConfig()

    @Test
    fun `redisTemplate uses StringRedisSerializer for keys`() {
        val factory = mock<RedisConnectionFactory>()
        val template = redisConfig.redisTemplate(factory)
        assertThat(template.keySerializer).isInstanceOf(StringRedisSerializer::class.java)
    }

    @Test
    fun `redisTemplate uses GenericJackson2JsonRedisSerializer for values`() {
        val factory = mock<RedisConnectionFactory>()
        val template = redisConfig.redisTemplate(factory)
        assertThat(template.valueSerializer).isInstanceOf(GenericJackson2JsonRedisSerializer::class.java)
    }

    @Test
    fun `redisTemplate uses StringRedisSerializer for hash keys`() {
        val factory = mock<RedisConnectionFactory>()
        val template = redisConfig.redisTemplate(factory)
        assertThat(template.hashKeySerializer).isInstanceOf(StringRedisSerializer::class.java)
    }

    @Test
    fun `redisTemplate connection factory is set`() {
        val factory = mock<RedisConnectionFactory>()
        val template = redisConfig.redisTemplate(factory)
        assertThat(template.connectionFactory).isEqualTo(factory)
    }
}
