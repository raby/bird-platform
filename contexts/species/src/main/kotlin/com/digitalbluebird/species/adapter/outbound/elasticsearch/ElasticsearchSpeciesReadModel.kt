package com.digitalbluebird.species.adapter.outbound.elasticsearch

import co.elastic.clients.elasticsearch.ElasticsearchClient
import co.elastic.clients.elasticsearch._types.Refresh
import co.elastic.clients.elasticsearch.core.BulkRequest
import com.digitalbluebird.shared.domain.SpeciesId
import com.digitalbluebird.species.domain.Species
import com.digitalbluebird.species.domain.port.outbound.SpeciesReadModel

class ElasticsearchSpeciesReadModel(
    private val client: ElasticsearchClient,
    private val indexName: String = INDEX_NAME,
) : SpeciesReadModel {

    override fun indexAll(species: List<Species>) {
        if (species.isEmpty()) return
        ensureIndexExists()

        val request = BulkRequest.Builder()
            .index(indexName)
            .refresh(Refresh.True)
            .operations(
                species.map { sp ->
                    co.elastic.clients.elasticsearch.core.bulk.BulkOperation.Builder()
                        .index { idx ->
                            idx.id(sp.id.value).document(SpeciesDocument.from(sp))
                        }
                        .build()
                },
            )
            .build()

        val response = client.bulk(request)
        check(!response.errors()) {
            "bulk index reported errors: " +
                response.items().filter { it.error() != null }.map { it.error()?.reason() }
        }
    }

    override fun search(query: String, limit: Int): List<Species> {
        ensureIndexExists()
        val response = client.search(
            { s ->
                s.index(indexName)
                    .size(limit)
                    .query { q ->
                        q.multiMatch { mm ->
                            mm.query(query)
                                .fields("scientificName^2", "commonNames.name")
                                .fuzziness("AUTO")
                        }
                    }
            },
            SpeciesDocument::class.java,
        )
        return response.hits().hits().mapNotNull { it.source()?.toDomain() }
    }

    override fun findById(id: SpeciesId): Species? {
        ensureIndexExists()
        val response = client.get(
            { g -> g.index(indexName).id(id.value) },
            SpeciesDocument::class.java,
        )
        return if (response.found()) response.source()?.toDomain() else null
    }

    private fun ensureIndexExists() {
        val exists = client.indices().exists { e -> e.index(indexName) }.value()
        if (!exists) {
            client.indices().create { c -> c.index(indexName) }
        }
    }

    companion object {
        const val INDEX_NAME = "species"
    }
}
