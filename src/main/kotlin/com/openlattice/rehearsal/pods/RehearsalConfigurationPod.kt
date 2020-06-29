package com.openlattice.rehearsal.pods

import com.kryptnostic.rhizome.pods.ConfigurationLoader
import com.openlattice.rehearsal.configuration.RehearsalConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import javax.inject.Inject

/**
 *
 * @author Matthew Tamayo-Rios &lt;matthew@openlattice.com&gt;
 */
@Configuration
class RehearsalConfigurationPod {
    @Inject
    private lateinit var configurationLoader : ConfigurationLoader

    @Bean
    fun rehearsalConfiguration() : RehearsalConfiguration {
        return configurationLoader.logAndLoad("rehearsal", RehearsalConfiguration::class.java)
    }
}