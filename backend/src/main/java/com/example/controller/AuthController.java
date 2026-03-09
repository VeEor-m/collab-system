package com.example.controller;

import com.example.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final JwtTokenProvider jwtTokenProvider;

    // Simplified login - in production, integrate with OAuth2 or database
    @PostMapping("/login")
    public ResponseEntity<Map<String, String>> login(@RequestBody Map<String, String> credentials) {
        // For demo: accept any username/password, create a user
        // In production: validate against user database
        String userId = UUID.randomUUID().toString();
        String token = jwtTokenProvider.createToken(userId, List.of("USER"));

        log.info("Generated token for user: {}", userId);
        return ResponseEntity.ok(Map.of(
                "token", token,
                "userId", userId
        ));
    }

    @PostMapping("/register")
    public ResponseEntity<Map<String, String>> register(@RequestBody Map<String, String> userData) {
        // For demo: create a simple user
        String userId = UUID.randomUUID().toString();
        String token = jwtTokenProvider.createToken(userId, List.of("USER"));

        log.info("Registered new user: {}", userId);
        return ResponseEntity.ok(Map.of(
                "token", token,
                "userId", userId
        ));
    }

    @GetMapping("/validate")
    public ResponseEntity<Map<String, Boolean>> validate(@RequestHeader("Authorization") String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            boolean valid = jwtTokenProvider.validateToken(token);
            return ResponseEntity.ok(Map.of("valid", valid));
        }
        return ResponseEntity.ok(Map.of("valid", false));
    }
}
