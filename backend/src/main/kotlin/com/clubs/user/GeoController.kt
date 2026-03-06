package com.clubs.user

import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.client.RestTemplate
import jakarta.servlet.http.HttpServletRequest

data class CityResponse(val city: String?)

@RestController
@RequestMapping("/api/geo")
class GeoController {

    private val log = LoggerFactory.getLogger(GeoController::class.java)
    private val restTemplate = RestTemplate()

    @GetMapping("/city")
    fun getCity(request: HttpServletRequest): ResponseEntity<CityResponse> {
        val ip = request.getHeader("X-Forwarded-For")?.split(",")?.firstOrNull()?.trim()
            ?: request.remoteAddr
        val city = resolveCity(ip)
        return ResponseEntity.ok(CityResponse(city))
    }

    private fun resolveCity(ip: String): String? {
        if (ip == "127.0.0.1" || ip == "0:0:0:0:0:0:0:1" || ip.startsWith("192.168.") || ip.startsWith("10.")) {
            return null
        }
        return try {
            val response = restTemplate.getForObject(
                "http://ip-api.com/json/$ip?fields=status,city",
                Map::class.java
            )
            if (response?.get("status") == "success") response["city"] as? String else null
        } catch (e: Exception) {
            log.warn("Failed to resolve city for IP $ip: ${e.message}")
            null
        }
    }
}
