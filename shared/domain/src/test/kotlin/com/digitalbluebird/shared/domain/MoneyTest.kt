package com.digitalbluebird.shared.domain

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.messageContains
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class MoneyTest {

    @Test
    fun `adds amounts of the same currency`() {
        assertThat(Money.gbp("10.00") + Money.gbp("5.50"))
            .isEqualTo(Money.gbp("15.50"))
    }

    @Test
    fun `subtracts amounts of the same currency`() {
        assertThat(Money.gbp("10.00") - Money.gbp("3.25"))
            .isEqualTo(Money.gbp("6.75"))
    }

    @Test
    fun `multiplies by integer`() {
        assertThat(Money.gbp("2.50") * 4).isEqualTo(Money.gbp("10.00"))
    }

    @Test
    fun `rejects addition across currencies`() {
        val e = assertThrows<IllegalArgumentException> {
            Money.gbp("10.00") + Money.usd("10.00")
        }
        assertThat(e).messageContains("GBP")
    }

    @Test
    fun `rejects amount with too much precision for currency`() {
        assertThrows<IllegalArgumentException> { Money.gbp("10.001") }
    }
}
