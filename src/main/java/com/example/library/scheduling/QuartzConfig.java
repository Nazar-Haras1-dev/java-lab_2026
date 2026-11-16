package com.example.library.scheduling;

import org.quartz.CronScheduleBuilder;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.TimeZone;

/**
 * Усі завдання (JobDetail) і тригери (Trigger) оголошені в одному місці.
 * Кожен тригер вказує на своє завдання через JobKey; імена й групи задані явно.
 * Розклад зберігається в БД (JDBC job store), тому переживає перезапуск; spring.quartz.overwrite-existing-jobs=true
 * оновлює визначення при розгортанні нової версії, не дублюючи завдань.
 */
@Configuration
public class QuartzConfig {

    public static final String GROUP = "library-maintenance";
    public static final String ZONE_ID = "Europe/Kyiv";

    public static final JobKey SNAPSHOT_JOB = JobKey.jobKey("catalog-snapshot", GROUP);
    public static final JobKey RETENTION_JOB = JobKey.jobKey("snapshot-retention", GROUP);

    // ---------- Завдання 1: щоденний знімок каталогу (має ДВА тригери) ----------

    @Bean
    public JobDetail catalogSnapshotJobDetail() {
        return JobBuilder.newJob(CatalogSnapshotJob.class)
                .withIdentity(SNAPSHOT_JOB)
                .withDescription("Stores the number of books in the catalog once per day")
                .usingJobData(CatalogSnapshotJob.SOURCE_KEY, CatalogSnapshotJob.DEFAULT_SOURCE)
                .usingJobData(CatalogSnapshotJob.TRIGGERED_BY_KEY, CatalogSnapshotJob.DEFAULT_TRIGGERED_BY)
                .storeDurably()
                .build();
    }

    /** Щодня о 02:00 за Києвом. Якщо запуск пропущено, виконати один раз одразу (FIRE_ONCE_NOW). */
    @Bean
    public Trigger catalogSnapshotDailyTrigger() {
        return TriggerBuilder.newTrigger()
                .forJob(SNAPSHOT_JOB)
                .withIdentity("catalog-snapshot-daily", GROUP)
                .withSchedule(CronScheduleBuilder.cronSchedule("0 0 2 * * ?")
                        .inTimeZone(TimeZone.getTimeZone(ZONE_ID))
                        .withMisfireHandlingInstructionFireAndProceed())
                .build();
    }

    /**
     * Страхувальний запуск кожні 6 годин (і при старті): якщо вночі застосунок не працював, знімок усе одно зʼявиться.
     * Перекриває параметр source. Дубля не буде, бо takeSnapshot ідемпотентний.
     */
    @Bean
    public Trigger catalogSnapshotCatchUpTrigger() {
        return TriggerBuilder.newTrigger()
                .forJob(SNAPSHOT_JOB)
                .withIdentity("catalog-snapshot-catchup", GROUP)
                .usingJobData(CatalogSnapshotJob.SOURCE_KEY, "catch-up")
                .startNow()
                .withSchedule(SimpleScheduleBuilder.simpleSchedule()
                        .withIntervalInHours(6)
                        .repeatForever()
                        .withMisfireHandlingInstructionNextWithRemainingCount())
                .build();
    }

    // ---------- Завдання 2: очищення старих знімків ----------

    @Bean
    public JobDetail snapshotRetentionJobDetail() {
        return JobBuilder.newJob(SnapshotRetentionJob.class)
                .withIdentity(RETENTION_JOB)
                .withDescription("Deletes catalog snapshots older than retentionDays")
                .usingJobData(SnapshotRetentionJob.RETENTION_DAYS_KEY, SnapshotRetentionJob.DEFAULT_RETENTION_DAYS)
                .usingJobData(CatalogSnapshotJob.TRIGGERED_BY_KEY, CatalogSnapshotJob.DEFAULT_TRIGGERED_BY)
                .storeDurably()
                .build();
    }

    /** Щонеділі о 03:00 за Києвом; пропущений запуск ігнорується (DO_NOTHING), чекаємо наступної неділі. */
    @Bean
    public Trigger snapshotRetentionWeeklyTrigger() {
        return TriggerBuilder.newTrigger()
                .forJob(RETENTION_JOB)
                .withIdentity("snapshot-retention-weekly", GROUP)
                .usingJobData(SnapshotRetentionJob.RETENTION_DAYS_KEY, 180)
                .withSchedule(CronScheduleBuilder.cronSchedule("0 0 3 ? * SUN")
                        .inTimeZone(TimeZone.getTimeZone(ZONE_ID))
                        .withMisfireHandlingInstructionDoNothing())
                .build();
    }
}
