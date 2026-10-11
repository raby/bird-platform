package com.digitalbluebird.integrations.ebird.adapter.inbound.web

import com.digitalbluebird.integrations.ebird.domain.port.inbound.ViewEbirdUseCase
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/** Read side of the eBird integration: the recent submissions and the live circuit-breaker state. */
@RestController
@RequestMapping("/integrations/ebird")
class EbirdController(
    private val view: ViewEbirdUseCase,
) {
    @GetMapping("/submissions")
    fun submissions(@RequestParam(defaultValue = "50") limit: Int): List<EbirdSubmissionResponse> =
        view.recent(limit.coerceIn(1, 200)).map(EbirdSubmissionResponse::from)

    @GetMapping("/circuit")
    fun circuit(): CircuitResponse = CircuitResponse(view.circuitState())
}
