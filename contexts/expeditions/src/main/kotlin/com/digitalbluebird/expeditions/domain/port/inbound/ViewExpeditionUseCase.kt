package com.digitalbluebird.expeditions.domain.port.inbound

import arrow.core.Either
import com.digitalbluebird.expeditions.domain.Expedition
import com.digitalbluebird.expeditions.domain.ExpeditionError

fun interface ViewExpeditionUseCase {
    fun findById(id: String): Either<ExpeditionError, Expedition>
}
