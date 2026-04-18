package com.digitalbluebird.identity.domain

@JvmInline
value class Email private constructor(val value: String) {
    companion object {
        private val PATTERN = Regex("""^[A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}$""")

        fun of(raw: String): Email {
            val normalised = raw.trim().lowercase()
            require(normalised.length in 3..254) { "email length must be 3..254" }
            require(PATTERN.matches(normalised)) { "email format invalid" }
            return Email(normalised)
        }
    }
}
