package org.hisp.dhis.fhir.config;

import lombok.RequiredArgsConstructor;
import org.hisp.dhis.fhir.config.properties.DhisProperties;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;

@Configuration
@RequiredArgsConstructor
public class MainConfiguration
{
    private final DhisProperties dhisProperties;

    @Bean
    public RestTemplate restTemplate()
    {
        return new RestTemplateBuilder()
            .defaultMessageConverters()
            .defaultHeader( "Content-Type", MediaType.APPLICATION_JSON_VALUE )
            .defaultHeader( "Accept", MediaType.APPLICATION_JSON_VALUE )
            .basicAuthentication( dhisProperties.getUsername(), dhisProperties.getPassword(), StandardCharsets.UTF_8 )
            .build();
    }

    // Used by ScheduleController to schedule a one-time future run. In-memory only -- a
    // pending one-time run is lost on restart, same caveat as any other in-process
    // scheduled task (not persisted to the database like the recurring schedule is).
    @Bean
    public TaskScheduler taskScheduler()
    {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize( 2 );
        scheduler.setThreadNamePrefix( "sync-scheduler-" );
        scheduler.initialize();
        return scheduler;
    }
}
