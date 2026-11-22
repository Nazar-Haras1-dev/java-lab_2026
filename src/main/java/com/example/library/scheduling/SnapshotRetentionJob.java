package com.example.library.scheduling;

import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Мінімалістичне завдання очищення старих знімків. Та сама стратегія збою, що й у CatalogSnapshotJob. */
@DisallowConcurrentExecution
public class SnapshotRetentionJob implements Job {

    private static final Logger log = LoggerFactory.getLogger(SnapshotRetentionJob.class);

    public static final String RETENTION_DAYS_KEY = "retentionDays";
    public static final int DEFAULT_RETENTION_DAYS = 90;

    private final CatalogSnapshotService service;

    public SnapshotRetentionJob(CatalogSnapshotService service) {
        this.service = service;
    }

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        try {
            JobDataMap data = context.getMergedJobDataMap();
            int retentionDays = JobParams.positiveInt(data, RETENTION_DAYS_KEY, DEFAULT_RETENTION_DAYS);
            String triggeredBy = JobParams.string(data, CatalogSnapshotJob.TRIGGERED_BY_KEY,
                    CatalogSnapshotJob.DEFAULT_TRIGGERED_BY);
            int removed = service.purgeOlderThan(retentionDays);
            log.info("SnapshotRetentionJob: retentionDays={} triggeredBy={} removed={}", retentionDays, triggeredBy, removed);
        } catch (RuntimeException e) {
            log.error("SnapshotRetentionJob failed, waiting for the next scheduled run: {}", e.toString());
            throw new JobExecutionException(e, false);
        }
    }
}
