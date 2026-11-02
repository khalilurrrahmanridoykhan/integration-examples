package org.hisp.dhis.fhir.routes;

import lombok.RequiredArgsConstructor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.RouteDefinition;
import org.hisp.dhis.fhir.aggregate.ImmunizationBundleAggregationStrategy;
import org.hisp.dhis.fhir.config.ScheduleGate;
import org.hisp.dhis.fhir.config.SyncTypes;
import org.hisp.dhis.fhir.config.VaccineCatalog;
import org.hisp.dhis.fhir.config.properties.DhisProperties;
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
    private final DhisProperties dhisProperties;

    private final VaccineCatalog vaccineCatalog;

    private final FhirProperties fhirProperties;

    private final VaccinationEventToImmunizationConverter vaccinationEventToImmunizationConverter;

    private final ScheduleGate scheduleGate;

    @Override
    public void configure()
    {
        // Ticks every 5 minutes but mostly no-ops -- see OrgUnitToFhirBundleRoute's own
        // comment on ScheduleGate for why.
        from( "timer://vaccinationSyncTick?period=300000" ).routeId( "dhis2-vaccination-to-fhir-immunization-tick" )
            .filter( exchange -> scheduleGate.shouldRunNow( SyncTypes.IMMUNIZATION_TO_FHIR ) )
            .to( "direct:dhis2ToFhirSync" );

        RouteDefinition route = from( "direct:dhis2ToFhirSync" ).routeId( "dhis2-vaccination-to-fhir-immunization" )
            .process( exchange -> exchange.setProperty( "combinedEvents", new ArrayList<>() ) );

        // One sequential DHIS2 call per program stage the vaccine catalog is configured with
        // (see VaccineCatalog / application.yml) -- however many that is, not a number fixed
        // in code. Each call goes through the same proven dhis2:// component, avoiding the
        // Dhis2Resource/Map type mismatch that caused real problems earlier in this route's
        // history.
        for ( String programStage : vaccineCatalog.programStages() )
        {
            route = route.setHeader( "CamelDhis2.queryParams",
                    () -> Map.of( "program", vaccineCatalog.getProgram(), "programStage", programStage,
                        "orgUnit", dhisProperties.getOrgUnit() ) )
                .to( "dhis2://get/collection?path=tracker/events&arrayName=events&client=#dhis2Client" )
                .process( exchange -> {
                    List<?> events = exchange.getIn().getBody( List.class );
                    List<Object> combined = exchange.getProperty( "combinedEvents", List.class );
                    if ( events != null )
                    {
                        combined.addAll( events );
                    }
                } );
        }

        route.process( exchange -> exchange.getIn().setBody( exchange.getProperty( "combinedEvents", List.class ) ) )
            .log( "Converting DHIS2 vaccination events to FHIR immunizations..." )
            .split().body().aggregationStrategy( new ImmunizationBundleAggregationStrategy() )
                .bean( vaccinationEventToImmunizationConverter, "eventToImmunization" )
            .end()
            .marshal().fhirJson( fhirProperties.getFhirVersion().name(), true )
            .to( "fhir://transaction/withBundle?client=#fhirClient" )
            .log( "Saved immunizations FHIR bundle" );
    }
}
