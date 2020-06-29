package com.openlattice.rehearsal.pods

import com.openlattice.auth0.Auth0SecurityPod
import com.ryantenney.metrics.spring.config.annotation.EnableMetrics
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity

/**
 *
 * @author Matthew Tamayo-Rios &lt;matthew@openlattice.com&gt;
 */
@Configuration
@EnableGlobalMethodSecurity(prePostEnabled = true)
@EnableWebSecurity(debug = false)
@EnableMetrics
class RehearsalSecurityPod : Auth0SecurityPod() {
    @Throws(Exception::class)
    override fun authorizeRequests(http: HttpSecurity) {
        //TODO: Lock these down
        http.authorizeRequests()
                .antMatchers(HttpMethod.OPTIONS).permitAll()
                .antMatchers("/rehearsal/**").authenticated()
    }
}