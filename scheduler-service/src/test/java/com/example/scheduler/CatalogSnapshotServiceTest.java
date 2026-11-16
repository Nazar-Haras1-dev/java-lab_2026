package com.example.scheduler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CatalogSnapshotServiceTest {

    // 2026-11-16 23:30 UTC = 2026-11-17 01:30 за Києвом (UTC+2). Годинник у тесті UTC, як у AopConfig: сервіс сам переводить у Київ
    private static final Instant NOW = Instant.parse("2026-11-16T23:30:00Z");

    @Mock
    private CatalogSnapshotRepository snapshots;
    @Mock
    private BookCountProvider books;

    private CatalogSnapshotService service;

    @BeforeEach
    void setUp() {
        service = new CatalogSnapshotService(snapshots, books, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void takeSnapshot_noSnapshotForToday_savesCountWithKyivDateAndReturnsTrue() {
        // Arrange
        when(snapshots.existsBySnapshotDate(LocalDate.of(2026, 11, 17))).thenReturn(false);
        when(books.countBooks()).thenReturn(12L);

        // Act
        boolean created = service.takeSnapshot("daily");

        // Assert
        ArgumentCaptor<CatalogSnapshot> captor = ArgumentCaptor.forClass(CatalogSnapshot.class);
        verify(snapshots).save(captor.capture());
        assertThat(created).isTrue();
        assertThat(captor.getValue().getSnapshotDate()).isEqualTo(LocalDate.of(2026, 11, 17));
        assertThat(captor.getValue().getBookCount()).isEqualTo(12L);
        assertThat(captor.getValue().getSource()).isEqualTo("daily");
        assertThat(captor.getValue().getCreatedAt()).isEqualTo(NOW);
    }

    @Test
    void takeSnapshot_snapshotForTodayExists_changesNothingAndReturnsFalse() {
        // Arrange
        when(snapshots.existsBySnapshotDate(LocalDate.of(2026, 11, 17))).thenReturn(true);

        // Act
        boolean created = service.takeSnapshot("catch-up");

        // Assert
        assertThat(created).isFalse();
        verify(snapshots, never()).save(any());
        verify(books, never()).countBooks();
    }

    @Test
    void takeSnapshot_runTwiceForSameDay_secondRunChangesNothing() {
        // Arrange: перший виклик - знімка немає, другий - вже є
        when(snapshots.existsBySnapshotDate(LocalDate.of(2026, 11, 17))).thenReturn(false, true);
        when(books.countBooks()).thenReturn(3L);

        // Act
        boolean first = service.takeSnapshot("daily");
        boolean second = service.takeSnapshot("daily");

        // Assert
        assertThat(first).isTrue();
        assertThat(second).isFalse();
        verify(snapshots, times(1)).save(any());
    }

    @Test
    void purgeOlderThan_validDays_deletesBeforeCutoffAndReturnsCount() {
        // Arrange
        when(snapshots.deleteOlderThan(LocalDate.of(2026, 10, 18))).thenReturn(4);

        // Act
        int removed = service.purgeOlderThan(30);

        // Assert
        assertThat(removed).isEqualTo(4);
        verify(snapshots).deleteOlderThan(LocalDate.of(2026, 10, 18));
    }

    @Test
    void purgeOlderThan_runTwice_secondRunRemovesNothing() {
        // Arrange
        when(snapshots.deleteOlderThan(any())).thenReturn(4, 0);

        // Act + Assert
        assertThat(service.purgeOlderThan(30)).isEqualTo(4);
        assertThat(service.purgeOlderThan(30)).isZero();
    }

    @Test
    void purgeOlderThan_nonPositiveDays_throwsAndDeletesNothing() {
        // Act + Assert
        assertThatThrownBy(() -> service.purgeOlderThan(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.purgeOlderThan(-1)).isInstanceOf(IllegalArgumentException.class);
        verify(snapshots, never()).deleteOlderThan(any());
    }

    @Test
    void findAll_snapshotsExist_returnsRepositoryOrder() {
        // Arrange
        List<CatalogSnapshot> list = List.of(new CatalogSnapshot(LocalDate.of(2026, 11, 17), 1, "daily", NOW));
        when(snapshots.findAllByOrderBySnapshotDateDesc()).thenReturn(list);

        // Act + Assert
        assertThat(service.findAll()).isSameAs(list);
    }
}
