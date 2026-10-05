package com.digitalbluebird.surveys.domain

import java.util.UUID

@JvmInline
value class SurveyPlotId(val value: UUID) {
    companion object {
        fun fromString(s: String): SurveyPlotId = SurveyPlotId(UUID.fromString(s))
    }
}
