package com.openlattice.rehearsal.configuration

import com.kryptnostic.rhizome.configuration.annotation.ReloadableConfiguration
import com.openlattice.admin.ServiceType

/**
 *
 * @author Matthew Tamayo-Rios &lt;matthew@openlattice.com&gt;
 */
@ReloadableConfiguration(uri = "rehearsal.yaml")
data class RehearsalConfiguration(
        val desiredCluster: Map<ServiceType, Int>,
        val timeoutMillis: Long = 0
)