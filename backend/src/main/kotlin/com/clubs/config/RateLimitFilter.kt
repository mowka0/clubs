package com.clubs.config

import io.github.bucket4j.Bandwidth
import io.github.bucket4j.Bucket
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

@Component
class RateLimitFilter(private val environment: org.springframework.core.env.Environment) : OncePerRequestFilter() {

    private val ipBuckets = ConcurrentHashMap<String, Bucket>()
    private val userBuckets = ConcurrentHashMap<String, Bucket>()

    private val isDevProfile: Boolean
        get() = environment.activeProfiles.contains("dev")

    private fun getIpBucket(ip: String): Bucket {
        return ipBuckets.computeIfAbsent(ip) {
            val capacity = if (isDevProfile) 1000L else 100L
            Bucket.builder()
                .addLimit(Bandwidth.builder().capacity(capacity).refillGreedy(capacity, Duration.ofMinutes(1)).build())
                .build()
        }
    }

    private fun getUserBucket(userId: String): Bucket {
        return userBuckets.computeIfAbsent(userId) {
            Bucket.builder()
                .addLimit(Bandwidth.builder().capacity(30).refillGreedy(30, Duration.ofMinutes(1)).build())
                .build()
        }
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val ip = request.getHeader("X-Forwarded-For")?.split(",")?.firstOrNull()?.trim()
            ?: request.remoteAddr ?: "unknown"
        val ipBucket = getIpBucket(ip)

        if (!ipBucket.tryConsume(1)) {
            response.status = HttpStatus.TOO_MANY_REQUESTS.value()
            response.contentType = "application/json"
            response.writer.write("{\"error\":\"Too Many Requests\",\"message\":\"Rate limit exceeded\",\"status\":429}")
            return
        }

        val isMutation = request.method in listOf("POST", "PUT", "DELETE", "PATCH")
        if (isMutation) {
            val authHeader = request.getHeader("Authorization")
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                val token = authHeader.substring(7)
                // Use a hash of the token prefix as user bucket key
                val userKey = token.take(40).hashCode().toString()
                val userBucket = getUserBucket(userKey)
                if (!userBucket.tryConsume(1)) {
                    response.status = HttpStatus.TOO_MANY_REQUESTS.value()
                    response.contentType = "application/json"
                    response.writer.write("{\"error\":\"Too Many Requests\",\"message\":\"User rate limit exceeded\",\"status\":429}")
                    return
                }
            }
        }

        filterChain.doFilter(request, response)
    }

    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        return !request.requestURI.startsWith("/api/")
    }
}
