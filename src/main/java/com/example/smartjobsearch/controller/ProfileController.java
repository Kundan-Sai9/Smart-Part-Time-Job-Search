package com.example.smartjobsearch.controller;

import com.example.smartjobsearch.model.User;
import com.example.smartjobsearch.service.ProfileScoringService;
import com.example.smartjobsearch.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/profile")
public class ProfileController {

    @Autowired
    private ProfileScoringService profileScoringService;
    
    @Autowired
    private UserService userService;

    @GetMapping("/score")
    public ResponseEntity<?> getProfileScore(@RequestParam Long userId) {
        try {
            if (userId == null || userId <= 0) {
                return ResponseEntity.badRequest().body(Map.of(
                    "error", "Valid user ID is required",
                    "score", 0
                ));
            }

            User user = userService.findById(userId).orElse(null);
            if (user == null) {
                return ResponseEntity.notFound().build();
            }

            Map<String, Object> analysis = profileScoringService.analyzeProfile(user);
            
            return ResponseEntity.ok(analysis);
            
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                "error", "Failed to analyze profile: " + e.getMessage(),
                "score", 0,
                "suggestion", "Unable to analyze profile at this time. Please try again later."
            ));
        }
    }

    @GetMapping("/analyze")
    public ResponseEntity<?> analyzeProfile(@RequestParam Long userId) {
        // Alias for score endpoint for compatibility
        return getProfileScore(userId);
    }
}