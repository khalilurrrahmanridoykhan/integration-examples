package org.hisp.dhis.fhir.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VaccineConfigTest
{
    @Test
    void booleanStyle_isGiven_onlyWhenValueIsLiteralTrue()
    {
        VaccineConfig bcg = new VaccineConfig( "bx6fsa0t90x", "stage", "19", "BCG", true );

        assertTrue( bcg.isGiven( "true" ) );
        assertFalse( bcg.isGiven( "false" ) );
        assertFalse( bcg.isGiven( null ) );
        // DHIS2's demo data was observed live to contain "Yes"/"No" for some entities --
        // deliberately not treated as given, since only a literal "true" is unambiguous.
        assertFalse( bcg.isGiven( "Yes" ) );
    }

    @Test
    void textStyle_isGiven_wheneverNonBlank()
    {
        VaccineConfig penta = new VaccineConfig( "vTUhAUZFoys", "stage", "110", "Penta", false );

        assertTrue( penta.isGiven( "1" ) );
        assertTrue( penta.isGiven( "2" ) );
        assertFalse( penta.isGiven( null ) );
        assertFalse( penta.isGiven( "" ) );
        assertFalse( penta.isGiven( "   " ) );
    }

    @Test
    void valueWhenGiven_matchesEachStylesOwnConvention()
    {
        VaccineConfig booleanStyle = new VaccineConfig( "d1", "stage", "19", "BCG", true );
        VaccineConfig textStyle = new VaccineConfig( "d2", "stage", "107", "DTaP", false );

        assertEquals( "true", booleanStyle.valueWhenGiven() );
        assertEquals( "1", textStyle.valueWhenGiven() );
    }
}
