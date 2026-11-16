package com.example.library.scheduling;

import org.junit.jupiter.api.Test;
import org.quartz.CronTrigger;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.SimpleTrigger;
import org.quartz.Trigger;
import org.quartz.TriggerKey;
import org.quartz.impl.JobExecutionContextImpl;
import org.quartz.spi.OperableTrigger;
import org.quartz.spi.TriggerFiredBundle;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class QuartzConfigTest {

    private final QuartzConfig config = new QuartzConfig();

    private final JobDetail snapshotJob = config.catalogSnapshotJobDetail();
    private final JobDetail retentionJob = config.snapshotRetentionJobDetail();
    private final List<Trigger> triggers = List.of(
            config.catalogSnapshotDailyTrigger(),
            config.catalogSnapshotCatchUpTrigger(),
            config.snapshotRetentionWeeklyTrigger());

    /** Обʼєднання JobDataMap рівно так, як це робить Quartz при запуску (JobExecutionContextImpl). */
    private JobDataMap mergedFor(JobDetail job, Trigger trigger) {
        TriggerFiredBundle bundle = new TriggerFiredBundle(job, (OperableTrigger) trigger, null, false,
                new Date(), new Date(), null, null);
        return new JobExecutionContextImpl(null, bundle, null).getMergedJobDataMap();
    }

    // ---------- Завдання й тригери оголошені явно ----------

    @Test
    void jobDetails_declared_haveExplicitNameAndGroup() {
        // Assert
        assertThat(snapshotJob.getKey()).isEqualTo(JobKey.jobKey("catalog-snapshot", "library-maintenance"));
        assertThat(retentionJob.getKey()).isEqualTo(JobKey.jobKey("snapshot-retention", "library-maintenance"));
        assertThat(snapshotJob.getJobClass()).isEqualTo(CatalogSnapshotJob.class);
        assertThat(retentionJob.getJobClass()).isEqualTo(SnapshotRetentionJob.class);
    }

    @Test
    void triggers_declared_haveExplicitUniqueNamesAndPointToExistingJobsByJobKey() {
        // Arrange
        Set<JobKey> jobKeys = Set.of(snapshotJob.getKey(), retentionJob.getKey());

        // Assert
        for (Trigger t : triggers) {
            assertThat(t.getKey().getName()).isNotBlank();
            assertThat(t.getKey().getGroup()).isEqualTo("library-maintenance");
            assertThat(jobKeys).contains(t.getJobKey());
        }
        Set<TriggerKey> unique = triggers.stream().map(Trigger::getKey).collect(Collectors.toSet());
        assertThat(unique).hasSize(triggers.size());
    }

    @Test
    void snapshotJob_hasTwoTriggers() {
        // Act
        long count = triggers.stream().filter(t -> t.getJobKey().equals(snapshotJob.getKey())).count();

        // Assert
        assertThat(count).isEqualTo(2);
    }

    // ---------- Пріоритет даних тригера ----------

    @Test
    void mergedJobDataMap_triggerOverridesOneKey_otherJobDefaultsStay() {
        // Act
        JobDataMap merged = mergedFor(snapshotJob, config.catalogSnapshotCatchUpTrigger());

        // Assert
        assertThat(merged.getString("source")).isEqualTo("catch-up");          // з тригера
        assertThat(merged.getString("triggeredBy")).isEqualTo("scheduler");     // значення Job за замовчуванням
    }

    @Test
    void mergedJobDataMap_triggerWithoutOverride_usesJobDefaults() {
        // Act
        JobDataMap merged = mergedFor(snapshotJob, config.catalogSnapshotDailyTrigger());

        // Assert
        assertThat(merged.getString("source")).isEqualTo("daily");
    }

    @Test
    void mergedJobDataMap_retentionTrigger_overridesDefault90With180() {
        // Assert
        assertThat(retentionJob.getJobDataMap().getInt("retentionDays")).isEqualTo(90);
        assertThat(mergedFor(retentionJob, config.snapshotRetentionWeeklyTrigger()).getInt("retentionDays")).isEqualTo(180);
    }

    // ---------- Заборона паралельного запуску ----------

    @Test
    void jobDetails_declared_disallowConcurrentExecution() {
        assertThat(snapshotJob.isConcurrentExectionDisallowed()).isTrue();
        assertThat(retentionJob.isConcurrentExectionDisallowed()).isTrue();
    }

    // ---------- Misfire ----------

    @Test
    void dailyTrigger_misfireInstruction_isFireOnceNow() {
        assertThat(config.catalogSnapshotDailyTrigger().getMisfireInstruction())
                .isEqualTo(CronTrigger.MISFIRE_INSTRUCTION_FIRE_ONCE_NOW);
    }

    @Test
    void catchUpTrigger_misfireInstruction_isRescheduleNextWithRemainingCount() {
        assertThat(config.catalogSnapshotCatchUpTrigger().getMisfireInstruction())
                .isEqualTo(SimpleTrigger.MISFIRE_INSTRUCTION_RESCHEDULE_NEXT_WITH_REMAINING_COUNT);
    }

    @Test
    void weeklyRetentionTrigger_misfireInstruction_isDoNothing() {
        assertThat(config.snapshotRetentionWeeklyTrigger().getMisfireInstruction())
                .isEqualTo(CronTrigger.MISFIRE_INSTRUCTION_DO_NOTHING);
    }

    // ---------- Часовий пояс cron-тригерів ----------

    @Test
    void cronTriggers_declared_haveExplicitKyivTimeZoneNotGmt() {
        // Act
        CronTrigger daily = (CronTrigger) config.catalogSnapshotDailyTrigger();
        CronTrigger weekly = (CronTrigger) config.snapshotRetentionWeeklyTrigger();

        // Assert
        assertThat(daily.getTimeZone().getID()).isEqualTo("Europe/Kyiv");
        assertThat(weekly.getTimeZone().getID()).isEqualTo("Europe/Kyiv");
    }

    @Test
    void timeZone_unknownId_isSilentlyReplacedByGmtSoConfiguredIdMustBeVerified() {
        // Act + Assert: документуємо поведінку JDK, через яку ідентифікатор треба перевіряти тестом
        assertThat(TimeZone.getTimeZone("Europe/Nowhere").getID()).isEqualTo("GMT");
        assertThat(TimeZone.getTimeZone(QuartzConfig.ZONE_ID).getID()).isNotEqualTo("GMT");
    }

    @Test
    void dailyTrigger_nextFireTime_is0200KyivRegardlessOfServerZone() {
        // Arrange: 1 липня (літній час, UTC+3) і 1 грудня (UTC+2); дати в майбутньому, бо тригер стартує "зараз"
        CronTrigger daily = (CronTrigger) config.catalogSnapshotDailyTrigger();

        // Act
        Date summer = daily.getFireTimeAfter(Date.from(Instant.parse("2030-07-01T00:00:00Z")));
        Date winter = daily.getFireTimeAfter(Date.from(Instant.parse("2030-12-01T00:00:00Z")));

        // Assert
        assertThat(summer.toInstant()).isEqualTo(Instant.parse("2030-07-01T23:00:00Z"));   // 02:00 за UTC+3 = 23:00Z попереднього дня
        assertThat(winter.toInstant()).isEqualTo(Instant.parse("2030-12-02T00:00:00Z"));
    }
}
