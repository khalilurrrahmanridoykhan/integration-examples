package org.hisp.dhis.fhir.routes;

import lombok.RequiredArgsConstructor;
import org.apache.camel.builder.RouteBuilder;
import org.hisp.dhis.fhir.aggregate.ImmunizationBundleAggregationStrategy;
import org.hisp.dhis.fhir.config.properties.FhirProperties;
import org.hisp.dhis.fhir.converters.VaccinationEventToImmunizationConverter;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class VaccinationEventToFhirImmunizationRoute extends RouteBuilder
{
    private final FhirProperties fhirProperties;

    private final VaccinationEventToImmunizationConverter vaccinationEventToImmunizationConverter;

    @Override
    public void configure()
    {
        // timer and direct both feed the same logic, so a manual REST trigger
        // (SyncTriggerController -> direct:dhis2ToFhirSync) runs the identical route.
        from( "timer://vaccinationSync?period=3600000" ).routeId( "dhis2-vaccination-to-fhir-immunization-timer" )
            .to( "direct:dhis2ToFhirSync" );

        from( "direct:dhis2ToFhirSync" ).routeId( "dhis2-vaccination-to-fhir-immunization" )
            .setHeader( "CamelDhis2.queryParams",
                () -> Map.of( "program", "IpHINAT79UW", "programStage", "A03MvHHogjR", "orgUnit", "DiszpKrYNg8" ) )
            .to( "dhis2://get/collection?path=tracker/events&arrayName=events&client=#dhis2Client" )
            .log( "Converting DHIS2 vaccination events to FHIR immunizations..." )
            .split().body().aggregationStrategy( new ImmunizationBundleAggregationStrategy() )
                .bean( vaccinationEventToImmunizationConverter, "eventToImmunization" )
            .end()
            .marshal().fhirJson( fhirProperties.getFhirVersion().name(), true )
            .to( "fhir://transaction/withBundle?client=#fhirClient" )
            .log( "Saved immunizations FHIR bundle" );
    }
}
