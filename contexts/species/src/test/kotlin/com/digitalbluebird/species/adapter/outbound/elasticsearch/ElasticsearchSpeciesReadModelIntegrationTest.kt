package com.digitalbluebird.species.adapter.outbound.elasticsearch

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import co.elastic.clients.elasticsearch.ElasticsearchClient
import co.elastic.clients.json.jackson.JacksonJsonpMapper
import co.elastic.clients.transport.rest_client.RestClientTransport
import com.digitalbluebird.shared.domain.SpeciesId
import com.digitalbluebird.species.adapter.outbound.taxonomy.StubTaxonomyGateway
import com.digitalbluebird.species.domain.CommonName
import com.digitalbluebird.species.domain.ConservationStatus
import com.digitalbluebird.species.domain.Species
import com.digitalbluebird.species.domain.TaxonomicClassification
import org.apache.http.HttpHost
import org.elasticsearch.client.RestClient
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.testcontainers.DockerClientFactory
import org.testcontainers.elasticsearch.ElasticsearchContainer
import org.testcontainers.utility.DockerImageName

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ElasticsearchSpeciesReadModelIntegrationTest {

    private val elasticsearch = ElasticsearchContainer(
        DockerImageName.parse("docker.elastic.co/elasticsearch/elasticsearch:8.15.3"),
    )
        .withEnv("xpack.security.enabled", "false")
        .withEnv("discovery.type", "single-node")
        .withEnv("ES_JAVA_OPTS", "-Xms512m -Xmx512m")

    private lateinit var client: ElasticsearchClient
    private lateinit var restClient: RestClient
    private lateinit var readModel: ElasticsearchSpeciesReadModel

    @BeforeAll
    fun setUp() {
        assumeTrue(DockerClientFactory.instance().isDockerAvailable, "Docker unavailable — skipping integration test")
        elasticsearch.start()

        restClient = RestClient.builder(HttpHost.create("http://${elasticsearch.httpHostAddress}")).build()
        val transport = RestClientTransport(restClient, JacksonJsonpMapper())
        client = ElasticsearchClient(transport)
        readModel = ElasticsearchSpeciesReadModel(client, indexName = "species-test")

        readModel.indexAll(StubTaxonomyGateway().fetchSpecies())
    }

    @AfterAll
    fun tearDown() {
        if (::restClient.isInitialized) restClient.close()
        if (elasticsearch.isRunning) elasticsearch.stop()
    }

    @Test
    fun `search on common name returns matching species`() {
        val hits = readModel.search("robin", 10)
        assertThat(hits.size).isEqualTo(1)
        assertThat(hits.single().scientificName).isEqualTo("Erithacus rubecula")
    }

    @Test
    fun `search on scientific name returns the species`() {
        val hits = readModel.search("Apus apus", 10)
        assertThat(hits.map { it.scientificName }).isEqualTo(listOf("Apus apus"))
    }

    @Test
    fun `search with fuzziness tolerates a typo`() {
        val hits = readModel.search("peregrne", 10)
        assertThat(hits.map { it.scientificName }).isEqualTo(listOf("Falco peregrinus"))
    }

    @Test
    fun `findById returns the document round-trip`() {
        val found = readModel.findById(SpeciesId("apus-apus"))
        assertThat(found).isNotNull()
        assertThat(found!!.scientificName).isEqualTo("Apus apus")
        assertThat(found.taxonomy.family).isEqualTo("Apodidae")
    }

    @Test
    fun `findById returns null for missing id`() {
        assertThat(readModel.findById(SpeciesId("does-not-exist"))).isNull()
    }

    @Test
    fun `indexing a fresh species makes it immediately searchable`() {
        val newSpecies = Species(
            id = SpeciesId("parus-major"),
            scientificName = "Parus major",
            commonNames = listOf(CommonName("en", "Great Tit")),
            taxonomy = TaxonomicClassification("Animalia", "Chordata", "Aves", "Passeriformes", "Paridae", "Parus"),
            conservationStatus = ConservationStatus.LEAST_CONCERN,
        )
        readModel.indexAll(listOf(newSpecies))
        val hits = readModel.search("Great Tit", 10)
        assertThat(hits.map { it.id.value }).isEqualTo(listOf("parus-major"))
    }
}
