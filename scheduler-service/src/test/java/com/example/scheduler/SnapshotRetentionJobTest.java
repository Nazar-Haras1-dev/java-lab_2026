package com.example.scheduler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SnapshotRetentionJobTest {

    @Mock
    private CatalogSnapshotService service;
    @InjectMocks
    private SnapshotRetentionJob job;

    private static JobExecutionContext contextWith(JobDataMap merged) {
        JobExecutionContext context = mock(JobExecutionContext.class);
        when(context.getMergedJobDataMap()).thenReturn(merged);
        return context;
    }

    @Test
    void execute_retentionDaysProvided_callsServiceOnceWithIt() throws Exception {
        // Arrange
        JobDataMap data = new JobDataMap();
        data.put("retentionDays", 180);

        // Act
        job.execute(contextWith(data));

        // Assert
        verify(service, times(1)).purgeOlderThan(180);
        verifyNoMoreInteractions(service);
    }

    @Test
    void execute_retentionDaysMissing_usesDocumentedDefault90() throws Exception {
        // Act
        job.execute(contextWith(new JobDataMap()));

        // Assert
        verify(service).purgeOlderThan(90);
    }

    @Test
    void execute_retentionDaysMalformed_failsWithoutCallingService() {
        // Arrange
        JobDataMap data = new JobDataMap();
        data.put("retentionDays", "ninety");

        // Act + Assert
        assertThatThrownBy(() -> job.execute(contextWith(data)))
                .isInstanceOf(JobExecutionException.class)
                .hasCauseInstanceOf(IllegalArgumentException.class);
        verifyNoMoreInteractions(service);
    }

    @Test
    void execute_serviceThrows_noRefireAndServiceCalledOnce() {
        // Arrange
        when(service.purgeOlderThan(90)).thenThrow(new IllegalStateException("db is down"));

        // Act
        Throwable thrown = catchThrowable(() -> job.execute(contextWith(new JobDataMap())));

        // Assert
        assertThat(thrown).isInstanceOf(JobExecutionException.class);
        assertThat(((JobExecutionException) thrown).refireImmediately()).isFalse();
        verify(service, times(1)).purgeOlderThan(90);
    }
}
