package com.example.controller;

import com.example.domain.Document;
import com.example.domain.DocumentSnapshot;
import com.example.domain.repository.DocumentRepository;
import com.example.security.JwtTokenProvider;
import com.example.service.CollabEventService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
@Slf4j
public class DocumentController {

    private final DocumentRepository documentRepository;
    private final CollabEventService collabEventService;
    private final JwtTokenProvider jwtTokenProvider;

    @PostMapping
    public ResponseEntity<Document> createDocument(@RequestBody Map<String, String> request,
                                                   Authentication auth) {
        String userId = auth.getName();
        Document document = Document.builder()
                .title(request.get("title"))
                .ownerId(UUID.fromString(userId))
                .permission("{\"owner\":\"" + userId + "\",\"write\":[\"" + userId + "\"],\"read\":[\"" + userId + "\"]}")
                .build();

        Document saved = documentRepository.save(document);
        return ResponseEntity.ok(saved);
    }

    @GetMapping
    public ResponseEntity<List<Document>> getDocuments(Authentication auth) {
        String userId = auth.getName();
        List<Document> documents = documentRepository.findByOwnerId(UUID.fromString(userId));
        return ResponseEntity.ok(documents);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Document> getDocument(@PathVariable UUID id) {
        return documentRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/snapshot")
    public ResponseEntity<byte[]> getSnapshot(@PathVariable UUID id,
                                               @RequestParam(required = false) Long version) {
        if (version != null) {
            return collabEventService.getSnapshot(id, version)
                    .map(s -> ResponseEntity.ok(s.getSnapshotData()))
                    .orElse(ResponseEntity.notFound().build());
        } else {
            return collabEventService.getLatestSnapshot(id)
                    .map(s -> ResponseEntity.ok(s.getSnapshotData()))
                    .orElse(ResponseEntity.notFound().build());
        }
    }

    @GetMapping("/{id}/snapshots")
    public ResponseEntity<List<DocumentSnapshot>> getSnapshotHistory(@PathVariable UUID id) {
        List<DocumentSnapshot> snapshots = collabEventService.getSnapshotHistory(id);
        return ResponseEntity.ok(snapshots);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDocument(@PathVariable UUID id, Authentication auth) {
        return documentRepository.findById(id)
                .map(doc -> {
                    if (doc.getOwnerId().toString().equals(auth.getName())) {
                        documentRepository.delete(doc);
                        return ResponseEntity.noContent().<Void>build();
                    }
                    return ResponseEntity.status(403).<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
