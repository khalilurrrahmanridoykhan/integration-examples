package org.hisp.dhis.fhir.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.hl7.fhir.r4.model.Immunization;
import org.hl7.fhir.r4.model.Patient;

@RequiredArgsConstructor
@Getter
public class ImmunizationAndPatient
{
    private final Immunization immunization;
    private final Patient patient;
}
