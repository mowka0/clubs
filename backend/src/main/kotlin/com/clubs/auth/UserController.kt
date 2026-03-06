package com.clubs.auth

import com.clubs.user.UserDto
import com.clubs.user.UserService
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/users")
class UserController(private val userService: UserService) {

    @GetMapping("/me")
    fun getMe(authentication: Authentication): ResponseEntity<UserDto> {
        val userId = UUID.fromString(authentication.principal as String)
        return ResponseEntity.ok(userService.getById(userId))
    }

    @GetMapping("/{id}")
    fun getUser(@PathVariable id: UUID): ResponseEntity<UserDto> {
        return ResponseEntity.ok(userService.getById(id))
    }
}
