package com.openlattice.rehearsal.requirements

import com.kryptnostic.rhizome.startup.Requirement
import com.openlattice.admin.BridgeService
import com.openlattice.admin.ServiceType
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit

/**
 *
 * @author Matthew Tamayo-Rios &lt;matthew@openlattice.com&gt;
 */
class ClusterStateRequirement(
        bridgeService: BridgeService,
        desiredCluster: Map<ServiceType, Int>,
        timeout: Long = 0,
        timeunit: TimeUnit = TimeUnit.MILLISECONDS
) : Requirement {
    companion object {
        private val logger = LoggerFactory.getLogger(ClusterStateRequirement::class.java)
    }

    private val satisfied: Boolean = try {
        bridgeService.awaitCluster(desiredCluster, timeout, timeunit)
        true
    } catch (ex: Exception) {
        logger.info("Cluster state requirement not met.", ex)
        false
    }

    override fun isSatisfied(): Boolean = satisfied


    override fun getDescription(): String = "Cluster state requirement"

}