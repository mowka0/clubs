package com.clubs.user

import com.clubs.config.NotFoundException
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class UserService(private val userRepository: UserRepository) {

    fun findByTelegramId(telegramId: Long): UserDto? = userRepository.findByTelegramId(telegramId)

    fun createOrUpdate(
        telegramId: Long,
        username: String?,
        firstName: String?,
        lastName: String?,
        avatarUrl: String?
    ): UserDto = userRepository.createOrUpdate(telegramId, username, firstName, lastName, avatarUrl)

    fun findById(id: UUID): UserDto? = userRepository.findById(id)

    fun getById(id: UUID): UserDto = findById(id) ?: throw NotFoundException("User not found: $id")

    fun updateProfile(id: UUID, dto: UpdateUserDto): UserDto {
        return userRepository.updateProfile(id, dto) ?: throw NotFoundException("User not found: $id")
    }
}
