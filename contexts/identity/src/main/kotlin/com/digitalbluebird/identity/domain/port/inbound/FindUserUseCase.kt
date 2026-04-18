package com.digitalbluebird.identity.domain.port.inbound

import arrow.core.Either
import com.digitalbluebird.identity.domain.IdentityError
import com.digitalbluebird.identity.domain.User
import com.digitalbluebird.shared.domain.UserId

interface FindUserUseCase {
    fun findById(id: UserId): Either<IdentityError, User>
}
