package com.example.library.controller;

import com.example.library.scheduling.CatalogSnapshot;
import com.example.library.scheduling.CatalogSnapshotService;
import com.example.library.scheduling.QuartzConfig;
import org.quartz.CronTrigger;
import org.quartz.JobDataMap;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.impl.matchers.GroupMatcher;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Лише для ADMIN (правило /api/admin/** у SecurityConfig). Дозволяє побачити розклад і запустити завдання вручну. */
@RestController
@RequestMapping("/api/admin/quartz")
public class QuartzAdminController {

    public record TriggerView(String name, String group, String type, Instant nextFireTime,
                              int misfireInstruction, String timeZone, Map<String, Object> data) { }

    public record JobView(String name, String group, boolean concurrentDisallowed, Map<String, Object> data,
                          List<TriggerView> triggers) { }

    public record SnapshotView(Long id, LocalDate snapshotDate, long bookCount, String source, Instant createdAt) { }

    private final Scheduler scheduler;
    private final CatalogSnapshotService snapshotService;

    public QuartzAdminController(Scheduler scheduler, CatalogSnapshotService snapshotService) {
        this.scheduler = scheduler;
        this.snapshotService = snapshotService;
    }

    @GetMapping("/jobs")
    public List<JobView> jobs() throws SchedulerException {
        List<JobView> result = new ArrayList<>();
        for (JobKey key : scheduler.getJobKeys(GroupMatcher.anyGroup())) {
            List<TriggerView> triggers = new ArrayList<>();
            for (Trigger t : scheduler.getTriggersOfJob(key)) {
                String zone = t instanceof CronTrigger c ? c.getTimeZone().getID() : null;
                triggers.add(new TriggerView(t.getKey().getName(), t.getKey().getGroup(),
                        typeOf(t),
                        t.getNextFireTime() == null ? null : t.getNextFireTime().toInstant(),
                        t.getMisfireInstruction(), zone, Map.copyOf(t.getJobDataMap().getWrappedMap())));
            }
            var detail = scheduler.getJobDetail(key);
            result.add(new JobView(key.getName(), key.getGroup(), detail.isConcurrentExectionDisallowed(),
                    Map.copyOf(detail.getJobDataMap().getWrappedMap()), triggers));
        }
        return result;
    }

    private static String typeOf(Trigger t) {
        return t instanceof CronTrigger ? "CRON" : "SIMPLE";
    }

    /** Негайний запуск завдання знімка (опційно з іншим source). Двічі підряд = лише один знімок за дату. */
    @PostMapping("/jobs/catalog-snapshot/run")
    public ResponseEntity<Map<String, String>> runSnapshot(@RequestParam(required = false) String source)
            throws SchedulerException {
        if (source == null) {
            scheduler.triggerJob(QuartzConfig.SNAPSHOT_JOB);
        } else {
            JobDataMap override = new JobDataMap();
            override.put("source", source);
            scheduler.triggerJob(QuartzConfig.SNAPSHOT_JOB, override);
        }
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of("status", "triggered"));
    }

    @GetMapping("/snapshots")
    public List<SnapshotView> snapshots() {
        return snapshotService.findAll().stream().map(QuartzAdminController::toView).toList();
    }

    private static SnapshotView toView(CatalogSnapshot s) {
        return new SnapshotView(s.getId(), s.getSnapshotDate(), s.getBookCount(), s.getSource(), s.getCreatedAt());
    }
}
