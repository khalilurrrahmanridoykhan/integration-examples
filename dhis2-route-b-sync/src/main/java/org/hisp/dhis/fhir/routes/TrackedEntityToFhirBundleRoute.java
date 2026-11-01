package org.hisp.dhis.fhir.routes;

import lombok.RequiredArgsConstructor;
import org.apache.camel.builder.RouteBuilder;
import org.hisp.dhis.fhir.aggregate.PatientBundleAggregationStrategy;
import org.hisp.dhis.fhir.config.properties.FhirProperties;
import org.hisp.dhis.fhir.converters.TrackedEntityToPatientConverter;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class TrackedEntityToFhirBundleRoute extends RouteBuilder
{
    private final FhirProperties fhirProperties;

    private final TrackedEntityToPatientConverter trackedEntityToPatientConverter;

    @Override
    public void configure()
    {
        // timer and direct both feed the same logic, so a manual REST trigger
        // (SyncTriggerController -> direct:trackedEntitiesToFhirSync) runs the identical route.
        from( "timer://teSync?period=3600000" ).routeId( "dhis2-te-to-fhir-bundle-timer" )
            .to( "direct:trackedEntitiesToFhirSync" );

        from( "direct:trackedEntitiesToFhirSync" ).routeId( "dhis2-te-to-fhir-bundle" )
            // orgUnits (plural) -- this endpoint ignores the singular "orgUnit"/"ou" entirely
            // (confirmed live: total stayed at 17,380 regardless of org unit with either of
            // those names, and only dropped to a real 36 with "orgUnits"). Inconsistent with
            // tracker/events, which uses the singular "orgUnit" -- a real API quirk, not a typo.
            .setHeader( "CamelDhis2.queryParams", () -> Map.of( "orgUnits", "DiszpKrYNg8", "program", "IpHINAT79UW" ) )
            .to("dhis2://get/collection?path=tracker/trackedEntities&arrayName=trackedEntities&client=#dhis2Client")
            .log( "Converting DHIS2 tracked entities to FHIR patients..." )
            .split().body().aggregationStrategy( new PatientBundleAggregationStrategy() )
                .bean( trackedEntityToPatientConverter, "trackedEntityResourceToPatient" )
            .end()
            .marshal().fhirJson( fhirProperties.getFhirVersion().name(), true )
            .to( "fhir://transaction/withBundle?client=#fhirClient" )
            .log( "Saved patients FHIR bundle" );
    }
}
