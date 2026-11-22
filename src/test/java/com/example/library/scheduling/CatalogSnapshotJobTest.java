package com.example.library.scheduling;

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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CatalogSnapshotJobTest {

    @Mock
    private CatalogSnapshotService service;
    @InjectMocks
    private CatalogSnapshotJob job;

    private static JobExecutionContext contextWith(JobDataMap merged) {
        JobExecutionContext context = mock(JobExecutionContext.class);
        when(context.getMergedJobDataMap()).thenReturn(merged);
        return context;
    }

    @Test
    void execute_sourceProvided_callsServiceExactlyOnceWithIt() throws Exception {
        // Arrange
        JobDataMap data = new JobDataMap();
        data.put("source", "catch-up");

        // Act
        job.execute(contextWith(data));

        // Assert
        verify(service, times(1)).takeSnapshot("catch-up");
        verifyNoMoreInteractions(service);
    }

    @Test
    void execute_sourceMissing_usesDocumentedDefaultDaily() throws Exception {
        // Act
        job.execute(contextWith(new JobDataMap()));

        // Assert
        verify(service).takeSnapshot("daily");
    }

    @Test
    void execute_sourceHasWrongType_failsWithoutRefireAndDoesNotCallService() {
        // Arrange
        JobDataMap data = new JobDataMap();
        data.put("source", 123);

        // Act + Assert
        assertThatThrownBy(() -> job.execute(contextWith(data)))
                .isInstanceOf(JobExecutionException.class)
                .hasCauseInstanceOf(IllegalArgumentException.class);
        verifyNoMoreInteractions(service);
    }

    @Test
    void execute_serviceThrows_errorIsRecordedWithoutRefireAndServiceCalledOnlyOnce() {
        // Arrange
        when(service.takeSnapshot("daily")).thenThrow(new IllegalStateException("db is down"));

        // Act
        Throwable thrown = org.assertj.core.api.Assertions.catchThrowable(() -> job.execute(contextWith(new JobDataMap())));

        // Assert: refireImmediately=false => ніякого нескінченного циклу, чекаємо наступного запуску за розкладом
        assertThat(thrown).isInstanceOf(JobExecutionException.class).hasCauseInstanceOf(IllegalStateException.class);
        assertThat(((JobExecutionException) thrown).refireImmediately()).isFalse();
        verify(service, times(1)).takeSnapshot("daily");
    }
}
