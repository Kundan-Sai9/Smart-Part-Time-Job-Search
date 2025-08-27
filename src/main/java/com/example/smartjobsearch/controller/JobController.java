
package com.example.smartjobsearch.controller;

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.Comparator;
import com.example.smartjobsearch.service.UserService;
import com.example.smartjobsearch.model.User;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    // Get all jobs or search
    @GetMapping
    public List<Job> getAllJobs(@RequestParam(value = "search", required = false) String search) {
        System.out.println("DEBUG JobController - getAllJobs called with search: " + search);
        if (search != null && !search.trim().isEmpty()) {
            List<Job> searchResults = jobService.searchJobs(search);
            System.out.println("DEBUG JobController - Search returned " + searchResults.size() + " jobs");
            return searchResults;
        }
        List<Job> allJobs = jobService.getAllJobs();
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
        return jobService.saveJob(job);
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
                                                              @RequestParam(defaultValue = "5") int limit) {
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
            
            // Format response
            List<Map<String, Object>> formattedRecommendations = result.getRecommendations().stream()
                .map(rec -> {
                    Job job = rec.getJob();
                    Map<String, Object> jobData = new HashMap<>();
                    jobData.put("id", job.getId());
                    jobData.put("title", job.getTitle());
                    jobData.put("company", job.getCompany());
                    jobData.put("location", job.getLocation());
                    jobData.put("salary", job.getSalary());
                    jobData.put("description", job.getDescription());
                    jobData.put("match_score", Math.round(rec.getScore() * 100));
                    jobData.put("match_reasons", rec.getReasons());
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
