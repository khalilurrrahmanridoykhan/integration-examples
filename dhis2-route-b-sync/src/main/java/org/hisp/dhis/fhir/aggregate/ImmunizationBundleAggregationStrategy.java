package org.hisp.dhis.fhir.aggregate;

import org.apache.camel.Exchange;
import org.hisp.dhis.fhir.domain.ImmunizationAndPatient;
import org.hl7.fhir.r4.model.Bundle;

import java.util.ArrayList;
import java.util.List;

public class ImmunizationBundleAggregationStrategy extends AbstractBundleAggregationStrategy
{
    @Override
    @SuppressWarnings( "unchecked" )
    protected List<Bundle.BundleEntryComponent> getEntries( Exchange exchange )
    {
        // One event can yield several vaccines (e.g. Birth stage: BCG + OPV), so the
        // converter returns a list -- possibly empty when nothing was given at all.
        List<ImmunizationAndPatient> immunizationsAndPatients =
            exchange.getIn().getBody( List.class );
        if ( immunizationsAndPatients == null || immunizationsAndPatients.isEmpty() )
        {
            return List.of();
        }

        List<Bundle.BundleEntryComponent> entries = new ArrayList<>();
        for ( ImmunizationAndPatient immunizationAndPatient : immunizationsAndPatients )
        {
            // One event can yield several vaccines sharing the same patient (e.g. BCG + OPV
            // from the same Birth-stage event), so without a fullUrl, HAPI sees two
            // "anonymous" entries with the identical conditional URL and rejects the whole
            // transaction (HAPI-2008) -- a fullUrl makes each Patient entry addressable, so
            // repeats across vaccines for the same patient no longer collide.
            String patientId = immunizationAndPatient.getPatient().getIdentifierFirstRep().getValue();
            Bundle.BundleEntryComponent patientEntry = new Bundle.BundleEntryComponent();
            patientEntry.setFullUrl( "urn:uuid:patient-" + patientId );
            patientEntry.setResource( immunizationAndPatient.getPatient() )
                .getRequest().setUrl( "Patient?identifier=" + patientId )
                .setMethod( Bundle.HTTPVerb.POST )
                .setIfNoneExist( "identifier=" + patientId );
            entries.add( patientEntry );

            String immunizationId = immunizationAndPatient.getImmunization().getIdentifierFirstRep().getValue();
            Bundle.BundleEntryComponent immunizationEntry = new Bundle.BundleEntryComponent();
            immunizationEntry.setFullUrl( "urn:uuid:immunization-" + immunizationId );
            immunizationEntry.setResource( immunizationAndPatient.getImmunization() )
                .getRequest().setUrl( "Immunization?identifier=" + immunizationId )
                .setMethod( Bundle.HTTPVerb.POST )
                .setIfNoneExist( "identifier=" + immunizationId );
            entries.add( immunizationEntry );
        }

        return entries;
    }

    @Override
    protected Bundle createBundle()
    {
        return new Bundle().setType( Bundle.BundleType.TRANSACTION );
    }
}
