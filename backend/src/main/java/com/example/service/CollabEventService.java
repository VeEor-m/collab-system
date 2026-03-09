package com.example.service;

import com.example.domain.CollabEvent;
import com.example.domain.DocumentSnapshot;
import com.example.domain.repository.CollabEventRepository;
import com.example.domain.repository.DocumentSnapshotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CollabEventService {

    private final CollabEventRepository collabEventRepository;
    private final DocumentSnapshotRepository snapshotRepository;
    private final MongoTemplate mongoTemplate;

    @Value("${app.snapshot.interval-minutes:5}")
    private int snapshotIntervalMinutes;

    @Value("${app.snapshot.max-pending-events:100}")
    private int maxPendingEvents;

    public void appendEvent(UUID docId, UUID userId, byte[] update) {
        CollabEvent event = CollabEvent.create(docId, userId, update);
        collabEventRepository.save(event);
        log.debug("Appended collab event for doc: {}, user: {}", docId, userId);
    }

    public List<CollabEvent> getEventsForDoc(UUID docId) {
        return collabEventRepository.findByDocIdOrderByTimestampDesc(docId);
    }

    public Optional<DocumentSnapshot> getSnapshot(UUID docId, Long version) {
        return snapshotRepository.findByDocIdAndVersion(docId, version);
    }

    public List<DocumentSnapshot> getSnapshotHistory(UUID docId) {
        return snapshotRepository.findByDocIdOrderByVersionDesc(docId);
    }

    public Optional<DocumentSnapshot> getLatestSnapshot(UUID docId) {
        return snapshotRepository.findByDocIdOrderByVersionDesc(docId)
                .stream()
                .findFirst();
    }

    @Scheduled(cron = "0 */5 * * * *")
    @Transactional
    public void generateSnapshots() {
        log.info("Starting snapshot generation task");

        List<CollabEvent> pendingEvents = collabEventRepository
                .findAll()
                .stream()
                .filter(e -> !e.isSnapshotted())
                .toList();

        if (pendingEvents.isEmpty()) {
            log.debug("No pending events to snapshot");
            return;
        }

        // Group events by docId
        var eventsByDoc = pendingEvents.stream()
                .collect(java.util.stream.Collectors.groupingBy(CollabEvent::getDocId));

        eventsByDoc.forEach((docId, events) -> {
            try {
                Long currentVersion = snapshotRepository.findMaxVersionByDocId(docId).orElse(0L);
                Long newVersion = currentVersion + 1;

                // For now, save the latest state as snapshot
                // In production, you would merge all updates using Yjs
                byte[] snapshotData = events.get(events.size() - 1).getUpdate();

                DocumentSnapshot snapshot = DocumentSnapshot.builder()
                        .docId(docId)
                        .version(newVersion)
                        .snapshotData(snapshotData)
                        .sizeBytes((long) snapshotData.length)
                        .build();

                snapshotRepository.save(snapshot);

                // Mark events as snapshotted
                events.forEach(e -> e.setSnapshotted(true));
                collabEventRepository.saveAll(events);

                log.info("Created snapshot version {} for doc {}", newVersion, docId);
            } catch (Exception e) {
                log.error("Failed to create snapshot for doc {}: {}", docId, e.getMessage());
            }
        });
    }

    public long getPendingEventsCount(UUID docId) {
        return collabEventRepository.countByDocIdAndSnapshottedFalse(docId);
    }
}
