package com.digitalbluebird.shared.domain

import java.math.BigDecimal
import java.util.Currency

data class Money(
    val amount: BigDecimal,
    val currency: Currency,
) {
    init {
        require(amount.scale() <= currency.defaultFractionDigits) {
            "Amount scale ${amount.scale()} exceeds ${currency.currencyCode} fraction digits ${currency.defaultFractionDigits}"
        }
    }

    operator fun plus(other: Money): Money {
        requireSameCurrency(other)
        return Money(amount + other.amount, currency)
    }

    operator fun minus(other: Money): Money {
        requireSameCurrency(other)
        return Money(amount - other.amount, currency)
    }

    operator fun times(multiplier: Int): Money =
        Money(amount * BigDecimal(multiplier), currency)

    private fun requireSameCurrency(other: Money) {
        require(currency == other.currency) {
            "Cannot combine ${currency.currencyCode} with ${other.currency.currencyCode}"
        }
    }

    companion object {
        private val GBP: Currency = Currency.getInstance("GBP")
        private val USD: Currency = Currency.getInstance("USD")
        private val EUR: Currency = Currency.getInstance("EUR")

        fun gbp(amount: String): Money = Money(BigDecimal(amount), GBP)
        fun usd(amount: String): Money = Money(BigDecimal(amount), USD)
        fun eur(amount: String): Money = Money(BigDecimal(amount), EUR)
    }
}
