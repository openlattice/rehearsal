package com.openlattice.rehearsal

import com.dataloom.mappers.ObjectMappers
import com.kryptnostic.rhizome.configuration.websockets.BaseRhizomeServer
import com.kryptnostic.rhizome.core.RhizomeApplicationServer
import com.kryptnostic.rhizome.hazelcast.serializers.RhizomeUtils
import com.openlattice.auditing.pods.AuditingConfigurationPod
import com.openlattice.auth0.Auth0Pod
import com.openlattice.aws.AwsS3Pod
import com.openlattice.data.serializers.FullQualifiedNameJacksonSerializer
import com.openlattice.datastore.pods.ByteBlobServicePod
import com.openlattice.hazelcast.pods.SharedStreamSerializersPod
import com.openlattice.jdbc.JdbcPod
import com.openlattice.rehearsal.pods.RehearsalConfigurationPod
import com.openlattice.rehearsal.pods.RehearsalSecurityPod
import com.openlattice.rehearsal.pods.RehearsalServicesPod
import com.openlattice.rehearsal.pods.RehearsalServletsPod

/**
 *
 * @author Matthew Tamayo-Rios &lt;matthew@openlattice.com&gt;
 */
class Rehearsal : BaseRhizomeServer(  *RhizomeUtils.Pods.concatenate(
        webPods,
        RhizomeApplicationServer.DEFAULT_PODS,
        rehearsalPods
)) {
    companion object {
        private val rehearsalPods = arrayOf(
                AuditingConfigurationPod::class.java,
                Auth0Pod::class.java,
                AwsS3Pod::class.java,
                RehearsalConfigurationPod::class.java,
                RehearsalServicesPod::class.java,
                JdbcPod::class.java,
//                PostgresPod::class.java,
                SharedStreamSerializersPod::class.java
        )

        private val webPods = arrayOf(
                RehearsalServletsPod::class.java,
                RehearsalSecurityPod::class.java
        )

        init {
            ObjectMappers.foreach(FullQualifiedNameJacksonSerializer::registerWithMapper)
        }

        @JvmStatic
        fun main(args: Array<String>) {
            Rehearsal().start(*args)
        }
    }
}