package com.digitalbluebird.config

import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.method.HandlerTypePredicate
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/**
 * `demo` profile only: mount every REST controller under an `/api` prefix, so the demo container can
 * serve the hand-built SPA (ui/, on @raby/ripple) from the same origin without a reverse proxy — the
 * static assets stay at the root, the API moves under `/api`, and actuator endpoints stay under
 * `/actuator` (only @RestController mappings are prefixed). The SPA is built with `VITE_API_BASE=/api`
 * to match, and the Vite dev proxy already rewrites the `/api` prefix onto the backend, so dev and the
 * container line up.
 *
 * Prefixing is deliberately confined to this profile: the default/production controller paths
 * (`/species`, `/hides`, `/bookings`) are unchanged, so existing web tests and the api WiringTest —
 * which run with no active profile — are unaffected.
 */
@Configuration
@Profile("demo")
class DemoWebConfiguration : WebMvcConfigurer {

    override fun configurePathMatch(configurer: PathMatchConfigurer) {
        configurer.addPathPrefix("/api", HandlerTypePredicate.forAnnotation(RestController::class.java))
    }
}
