package org.hisp.dhis.fhir.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

// Allows the DHIS2 console (a separate browser origin from this service) to call
// /api/** directly. Open to any origin since the DHIS2 instance's own origin varies
// per deployment -- tighten this to a specific origin before running against anything
// other than a local dev DHIS2 instance.
@Configuration
public class CorsConfig implements WebMvcConfigurer
{
    @Override
    public void addCorsMappings( CorsRegistry registry )
    {
        registry.addMapping( "/api/**" )
            .allowedOrigins( "*" )
            .allowedMethods( "GET", "POST" );
    }
}
