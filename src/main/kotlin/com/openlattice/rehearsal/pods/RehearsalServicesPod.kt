package com.openlattice.rehearsal.pods

import com.dataloom.mappers.ObjectMappers
import com.fasterxml.jackson.databind.ObjectMapper
import com.hazelcast.core.HazelcastInstance
import com.kryptnostic.rhizome.startup.Requirement
import com.openlattice.admin.BridgeAwareServices
import com.openlattice.admin.BridgeService
import com.openlattice.admin.ServiceDescription
import com.openlattice.admin.ServiceType
import com.openlattice.data.serializers.FullQualifiedNameJacksonSerializer
import com.openlattice.rehearsal.configuration.RehearsalConfiguration
import com.openlattice.rehearsal.requirements.ClusterStateRequirement
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import javax.annotation.PostConstruct
import javax.inject.Inject

/**
 *
 * @author Matthew Tamayo-Rios &lt;matthew@openlattice.com&gt;
 */
@Configuration
class RehearsalServicesPod {
    @Inject
    private lateinit var hazelcastInstance: HazelcastInstance

    @Inject
    private lateinit var rehearsalConfiguration: RehearsalConfiguration

    @Bean
    fun defaultObjectMapper(): ObjectMapper? {
        val mapper = ObjectMappers.getJsonMapper()
        FullQualifiedNameJacksonSerializer.registerWithMapper(mapper)
        return mapper
    }

    @Bean
    fun bridgeAwareServices(): BridgeAwareServices = BridgeAwareServices()

    @Bean
    fun bridgeService(): BridgeService = BridgeService(
            ServiceDescription(ServiceType.REHEARSAL),
            bridgeAwareServices(),
            hazelcastInstance
    )

    @Bean
    fun clusterStateRequirement(): Requirement {
        return ClusterStateRequirement(
                bridgeService(),
                rehearsalConfiguration.desiredCluster,
                rehearsalConfiguration.timeoutMillis
        )
    }

}