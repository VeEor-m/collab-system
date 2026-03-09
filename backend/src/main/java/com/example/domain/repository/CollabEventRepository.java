package com.example.domain.repository;

import com.example.domain.CollabEvent;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CollabEventRepository extends MongoRepository<CollabEvent, String> {

    List<CollabEvent> findByDocIdAndSnapshottedFalseOrderByTimestampAsc(UUID docId);

    List<CollabEvent> findByDocIdOrderByTimestampDesc(UUID docId);

    long countByDocIdAndSnapshottedFalse(UUID docId);
}
