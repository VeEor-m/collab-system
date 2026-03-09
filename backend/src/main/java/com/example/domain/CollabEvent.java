package com.example.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "collab_events")
public class CollabEvent {

    @Id
    private String id;

    private UUID docId;

    private UUID userId;

    private byte[] update;

    private Instant timestamp;

    private boolean snapshotted;

    public static CollabEvent create(UUID docId, UUID userId, byte[] update) {
        return CollabEvent.builder()
                .docId(docId)
                .userId(userId)
                .update(update)
                .timestamp(Instant.now())
                .snapshotted(false)
                .build();
    }
}
