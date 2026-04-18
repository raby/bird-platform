package com.digitalbluebird.identity.domain.port.outbound

import com.digitalbluebird.identity.domain.Email
import com.digitalbluebird.identity.domain.User
import com.digitalbluebird.shared.domain.UserId

interface UserRepository {
    fun save(user: User): User
    fun findById(id: UserId): User?
    fun existsByEmail(email: Email): Boolean
}
