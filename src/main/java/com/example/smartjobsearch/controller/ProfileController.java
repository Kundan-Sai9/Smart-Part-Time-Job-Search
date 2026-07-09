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
import java.util.Optional;

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
    @PutMapping("/")
    public ResponseEntity<?> updateProfile(@RequestBody UpdateProfileRequest request) {
        try {
            if (request.userId == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "User ID is required"));
            }
            
            Optional<User> userOpt = userService.findById(request.userId);
            if (userOpt.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "User not found"));
            }
            
            User user = userOpt.get();
            
            // Update basic user details
            if (request.fullName != null && !request.fullName.trim().isEmpty()) {
                user.setFullName(request.fullName.trim());
            }
            if (request.username != null && !request.username.trim().isEmpty()) {
                // Check if new username is already taken
                Optional<User> existingUser = userService.findByUsername(request.username);
                if (existingUser.isPresent() && !existingUser.get().getId().equals(user.getId())) {
                    return ResponseEntity.badRequest().body(Map.of("error", "Username already exists"));
                }
                user.setUsername(request.username.trim());
            }
            if (request.email != null && !request.email.trim().isEmpty()) {
                // Check if new email is already taken
                Optional<User> existingUser = userService.findByEmail(request.email);
                if (existingUser.isPresent() && !existingUser.get().getId().equals(user.getId())) {
                    return ResponseEntity.badRequest().body(Map.of("error", "Email already exists"));
                }
                user.setEmail(request.email.trim());
            }
            
            // Update profile fields for AI recommendations
            if (request.skills != null) {
                user.setSkills(request.skills.trim());
            }
            if (request.experience != null) {
                user.setExperience(request.experience.trim());
            }
            if (request.preferredLocation != null) {
                user.setPreferredLocation(request.preferredLocation.trim());
            }
            if (request.salaryExpectation != null) {
                user.setSalaryExpectation(request.salaryExpectation.trim());
            }
            if (request.bio != null) {
                user.setBio(request.bio.trim());
            }
            if (request.preferredJobType != null) {
                user.setPreferredJobType(request.preferredJobType.trim());
            }
            if (request.jobTitle != null) {
                user.setJobTitle(request.jobTitle.trim());
            }
            if (request.yearsExperience != null) {
                user.setYearsExperience(request.yearsExperience);
            }
            if (request.industries != null) {
                user.setIndustries(request.industries.trim());
            }
            if (request.certifications != null) {
                user.setCertifications(request.certifications.trim());
            }
            
            User savedUser = userService.saveUser(user);
            
            Map<String, Object> userMap = new HashMap<>();
            userMap.put("user_id", savedUser.getId());
            userMap.put("full_name", savedUser.getFullName());
            userMap.put("username", savedUser.getUsername());
            userMap.put("email", savedUser.getEmail());
            userMap.put("skills", savedUser.getSkills() != null ? savedUser.getSkills() : "");
            userMap.put("experience", savedUser.getExperience() != null ? savedUser.getExperience() : "");
            userMap.put("preferred_location", savedUser.getPreferredLocation() != null ? savedUser.getPreferredLocation() : "");
            userMap.put("salary_expectation", savedUser.getSalaryExpectation() != null ? savedUser.getSalaryExpectation() : "");
            userMap.put("bio", savedUser.getBio() != null ? savedUser.getBio() : "");
            userMap.put("preferred_job_type", savedUser.getPreferredJobType() != null ? savedUser.getPreferredJobType() : "");
            userMap.put("job_title", savedUser.getJobTitle() != null ? savedUser.getJobTitle() : "");
            userMap.put("years_experience", savedUser.getYearsExperience() != null ? savedUser.getYearsExperience() : 0);
            userMap.put("industries", savedUser.getIndustries() != null ? savedUser.getIndustries() : "");
            userMap.put("certifications", savedUser.getCertifications() != null ? savedUser.getCertifications() : "");
            
            return ResponseEntity.ok().body(Map.of(
                "message", "Profile updated successfully",
                "user", userMap
            ));
            
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Profile update failed: " + e.getMessage()));
        }
    }

    @GetMapping("/user-info")
    public ResponseEntity<?> getUserInfo(@RequestParam Long userId) {
        try {
            Optional<User> userOpt = userService.findById(userId);
            if (userOpt.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "User not found"));
            }
            
            User user = userOpt.get();
            
            Map<String, Object> userInfo = new HashMap<>();
            userInfo.put("user_id", user.getId());
            userInfo.put("full_name", user.getFullName());
            userInfo.put("username", user.getUsername());
            userInfo.put("email", user.getEmail());
            userInfo.put("skills", user.getSkills() != null ? user.getSkills() : "");
            userInfo.put("experience", user.getExperience() != null ? user.getExperience() : "");
            userInfo.put("preferred_location", user.getPreferredLocation() != null ? user.getPreferredLocation() : "");
            userInfo.put("salary_expectation", user.getSalaryExpectation() != null ? user.getSalaryExpectation() : "");
            userInfo.put("bio", user.getBio() != null ? user.getBio() : "");
            userInfo.put("preferred_job_type", user.getPreferredJobType() != null ? user.getPreferredJobType() : "");
            userInfo.put("job_title", user.getJobTitle() != null ? user.getJobTitle() : "");
            userInfo.put("years_experience", user.getYearsExperience() != null ? user.getYearsExperience() : 0);
            userInfo.put("industries", user.getIndustries() != null ? user.getIndustries() : "");
            userInfo.put("certifications", user.getCertifications() != null ? user.getCertifications() : "");
            
            return ResponseEntity.ok().body(userInfo);
            
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Failed to get user info: " + e.getMessage()));
        }
    }
    
    public static class UpdateProfileRequest {
        public Long userId;
        public String fullName, username,email, skills, experience, preferredLocation,salaryExpectation,bio,preferredJobType;
        public Integer yearsExperience;
        public String industries,certifications,jobTitle;
    }
}
