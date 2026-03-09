package com.example.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "document_snapshots")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "doc_id", nullable = false)
    private UUID docId;

    @Column(nullable = false)
    private Long version;

    @Lob
    @Column(name = "snapshot_data", columnDefinition = "BYTEA")
    private byte[] snapshotData;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        if (sizeBytes == null && snapshotData != null) {
            sizeBytes = (long) snapshotData.length;
        }
    }
}
