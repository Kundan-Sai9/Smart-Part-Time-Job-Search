package com.example.smartjobsearch.controller;

import com.example.smartjobsearch.model.AppliedJob;
import com.example.smartjobsearch.model.Job;
import com.example.smartjobsearch.model.User;
import com.example.smartjobsearch.service.AppliedJobService;
import com.example.smartjobsearch.service.JobService;
import com.example.smartjobsearch.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.util.StringUtils;
import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/applied-jobs")
public class AppliedJobsController {
    
    private final AppliedJobService appliedJobService;
    private final JobService jobService;
    private final UserService userService;

    @Autowired
    public AppliedJobsController(AppliedJobService appliedJobService, JobService jobService, UserService userService) {
        this.appliedJobService = appliedJobService;
        this.jobService = jobService;
        this.userService = userService;
    }

    @GetMapping("/view-applications/{jobId}")
    public ResponseEntity<List<?>> viewApplications(@PathVariable Long jobId) {
        List<?> applications = appliedJobService.findByJobId(jobId);
        return ResponseEntity.ok(applications);
    }

    @PostMapping(value = "/apply", consumes = {"multipart/form-data"})
    public ResponseEntity<?> applyJobMultipart(
            @RequestParam("userId") Long userId,
            @RequestParam("jobId") Long jobId,
            @RequestParam(value = "resume", required = false) MultipartFile resumeFile
    ) {
        try {
            if (userId == null || jobId == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "User ID and Job ID are required"));
            }
            if (userId <= 0 || jobId <= 0) {
                return ResponseEntity.badRequest().body(Map.of("error", "User ID and Job ID must be valid numbers and are required"));
            }

            // Check if user exists
            Optional<User> userOpt = userService.findById(userId);
            if (userOpt.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "User not found"));
            }

            // Check if job exists
            Optional<Job> jobOpt = jobService.getJobById(jobId);
            if (jobOpt.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Job not found"));
            }

            Job job = jobOpt.get();
            User user = userOpt.get();

            // Prevent applying to self-posted jobs
            if (job.getPostedBy() != null && job.getPostedBy().equals(user.getId())) {
                return ResponseEntity.badRequest().body(Map.of("error", "You cannot apply to your own posted job"));
            }

            // Check if user already applied
            List<AppliedJob> existingApplications = appliedJobService.findByJobId(jobId);
            boolean alreadyApplied = existingApplications.stream()
                .anyMatch(app -> app.getUserId().equals(userId));

            if (alreadyApplied) {
                return ResponseEntity.badRequest().body(Map.of("error", "You have already applied for this job"));
            }

            // Save resume file if present
            String resumePath = null;
            if (resumeFile != null && !resumeFile.isEmpty()) {
                // Use absolute path based on project root
                String projectRoot = System.getProperty("user.dir");
                String uploadDir = projectRoot + File.separator + "uploads" + File.separator + "resumes" + File.separator;
                File dir = new File(uploadDir);
                if (!dir.exists()) dir.mkdirs();
                String fileName = System.currentTimeMillis() + "_" + StringUtils.cleanPath(resumeFile.getOriginalFilename());
                File dest = new File(uploadDir + fileName);
                resumeFile.transferTo(dest);
                resumePath = dest.getAbsolutePath();
            }

            // Create application
            AppliedJob application = new AppliedJob();
            application.setUserId(user.getId());
            application.setJobId(job.getId());
            application.setJobTitle(job.getTitle());
            application.setCompany(job.getCompany());
            application.setStatus("Pending");
            application.setAppliedAt(LocalDateTime.now());
            application.setResumePath(resumePath);

            AppliedJob savedApplication = appliedJobService.save(application);

            return ResponseEntity.ok().body(Map.of(
                "message", "Application submitted successfully",
                "application_id", savedApplication.getId(),
                "resumePath", resumePath != null ? resumePath : ""
            ));

        } catch (IOException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Failed to save resume: " + e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Application failed: " + e.getMessage()));
        }
    }

    @GetMapping
    public ResponseEntity<?> getAppliedJobs(@RequestParam Long userId) {
        try {
            List<AppliedJob> applications = appliedJobService.findByUserId(userId)
                .stream()
                .filter(app -> !"Accepted".equalsIgnoreCase(app.getStatus())) // Exclude approved jobs
                .collect(Collectors.toList());
            List<Map<String, Object>> result = new ArrayList<>();
            for (AppliedJob app : applications) {
                Map<String, Object> appMap = new HashMap<>();
                appMap.put("application_id", app.getId());
                appMap.put("job_id", app.getJobId());
                // Fetch full job details
                Optional<Job> jobOpt = jobService.getJobById(app.getJobId());
                if (jobOpt.isPresent()) {
                    Job job = jobOpt.get();
                    appMap.put("title", job.getTitle());
                    appMap.put("company", job.getCompany());
                    appMap.put("location", job.getLocation());
                    appMap.put("salary", job.getSalary());
                } else {
                    appMap.put("title", app.getJobTitle());
                    appMap.put("company", app.getCompany());
                    appMap.put("location", "N/A");
                    appMap.put("salary", null);
                }
                appMap.put("status", app.getStatus());
                appMap.put("applied_at", app.getAppliedAt() != null ? app.getAppliedAt().toString() : "");
                result.add(appMap);
            }
            Map<String, Object> response = new HashMap<>();
            response.put("jobs", result);
            return ResponseEntity.ok().body(response);
        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Failed to fetch applied jobs: " + e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    @PostMapping("/approve")
    public ResponseEntity<?> approveApplication(@RequestBody ApproveApplicationRequest request) {
        try {
            if (request.applicationId == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Application ID is required"));
            }
            
            Optional<AppliedJob> applicationOpt = appliedJobService.findById(request.applicationId);
            if (applicationOpt.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Application not found"));
            }
            
            AppliedJob application = applicationOpt.get();
            
            // Check if job belongs to the requesting user (should validate job owner)
            Optional<Job> jobOpt = jobService.getJobById(application.getJobId());
            if (jobOpt.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Job not found"));
            }
            
            // Update application status
            application.setStatus("Accepted");
            appliedJobService.save(application);

            // Close the job
            Job job = jobOpt.get();
            job.setStatus("CLOSED");
            jobService.saveJob(job);
            
            // Reject other applications for the same job (only one can be accepted)
            List<AppliedJob> otherApplications = appliedJobService.findByJobId(application.getJobId());
            for (AppliedJob otherApp : otherApplications) {
                if (!otherApp.getId().equals(application.getId()) && "Pending".equals(otherApp.getStatus())) {
                    otherApp.setStatus("Rejected");
                    appliedJobService.save(otherApp);
                }
            }
            
            return ResponseEntity.ok().body(Map.of("message", "Application approved successfully"));
            
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Approval failed: " + e.getMessage()));
        }
    }

    @PostMapping("/reject")
    public ResponseEntity<?> rejectApplication(@RequestBody ApproveApplicationRequest request) {
        try {
            if (request.applicationId == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Application ID is required"));
            }
            
            Optional<AppliedJob> applicationOpt = appliedJobService.findById(request.applicationId);
            if (applicationOpt.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Application not found"));
            }
            
            AppliedJob application = applicationOpt.get();
            
            // Check if job belongs to the requesting user (should validate job owner)
            Optional<Job> jobOpt = jobService.getJobById(application.getJobId());
            if (jobOpt.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Job not found"));
            }
            
            application.setStatus("Rejected");
            appliedJobService.save(application);
            
            return ResponseEntity.ok().body(Map.of("message", "Application rejected successfully"));
            
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Rejection failed: " + e.getMessage()));
        }
    }

    @GetMapping("/dashboard")
    public ResponseEntity<?> dashboard(@RequestParam Long userId) {
        try {
            // Get user's posted jobs
            List<Job> postedJobs = jobService.getJobsByUser(userId);
            
            // Get applications for each job
            List<Map<String, Object>> jobsWithApplications = new ArrayList<>();
            
            for (Job job : postedJobs) {
                List<AppliedJob> applications = appliedJobService.findByJobId(job.getId());
                
                List<Map<String, Object>> applicants = applications.stream()
                    .map(app -> {
                        Optional<User> userOpt = userService.findById(app.getUserId());
                        String username = userOpt.map(User::getUsername).orElse("Unknown");
                        
                        Map<String, Object> appMap = new HashMap<>();
                        appMap.put("application_id", app.getId());
                        appMap.put("username", username);
                        appMap.put("status", app.getStatus());
                        appMap.put("applied_at", app.getAppliedAt().toString());
                        return appMap;
                    })
                    .collect(Collectors.toList());
                
                Map<String, Object> jobMap = new HashMap<>();
                jobMap.put("job_id", job.getId());
                jobMap.put("job_title", job.getTitle());
                jobMap.put("company", job.getCompany());
                jobMap.put("applicants", applicants);
                jobsWithApplications.add(jobMap);
            }
            
            return ResponseEntity.ok().body(jobsWithApplications);
            
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Dashboard load failed: " + e.getMessage()));
        }
    }

    @GetMapping("/posted-applications")
    public ResponseEntity<?> getAllPostedApplications(@RequestParam Long userId) {
        try {
            // Get all jobs posted by the user
            List<Job> userJobs = jobService.getJobsByUser(userId);
            
            List<Map<String, Object>> result = new ArrayList<>();
            
            for (Job job : userJobs) {
                List<AppliedJob> applications = appliedJobService.findByJobId(job.getId());
                
                List<Map<String, Object>> applicants = applications.stream()
                    .map(app -> {
                        Optional<User> applicantOpt = userService.findById(app.getUserId());
                        String username = applicantOpt.map(User::getUsername).orElse("Unknown");
                        
                        Map<String, Object> appMap = new HashMap<>();
                        appMap.put("application_id", app.getId());
                        appMap.put("username", username);
                        appMap.put("status", app.getStatus());
                        appMap.put("applied_at", app.getAppliedAt().toString());
                        return appMap;
                    })
                    .collect(Collectors.toList());
                
                Map<String, Object> jobMap = new HashMap<>();
                jobMap.put("job_id", job.getId());
                jobMap.put("job_title", job.getTitle());
                jobMap.put("job_type", job.getJobType()); // Add job_type field
                jobMap.put("company", job.getCompany());
                jobMap.put("location", job.getLocation());
                jobMap.put("salary", job.getSalary());
                jobMap.put("description", job.getDescription());
                jobMap.put("applicants", applicants);
                result.add(jobMap);
            }
            
            return ResponseEntity.ok().body(result);
            
        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Failed to fetch applications: " + e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    @DeleteMapping("/{applicationId}")
    public ResponseEntity<?> deleteApplication(@PathVariable Long applicationId, @RequestParam Long userId) {
        try {
            Optional<AppliedJob> applicationOpt = appliedJobService.findById(applicationId);
            if (applicationOpt.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Application not found"));
            }
            
            AppliedJob application = applicationOpt.get();
            
            // Verify that the application belongs to the requesting user
            if (!application.getUserId().equals(userId)) {
                return ResponseEntity.badRequest().body(Map.of("error", "Unauthorized to delete this application"));
            }
            
            appliedJobService.deleteById(applicationId);
            
            return ResponseEntity.ok().body(Map.of(
                "success", true,
                "message", "Application deleted successfully"
            ));
            
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Failed to delete application: " + e.getMessage()));
        }
    }

    @GetMapping("/approved")
    public ResponseEntity<?> getApprovedJobs(@RequestParam Long userId) {
        try {
            List<AppliedJob> approvedApplications = appliedJobService.findByUserId(userId)
                .stream()
                .filter(app -> "Accepted".equalsIgnoreCase(app.getStatus()))
                .collect(Collectors.toList());
            List<Map<String, Object>> result = new ArrayList<>();
            for (AppliedJob app : approvedApplications) {
                Optional<Job> jobOpt = jobService.getJobById(app.getJobId());
                if (jobOpt.isPresent()) {
                    Job job = jobOpt.get();
                    result.add(Map.of(
                        "title", job.getTitle(),
                        "company", job.getCompany(),
                        "location", job.getLocation(),
                        "salary", job.getSalary(),
                        "description", job.getDescription(),
                        "accepted_at", app.getAppliedAt().toString()
                    ));
                }
            }
            return ResponseEntity.ok().body(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Failed to fetch approved jobs: " + e.getMessage()));
        }
    }

    public static class ApplyJobRequest {
        public Long userId,jobId;
    }
    
    public static class ApproveApplicationRequest {
        public Long applicationId,jobId;
    }
}
