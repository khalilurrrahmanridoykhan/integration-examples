package org.hisp.dhis.fhir.routes;

import ca.uhn.fhir.rest.client.api.IGenericClient;
import lombok.RequiredArgsConstructor;
import org.apache.camel.builder.RouteBuilder;
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

    @Override
    public void configure()
    {
        // timer and direct both feed the same logic, so a manual REST trigger
        // (SyncTriggerController -> direct:fhirToDhis2Sync) runs the identical route.
        from( "timer://fhirToDhisSync?period=3600000" ).routeId( "fhir-immunization-to-dhis2-event-timer" )
            .to( "direct:fhirToDhis2Sync" );

        from( "direct:fhirToDhis2Sync" ).routeId( "fhir-immunization-to-dhis2-event" )
            .process( exchange -> {
                Bundle bundle = fhirClient.search().forResource( Immunization.class )
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
