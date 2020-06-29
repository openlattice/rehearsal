package com.openlattice.rehearsal.pods

import com.fasterxml.jackson.databind.ObjectMapper
import com.openlattice.data.DataApi
import com.openlattice.rehearsal.controllers.RehearsalController
import com.openlattice.web.converters.CsvHttpMessageConverter
import com.openlattice.web.converters.YamlHttpMessageConverter
import com.openlattice.web.mediatypes.CustomMediaType
import com.ryantenney.metrics.spring.config.annotation.EnableMetrics
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.FilterType
import org.springframework.http.MediaType
import org.springframework.http.converter.HttpMessageConverter
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.servlet.config.annotation.ContentNegotiationConfigurer
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurationSupport
import javax.inject.Inject

/**
 *
 * @author Matthew Tamayo-Rios &lt;matthew@openlattice.com&gt;
 */
@Configuration
@ComponentScan(
        basePackageClasses = [RehearsalController::class],
        includeFilters = [ComponentScan.Filter(
                value = [Controller::class, RestControllerAdvice::class],
                type = FilterType.ANNOTATION
        )]
)
@EnableAsync
@EnableMetrics(proxyTargetClass = true)
class RehearsalMvcPod : WebMvcConfigurationSupport() {
        @Inject
        private lateinit var defaultObjectMapper: ObjectMapper

        @Inject
        private lateinit var rehearsalSecurityPod: RehearsalSecurityPod

        override fun configureMessageConverters(
                converters: MutableList<HttpMessageConverter<*>?>
        ) {
                super.addDefaultHttpMessageConverters(converters)
                for (converter in converters) {
                        if (converter is MappingJackson2HttpMessageConverter) {
                                converter.objectMapper = defaultObjectMapper
                        }
                }
                converters.add(CsvHttpMessageConverter())
                converters.add(YamlHttpMessageConverter())
        }

        // TODO(LATTICE-2346): We need to lock this down. Since all endpoints are stateless + authenticated this is more a
        // defense-in-depth measure.
        @SuppressFBWarnings(value = ["PERMISSIVE_CORS"], justification = "LATTICE-2346")
        override fun addCorsMappings(registry: CorsRegistry) {
                registry
                        .addMapping("/**")
                        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH")
                        .allowedOrigins("*")
                super.addCorsMappings(registry)
        }

        override fun configureContentNegotiation(configurer: ContentNegotiationConfigurer) {
                configurer.parameterName(DataApi.FILE_TYPE)
                        .favorParameter(true)
                        .mediaType("csv", CustomMediaType.TEXT_CSV)
                        .mediaType("json", MediaType.APPLICATION_JSON)
                        .mediaType("yaml", CustomMediaType.TEXT_YAML)
                        .defaultContentType(MediaType.APPLICATION_JSON)
        }

        @Bean
        @Throws(Exception::class)
        fun authenticationManagerBean(): AuthenticationManager? {
                return rehearsalSecurityPod.authenticationManagerBean()
        }
}