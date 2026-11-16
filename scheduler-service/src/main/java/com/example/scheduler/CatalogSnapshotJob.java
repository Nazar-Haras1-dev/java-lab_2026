package com.example.scheduler;

import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Мінімалістичне завдання: читає параметри й робить ОДИН виклик сервісу. Своєї логіки не має.
 * Стратегія збою: помилка логується, повторний запуск НЕ вимагається (refireImmediately=false),
 * наступна спроба буде за розкладом. Нескінченного циклу не буває.
 * Паралельний запуск заборонено: два одночасних знімки зайві.
 */
@DisallowConcurrentExecution
public class CatalogSnapshotJob implements Job {

    private static final Logger log = LoggerFactory.getLogger(CatalogSnapshotJob.class);

    public static final String SOURCE_KEY = "source";
    public static final String DEFAULT_SOURCE = "daily";
    public static final String TRIGGERED_BY_KEY = "triggeredBy";
    public static final String DEFAULT_TRIGGERED_BY = "scheduler";

    private final CatalogSnapshotService service;

    public CatalogSnapshotJob(CatalogSnapshotService service) {
        this.service = service;
    }

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        try {
            JobDataMap data = context.getMergedJobDataMap();   // тригер перекриває значення Job
            String source = JobParams.string(data, SOURCE_KEY, DEFAULT_SOURCE);
            String triggeredBy = JobParams.string(data, TRIGGERED_BY_KEY, DEFAULT_TRIGGERED_BY);
            boolean created = service.takeSnapshot(source);
            log.info("CatalogSnapshotJob: source={} triggeredBy={} created={}", source, triggeredBy, created);
        } catch (RuntimeException e) {
            log.error("CatalogSnapshotJob failed, waiting for the next scheduled run: {}", e.toString());
            throw new JobExecutionException(e, false);
        }
    }
}
