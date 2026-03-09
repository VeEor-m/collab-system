package com.example.domain.repository;

import com.example.domain.DocumentSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DocumentSnapshotRepository extends JpaRepository<DocumentSnapshot, UUID> {

    List<DocumentSnapshot> findByDocIdOrderByVersionDesc(UUID docId);

    Optional<DocumentSnapshot> findByDocIdAndVersion(UUID docId, Long version);

    @Query("SELECT MAX(s.version) FROM DocumentSnapshot s WHERE s.docId = :docId")
    Optional<Long> findMaxVersionByDocId(UUID docId);
}
