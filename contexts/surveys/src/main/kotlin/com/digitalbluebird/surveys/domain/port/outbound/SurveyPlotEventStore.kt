package com.digitalbluebird.surveys.domain.port.outbound

import com.digitalbluebird.surveys.domain.SurveyPlotId
import com.digitalbluebird.surveys.domain.event.SurveyPlotEvent

/** Append-only event store for survey plots — the source of truth the aggregate is replayed from. */
interface SurveyPlotEventStore {
    /** The plot's events in stream order (sequence ascending); empty for a plot never touched. */
    fun load(plotId: SurveyPlotId): List<SurveyPlotEvent>

    /**
     * Append [event] at stream position [expectedVersion] (the count of events the caller replayed).
     * Returns false if another writer already appended at that position — optimistic concurrency on
     * the stream, enforced by the (plot_id, sequence) primary key.
     */
    fun append(plotId: SurveyPlotId, expectedVersion: Int, event: SurveyPlotEvent): Boolean
}
