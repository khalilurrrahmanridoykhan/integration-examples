package org.hisp.dhis.fhir.routes;

import lombok.RequiredArgsConstructor;
import org.apache.camel.builder.RouteBuilder;
import org.hisp.dhis.fhir.aggregate.ImmunizationBundleAggregationStrategy;
import org.hisp.dhis.fhir.config.VaccineCatalog;
import org.hisp.dhis.fhir.config.properties.FhirProperties;
import org.hisp.dhis.fhir.converters.VaccinationEventToImmunizationConverter;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class VaccinationEventToFhirImmunizationRoute extends RouteBuilder
{
    private static final String ORG_UNIT = "DiszpKrYNg8";

    private final FhirProperties fhirProperties;

    private final VaccinationEventToImmunizationConverter vaccinationEventToImmunizationConverter;

    @Override
    public void configure()
    {
        // timer and direct both feed the same logic, so a manual REST trigger
        // (SyncTriggerController -> direct:dhis2ToFhirSync) runs the identical route.
        from( "timer://vaccinationSync?period=3600000" ).routeId( "dhis2-vaccination-to-fhir-immunization-timer" )
            .to( "direct:dhis2ToFhirSync" );

        // Fetches both program stages that carry vaccine data elements (see VaccineCatalog)
        // as two sequential DHIS2 calls, combined into one list before the split -- each call
        // goes through the same proven dhis2:// component, avoiding the Dhis2Resource/Map type
        // mismatch that caused real problems earlier in this route's history.
        from( "direct:dhis2ToFhirSync" ).routeId( "dhis2-vaccination-to-fhir-immunization" )
            .setHeader( "CamelDhis2.queryParams",
                () -> Map.of( "program", VaccineCatalog.PROGRAM, "programStage", VaccineCatalog.BIRTH_STAGE,
                    "orgUnit", ORG_UNIT ) )
            .to( "dhis2://get/collection?path=tracker/events&arrayName=events&client=#dhis2Client" )
            .setProperty( "birthStageEvents", body() )
            .setHeader( "CamelDhis2.queryParams",
                () -> Map.of( "program", VaccineCatalog.PROGRAM, "programStage", VaccineCatalog.BABY_POSTNATAL_STAGE,
                    "orgUnit", ORG_UNIT ) )
            .to( "dhis2://get/collection?path=tracker/events&arrayName=events&client=#dhis2Client" )
            .process( exchange -> {
                List<Object> combined = new ArrayList<>();
                List<?> birthStageEvents = exchange.getProperty( "birthStageEvents", List.class );
                if ( birthStageEvents != null )
                {
                    combined.addAll( birthStageEvents );
                }
                List<?> babyPostnatalEvents = exchange.getIn().getBody( List.class );
                if ( babyPostnatalEvents != null )
                {
                    combined.addAll( babyPostnatalEvents );
                }
                exchange.getIn().setBody( combined );
            } )
            .log( "Converting DHIS2 vaccination events to FHIR immunizations..." )
            .split().body().aggregationStrategy( new ImmunizationBundleAggregationStrategy() )
                .bean( vaccinationEventToImmunizationConverter, "eventToImmunization" )
            .end()
            .marshal().fhirJson( fhirProperties.getFhirVersion().name(), true )
            .to( "fhir://transaction/withBundle?client=#fhirClient" )
            .log( "Saved immunizations FHIR bundle" );
    }
}
