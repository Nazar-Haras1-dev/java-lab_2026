package com.example.scheduler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.quartz.CronScheduleBuilder;
import org.quartz.JobBuilder;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.impl.matchers.GroupMatcher;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class QuartzAdminControllerTest {

    @Mock
    private Scheduler scheduler;
    @Mock
    private CatalogSnapshotService snapshotService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        // Як у Spring Boot: дати серіалізуються як ISO-рядки, а не масиви чисел
        ObjectMapper mapper = Jackson2ObjectMapperBuilder.json()
                .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build();
        mockMvc = MockMvcBuilders.standaloneSetup(new QuartzAdminController(scheduler, snapshotService))
                .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper))
                .build();
    }

    @Test
    void runSnapshot_noSource_triggersJobWithoutOverrideAndReturns202() throws Exception {
        // Act + Assert
        mockMvc.perform(post("/api/admin/quartz/jobs/catalog-snapshot/run"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("triggered"));
        verify(scheduler).triggerJob(QuartzConfig.SNAPSHOT_JOB);
    }

    @Test
    void runSnapshot_withSource_passesSourceOverrideInJobDataMap() throws Exception {
        // Act
        mockMvc.perform(post("/api/admin/quartz/jobs/catalog-snapshot/run").param("source", "manual"))
                .andExpect(status().isAccepted());

        // Assert
        ArgumentCaptor<JobDataMap> captor = ArgumentCaptor.forClass(JobDataMap.class);
        verify(scheduler).triggerJob(any(), captor.capture());
        assertThat(captor.getValue().getString("source")).isEqualTo("manual");
    }

    @Test
    void snapshots_snapshotsExist_returnsThemAsJson() throws Exception {
        // Arrange
        when(snapshotService.findAll()).thenReturn(List.of(
                new CatalogSnapshot(LocalDate.of(2026, 11, 17), 5, "daily", Instant.parse("2026-11-17T00:00:00Z"))));

        // Act + Assert
        mockMvc.perform(get("/api/admin/quartz/snapshots"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].bookCount").value(5))
                .andExpect(jsonPath("$[0].source").value("daily"))
                .andExpect(jsonPath("$[0].snapshotDate").value("2026-11-17"));
    }

    @Test
    void jobs_cronTriggerScheduled_returnsJobWithTriggerMisfireAndTimeZone() throws Exception {
        // Arrange
        JobDetail detail = JobBuilder.newJob(com.example.scheduler.CatalogSnapshotJob.class)
                .withIdentity(QuartzConfig.SNAPSHOT_JOB).usingJobData("source", "daily").storeDurably().build();
        Trigger trigger = TriggerBuilder.newTrigger().withIdentity("t1", "library-maintenance").forJob(detail)
                .withSchedule(CronScheduleBuilder.cronSchedule("0 0 2 * * ?")
                        .inTimeZone(TimeZone.getTimeZone("Europe/Kyiv")).withMisfireHandlingInstructionDoNothing())
                .build();
        when(scheduler.getJobKeys(GroupMatcher.anyGroup())).thenReturn(Set.of(QuartzConfig.SNAPSHOT_JOB));
        when(scheduler.getTriggersOfJob(QuartzConfig.SNAPSHOT_JOB)).thenAnswer(i -> List.of(trigger));
        when(scheduler.getJobDetail(QuartzConfig.SNAPSHOT_JOB)).thenReturn(detail);

        // Act + Assert
        mockMvc.perform(get("/api/admin/quartz/jobs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("catalog-snapshot"))
                .andExpect(jsonPath("$[0].group").value("library-maintenance"))
                .andExpect(jsonPath("$[0].triggers[0].type").value("CRON"))
                .andExpect(jsonPath("$[0].triggers[0].timeZone").value("Europe/Kyiv"))
                .andExpect(jsonPath("$[0].triggers[0].misfireInstruction").value(2));
    }
}
