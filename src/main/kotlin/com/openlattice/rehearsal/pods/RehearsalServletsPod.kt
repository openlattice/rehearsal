package com.openlattice.rehearsal.pods

import com.google.common.collect.Lists
import com.kryptnostic.rhizome.configuration.servlets.DispatcherServletConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 *
 * @author Matthew Tamayo-Rios &lt;matthew@openlattice.com&gt;
 */
@Configuration
class RehearsalServletsPod {
    @Bean
    fun edmServlet(): DispatcherServletConfiguration? {
        return DispatcherServletConfiguration(
                "datastore", arrayOf("/datastore/*"),
                1,
                Lists.newArrayList<Class<*>>(RehearsalMvcPod::class.java)
        )
    }
}