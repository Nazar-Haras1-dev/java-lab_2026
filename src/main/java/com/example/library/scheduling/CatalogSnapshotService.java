package com.example.library.scheduling;

import com.example.library.repository.BookRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * Уся бізнес-логіка запланованих завдань. Межа транзакції проходить тут (на рівні методів сервісу),
 * а не в Job. Обидві операції ідемпотентні: повторний запуск для тих самих даних нічого не змінює.
 */
@Service
@Transactional(readOnly = true)
public class CatalogSnapshotService {

    private final CatalogSnapshotRepository snapshots;
    private final BookRepository books;
    private final Clock clock;

    public CatalogSnapshotService(CatalogSnapshotRepository snapshots, BookRepository books, Clock clock) {
        this.snapshots = snapshots;
        this.books = books;
        // Спільний Clock із AopConfig - UTC; дата знімка має бути за київським часом, як і cron-тригери
        this.clock = clock.withZone(ZoneId.of(QuartzConfig.ZONE_ID));
    }

    /** @return true, якщо знімок створено; false, якщо за сьогодні він уже є (повторний запуск нічого не міняє). */
    @Transactional
    public boolean takeSnapshot(String source) {
        LocalDate today = LocalDate.now(clock);
        if (snapshots.existsBySnapshotDate(today)) {
            return false;
        }
        snapshots.save(new CatalogSnapshot(today, books.count(), source, Instant.now(clock)));
        return true;
    }

    /** Видаляє знімки старші за retentionDays днів. Повторний виклик видаляє 0. */
    @Transactional
    public int purgeOlderThan(int retentionDays) {
        if (retentionDays <= 0) {
            throw new IllegalArgumentException("retentionDays must be positive, got " + retentionDays);
        }
        return snapshots.deleteOlderThan(LocalDate.now(clock).minusDays(retentionDays));
    }

    public List<CatalogSnapshot> findAll() {
        return snapshots.findAllByOrderBySnapshotDateDesc();
    }
}
