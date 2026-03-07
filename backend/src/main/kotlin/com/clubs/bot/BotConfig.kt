package com.clubs.bot

import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestTemplate

@Configuration
class BotConfig(private val restTemplateBuilder: RestTemplateBuilder) {

    @Bean
    fun restTemplate(): RestTemplate = restTemplateBuilder.build()
}
