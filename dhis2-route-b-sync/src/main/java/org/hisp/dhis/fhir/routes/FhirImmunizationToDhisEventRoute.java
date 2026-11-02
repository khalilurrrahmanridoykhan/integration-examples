package org.hisp.dhis.fhir.routes;

import ca.uhn.fhir.rest.client.api.IGenericClient;
import lombok.RequiredArgsConstructor;
import org.apache.camel.builder.RouteBuilder;
import org.hisp.dhis.fhir.config.ScheduleGate;
import org.hisp.dhis.fhir.config.SyncTypes;
import org.hisp.dhis.fhir.converters.FhirImmunizationToDhisEventConverter;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Immunization;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

// Route A: FHIR -> DHIS2 -- the reverse direction of VaccinationEventToFhirImmunizationRoute.
// The FHIR search/read calls run directly against the injected HAPI client (plain Java,
// not a Camel URI) for the same reason the converter does: this component's generic
// API-based lookup kept failing to match custom types during Route B's development.
@Component
@RequiredArgsConstructor
public class FhirImmunizationToDhisEventRoute extends RouteBuilder
{
    private final IGenericClient fhirClient;

    private final FhirImmunizationToDhisEventConverter fhirImmunizationToDhisEventConverter;

    private final ScheduleGate scheduleGate;

    @Override
    public void configure()
    {
        // Ticks every 5 minutes but mostly no-ops -- see OrgUnitToFhirBundleRoute's own
        // comment on ScheduleGate for why.
        from( "timer://fhirToDhisSyncTick?period=300000" ).routeId( "fhir-immunization-to-dhis2-event-tick" )
            .filter( exchange -> scheduleGate.shouldRunNow( SyncTypes.IMMUNIZATION_TO_DHIS2 ) )
            .to( "direct:fhirToDhis2Sync" );

        from( "direct:fhirToDhis2Sync" ).routeId( "fhir-immunization-to-dhis2-event" )
            .process( exchange -> {
                // Without an explicit count, HAPI's default search page size (20) silently
                // truncated this -- found live when a newly-added test Immunization past
                // the first page was never even looked at. 200 is a demo-scale ceiling, not
                // real pagination; a production version would page through the full result set.
                Bundle bundle = fhirClient.search().forResource( Immunization.class )
                    .count( 200 )
                    .returnBundle( Bundle.class )
                    .execute();

                List<Immunization> immunizations = bundle.getEntry().stream()
                    .map( entry -> (Immunization) entry.getResource() )
                    .collect( Collectors.toList() );

                exchange.getIn().setBody( immunizations );
            } )
            .log( "Converting FHIR immunizations to DHIS2 vaccination events..." )
            .split( body() )
                .bean( fhirImmunizationToDhisEventConverter, "immunizationToDhisEventPayload" )
                .filter( body().isNotNull() )
                    .to( "dhis2://post/resource?path=tracker&inBody=resource&client=#dhis2Client" )
                    .convertBodyTo( String.class )
                    // DHIS2's tracker import returns 200 even when it rejects every event
                    // inside the payload (validation errors live in the response body, not
                    // the HTTP status), so log the actual response rather than assuming success.
                    .log( "DHIS2 tracker import response: ${body}" )
                .end()
            .end();
    }
}
