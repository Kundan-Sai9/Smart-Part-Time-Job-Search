
package com.example.smartjobsearch.controller;

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import com.example.smartjobsearch.service.UserService;
import com.example.smartjobsearch.model.User;
import com.example.smartjobsearch.model.Job;
import com.example.smartjobsearch.service.JobService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/jobs")
public class JobController {
    // Update job info
    @PutMapping("/{id}")
    public ResponseEntity<?> updateJob(@PathVariable Long id, @RequestBody Job updatedJob) {
        Optional<Job> jobOpt = jobService.getJobById(id);
        if (jobOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("success", false, "error", "Job not found"));
        }
        Job job = jobOpt.get();
        job.setTitle(updatedJob.getTitle());
        job.setDescription(updatedJob.getDescription());
        job.setCompany(updatedJob.getCompany());
        job.setLocation(updatedJob.getLocation());
        job.setSalary(updatedJob.getSalary());
        jobService.saveJob(job);
        return ResponseEntity.ok(Map.of("success", true, "message", "Job updated successfully!"));
    }

    @Autowired
    private JobService jobService;

    @Autowired
    private UserService userService;
    
    @Autowired
    private com.example.smartjobsearch.service.JobRecommendationService jobRecommendationService;

    @GetMapping
    public List<Job> getAllJobs(@RequestParam(value = "search", required = false) String search) {
        System.out.println("DEBUG JobController - getAllJobs called with search: " + search);
        if (search != null && !search.trim().isEmpty()) {
            List<Job> searchResults = jobService.searchJobs(search);
            searchResults = searchResults.stream().filter(j -> !"CLOSED".equals(j.getStatus())).collect(Collectors.toList());
            System.out.println("DEBUG JobController - Search returned " + searchResults.size() + " jobs");
            return searchResults;
        }
        List<Job> allJobs = jobService.getAllJobs().stream().filter(j -> !"CLOSED".equals(j.getStatus())).collect(Collectors.toList());
        System.out.println("DEBUG JobController - getAllJobs returned " + allJobs.size() + " jobs");
        return allJobs;
    }

    // Get jobs by user
    @GetMapping("/user/{userId}")
    public List<Job> getJobsByUser(@PathVariable Long userId) {
        return jobService.getJobsByUser(userId);
    }

    // Get job by id
    @GetMapping("/{id}")
    public ResponseEntity<Job> getJobById(@PathVariable Long id) {
        Optional<Job> job = jobService.getJobById(id);
        return job.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    // Create or update job
    @PostMapping
    public Job createOrUpdateJob(@RequestBody Job job) {
        Job saved = jobService.saveJob(job);

        // Try to notify the ML service so it can update its corpus dynamically.
        // This is best-effort and should not break job creation if ML is unavailable.
        try {
            String mlUrl = System.getenv().getOrDefault("ML_RECOMMENDER_URL", "http://localhost:8000");
            // If env var points to a specific endpoint like /recommend, strip path
            if (mlUrl.endsWith("/recommend")) {
                mlUrl = mlUrl.substring(0, mlUrl.length() - "/recommend".length());
            }
            String uploadUrl = mlUrl.endsWith("/") ? mlUrl + "upload_jobs" : mlUrl + "/upload_jobs";

            org.springframework.web.client.RestTemplate rt = new org.springframework.web.client.RestTemplate();
            java.util.Map<String, Object> jobMap = new java.util.HashMap<>();
            jobMap.put("Job Title", saved.getTitle() != null ? saved.getTitle() : "");
            jobMap.put("Company", saved.getCompany() != null ? saved.getCompany() : "");
            jobMap.put("Location", saved.getLocation() != null ? saved.getLocation() : "");
            jobMap.put("Experience Level", saved.getExperience() != null ? saved.getExperience() : "");
            jobMap.put("Salary", saved.getSalary() != null ? saved.getSalary() : "");
            jobMap.put("Industry", "");
            jobMap.put("Required Skills", saved.getSkills() != null ? saved.getSkills() : "");
            jobMap.put("job_id", saved.getId() != null ? saved.getId() : 0);

            java.util.Map<String, Object> payload = new java.util.HashMap<>();
            payload.put("jobs", java.util.Arrays.asList(jobMap));
            payload.put("train_reranker", false);

            // Fire-and-forget: do not block user flow on ML availability. Log any error.
            try {
                @SuppressWarnings("unchecked")
                java.util.Map<String, Object> resp = rt.postForObject(uploadUrl, payload, java.util.Map.class);
                System.out.println("ML upload_jobs response: " + (resp != null ? resp.toString() : "null"));
            } catch (Exception ex) {
                System.out.println("Warning: failed to notify ML service of new job: " + ex.getMessage());
            }

            // Spawn a background retrain task to update the reranker asynchronously.
            try {
                final String uploadUrlFinal = uploadUrl;
                final java.util.Map<String, Object> retrainPayload = new java.util.HashMap<>();
                retrainPayload.put("jobs", java.util.Arrays.asList(jobMap));
                retrainPayload.put("train_reranker", true);

                Thread retrainThread = new Thread(() -> {
                    try {
                        // Slight delay to allow ML to finish any in-flight upload
                        Thread.sleep(1000);
                        org.springframework.web.client.RestTemplate rt2 = new org.springframework.web.client.RestTemplate();
                        @SuppressWarnings("unchecked")
                        java.util.Map<String, Object> r = rt2.postForObject(uploadUrlFinal, retrainPayload, java.util.Map.class);
                        System.out.println("ML async retrain response: " + (r != null ? r.toString() : "null"));
                    } catch (Exception ex) {
                        System.out.println("Warning: async retrain failed: " + ex.getMessage());
                    }
                });
                retrainThread.setDaemon(true);
                retrainThread.start();
            } catch (Exception e) {
                System.out.println("Non-fatal: failed to start async retrain thread: " + e.getMessage());
            }
        } catch (Exception e) {
            System.out.println("Non-fatal: error while preparing ML notification: " + e.getMessage());
        }

        return saved;
    }

    // Delete job
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteJob(@PathVariable Long id) {
        jobService.deleteJob(id);
        return ResponseEntity.noContent().build();
    }
    
    // AI-powered personalized job recommendations
    @GetMapping("/ai/recommendations")
    public ResponseEntity<?> getPersonalizedJobRecommendations(@RequestParam Long userId, 
                                                              @RequestParam(defaultValue = "3") int limit) {
        try {
            // Validate user
            Optional<User> userOpt = userService.findById(userId);
            if (userOpt.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "User not found"));
            }
            
            User user = userOpt.get();
            
            // Debug: Log user skills
            System.out.println("DEBUG - User ID: " + user.getId() + ", Skills: " + user.getSkills());
            
            // Get AI-powered recommendations
            var result = jobRecommendationService.getPersonalizedRecommendations(user, limit);
            
            // Debug: Log recommendation results
            System.out.println("DEBUG - Found " + result.getRecommendations().size() + " recommendations");
            result.getRecommendations().forEach(rec -> 
                System.out.println("DEBUG - Job: " + rec.getJob().getTitle() + ", Score: " + rec.getScore())
            );
            
            // Format response and respect requested limit
            double profileCompletenessFraction = Math.max(0.0, Math.min(1.0, result.getProfileCompleteness() / 100.0));

            List<Map<String, Object>> formattedRecommendations = result.getRecommendations().stream()
                .limit(limit)
                .map(rec -> {
                    Job job = rec.getJob();
                    Map<String, Object> jobData = new HashMap<>();
                    jobData.put("id", job.getId());
                    jobData.put("title", job.getTitle());
                    jobData.put("company", job.getCompany());
                    jobData.put("location", job.getLocation());
                    jobData.put("salary", job.getSalary());
                    jobData.put("description", job.getDescription());

                    // Adjust score using profile completeness to make match % more accurate and stable
                    double rawScore = rec.getScore();
                    if (Double.isNaN(rawScore) || Double.isInfinite(rawScore)) rawScore = 0.0;
                    // Blend: 85% recommender score, 15% profile completeness (tunable)
                    double blended = rawScore * 0.85 + profileCompletenessFraction * 0.15;
                    // Clamp to [0,1]
                    double safe = Math.max(0.0, Math.min(1.0, blended));
                    int percent = (int) Math.round(safe * 100.0);
                    // If recommender or profile gives a non-zero signal, show a small visible floor
                    if (percent == 0 && (rawScore > 0.0 || profileCompletenessFraction > 0.0)) {
                        percent = 5; // minimal visible percent to avoid misleading 0%
                    }
                    jobData.put("match_score", percent);

                    // Build match reasons and include profile completeness note
                    java.util.List<String> reasons = new java.util.ArrayList<>();
                    if (rec.getReasons() != null) reasons.addAll(rec.getReasons());
                    reasons.add("Profile completeness: " + Math.round(profileCompletenessFraction * 100) + "%");
                    jobData.put("match_reasons", reasons);

                    return jobData;
                })
                .collect(Collectors.toList());
            
            return ResponseEntity.ok(Map.of(
                "recommendations", formattedRecommendations,
                "profile_completeness", Math.round(result.getProfileCompleteness()),
                "insights", result.getInsights(),
                "total_jobs_analyzed", result.getTotalJobsAnalyzed(),
                "user_profile", Map.of(
                    "skills", user.getSkills() != null ? user.getSkills() : "",
                    "experience", user.getExperience() != null ? user.getExperience() : "",
                    "preferred_location", user.getPreferredLocation() != null ? user.getPreferredLocation() : "",
                    "preferred_job_type", user.getPreferredJobType() != null ? user.getPreferredJobType() : "",
                    "bio", user.getBio() != null ? user.getBio() : ""
                ),
                "message", formattedRecommendations.isEmpty() ? 
                    "No jobs available at the moment. Please check back later." :
                    "Found " + formattedRecommendations.size() + " job recommendations for you"
            ));
            
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "Failed to get job recommendations: " + e.getMessage()
            ));
        }
    }
}
