package org.hisp.dhis.fhir.config;

import org.hisp.dhis.fhir.domain.SyncSchedule;
import org.hisp.dhis.fhir.repository.SyncScheduleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith( MockitoExtension.class )
class ScheduleGateTest
{
    @Mock
    private SyncScheduleRepository syncScheduleRepository;

    private ScheduleGate gate;

    @BeforeEach
    void setUp()
    {
        gate = new ScheduleGate( syncScheduleRepository );
    }

    @Test
    void noScheduleRowYet_defaultsToAutomaticAndRunsImmediately()
    {
        when( syncScheduleRepository.findById( "immunization-to-fhir" ) ).thenReturn( Optional.empty() );

        assertTrue( gate.shouldRunNow( "immunization-to-fhir" ) );
        verify( syncScheduleRepository ).save( any( SyncSchedule.class ) );
    }

    @Test
    void manualMode_neverRunsRegardlessOfLastRunAt()
    {
        SyncSchedule schedule = new SyncSchedule( "org-units-to-fhir", "MANUAL", 1 );
        when( syncScheduleRepository.findById( "org-units-to-fhir" ) ).thenReturn( Optional.of( schedule ) );

        assertFalse( gate.shouldRunNow( "org-units-to-fhir" ) );
        verify( syncScheduleRepository, never() ).save( any() );
    }

    @Test
    void automaticMode_intervalNotYetElapsed_doesNotRun()
    {
        SyncSchedule schedule = new SyncSchedule( "option-sets-to-fhir", "AUTOMATIC", 6 );
        schedule.setLastRunAt( Instant.now().minus( 2, ChronoUnit.HOURS ) );
        when( syncScheduleRepository.findById( "option-sets-to-fhir" ) ).thenReturn( Optional.of( schedule ) );

        assertFalse( gate.shouldRunNow( "option-sets-to-fhir" ) );
        verify( syncScheduleRepository, never() ).save( any() );
    }

    @Test
    void automaticMode_intervalElapsed_runsAndStampsLastRunAt()
    {
        SyncSchedule schedule = new SyncSchedule( "tracked-entities-to-fhir", "AUTOMATIC", 1 );
        schedule.setLastRunAt( Instant.now().minus( 2, ChronoUnit.HOURS ) );
        when( syncScheduleRepository.findById( "tracked-entities-to-fhir" ) ).thenReturn( Optional.of( schedule ) );

        assertTrue( gate.shouldRunNow( "tracked-entities-to-fhir" ) );

        ArgumentCaptor<SyncSchedule> saved = ArgumentCaptor.forClass( SyncSchedule.class );
        verify( syncScheduleRepository ).save( saved.capture() );
        assertTrue( saved.getValue().getLastRunAt().isAfter( Instant.now().minus( 1, ChronoUnit.MINUTES ) ) );
    }

    @Test
    void markRanNow_stampsLastRunAtEvenWithNoPriorRow()
    {
        when( syncScheduleRepository.findById( "immunization-to-dhis2" ) ).thenReturn( Optional.empty() );

        gate.markRanNow( "immunization-to-dhis2" );

        ArgumentCaptor<SyncSchedule> saved = ArgumentCaptor.forClass( SyncSchedule.class );
        verify( syncScheduleRepository ).save( saved.capture() );
        assertTrue( saved.getValue().getLastRunAt().isAfter( Instant.now().minus( 1, ChronoUnit.MINUTES ) ) );
        assertTrue( saved.getValue().getSyncType().equals( "immunization-to-dhis2" ) );
    }
}
