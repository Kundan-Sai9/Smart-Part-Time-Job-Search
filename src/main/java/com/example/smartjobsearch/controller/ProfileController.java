package com.example.smartjobsearch.controller;

import com.example.smartjobsearch.model.User;
import com.example.smartjobsearch.service.ProfileScoringService;
import com.example.smartjobsearch.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/profile")
public class ProfileController {

    @Autowired
    private ProfileScoringService profileScoringService;
    
    @Autowired
    private UserService userService;

    private final RestTemplate rest = new RestTemplate();

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

            // First try ML service
            try {
                Map<String, Object> payload = new HashMap<>();
                payload.put("name", user.getFullName() != null ? user.getFullName() : "");
                payload.put("bio", user.getBio() != null ? user.getBio() : "");
                payload.put("skills", user.getSkills() != null ? user.getSkills() : "");
                payload.put("experience", user.getExperience() != null ? user.getExperience() : "");

                String mlBase = System.getenv().getOrDefault("ML_RECOMMENDER_URL", "http://localhost:8000");
                String url = mlBase.endsWith("/") ? mlBase + "profile/score" : mlBase + "/profile/score";

                @SuppressWarnings("unchecked")
                Map<String, Object> resp = rest.postForObject(url, payload, Map.class);
                if (resp != null) return ResponseEntity.ok(resp);
            } catch (Exception e) {
                // ML service failed - fallback to local scoring
                System.out.println("ML profile endpoint failed, using local scoring: " + e.getMessage());
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
        try {
            if (userId == null || userId <= 0) {
                return ResponseEntity.badRequest().body(Map.of("error", "Valid user ID is required"));
            }

            User user = userService.findById(userId).orElse(null);
            if (user == null) {
                return ResponseEntity.notFound().build();
            }

            // Try agentic profile analysis endpoint in ML service first.
            try {
                Map<String, Object> payload = new HashMap<>();
                payload.put("name", user.getFullName() != null ? user.getFullName() : "");
                payload.put("bio", user.getBio() != null ? user.getBio() : "");
                payload.put("skills", user.getSkills() != null ? user.getSkills() : "");
                payload.put("experience", user.getExperience() != null ? user.getExperience() : "");
                payload.put("role", user.getJobTitle() != null ? user.getJobTitle() : "");

                String mlBase = System.getenv().getOrDefault("ML_RECOMMENDER_URL", "http://localhost:8000");
                String url = mlBase.endsWith("/") ? mlBase + "profile/analyze-agent" : mlBase + "/profile/analyze-agent";

                @SuppressWarnings("unchecked")
                Map<String, Object> resp = rest.postForObject(url, payload, Map.class);
                if (resp != null) {
                    return ResponseEntity.ok(resp);
                }
            } catch (Exception e) {
                System.out.println("Agentic profile endpoint failed, using local fallback: " + e.getMessage());
            }

            // Backward-compatible fallback
            return getProfileScore(userId);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                "error", "Failed to analyze profile: " + e.getMessage()
            ));
        }
    }
}