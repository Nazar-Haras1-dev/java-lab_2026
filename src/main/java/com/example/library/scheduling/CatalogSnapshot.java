package com.example.library.scheduling;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;

/** Знімок каталогу: скільки книг було в бібліотеці в певну дату. Одна дата - один знімок (UNIQUE). */
@Entity
@Table(name = "catalog_snapshots")
public class CatalogSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "snapshot_date", nullable = false, unique = true)
    private LocalDate snapshotDate;

    @Column(name = "book_count", nullable = false)
    private long bookCount;

    @Column(nullable = false, length = 50)
    private String source;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected CatalogSnapshot() {
    }

    public CatalogSnapshot(LocalDate snapshotDate, long bookCount, String source, Instant createdAt) {
        this.snapshotDate = snapshotDate;
        this.bookCount = bookCount;
        this.source = source;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public LocalDate getSnapshotDate() { return snapshotDate; }
    public long getBookCount() { return bookCount; }
    public String getSource() { return source; }
    public Instant getCreatedAt() { return createdAt; }
}
