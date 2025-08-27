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
@RequestMapping("/api/auth")
public class AuthController {
    
    private final UserService userService;
    private final AppliedJobService appliedJobService;
    private final JobService jobService;

    @Autowired
    public AuthController(UserService userService, AppliedJobService appliedJobService, JobService jobService) {
        this.userService = userService;
        this.appliedJobService = appliedJobService;
        this.jobService = jobService;
    }

    @PostMapping("/signup")
    public ResponseEntity<?> signup(@RequestBody SignupRequest request) {
        try {
            // Validate input
            if (request.fullName == null || request.fullName.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Full name is required"));
            }
            if (request.username == null || request.username.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Username is required"));
            }
            if (request.email == null || request.email.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Email is required"));
            }
            if (request.password == null || request.password.length() < 8) {
                return ResponseEntity.badRequest().body(Map.of("error", "Password must be at least 8 characters"));
            }
            if (!request.password.equals(request.confirmPassword)) {
                return ResponseEntity.badRequest().body(Map.of("error", "Passwords do not match"));
            }
            
            // Check if user already exists
            if (userService.findByUsername(request.username).isPresent()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Username already exists"));
            }
            if (userService.findByEmail(request.email).isPresent()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Email already exists"));
            }
            
            // Create new user (password should be hashed in production)
            User user = new User(request.fullName, request.username, request.email, request.password);
            User savedUser = userService.saveUser(user);
            
            return ResponseEntity.ok().body(Map.of(
                "message", "User registered successfully",
                "user_id", savedUser.getId()
            ));
            
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Registration failed: " + e.getMessage()));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        try {
            if (request.userInput == null || request.userInput.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Username/Email is required"));
            }
            if (request.password == null || request.password.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Password is required"));
            }
            
            // Find user by username or email
            Optional<User> userOpt = userService.findByUsername(request.userInput);
            if (userOpt.isEmpty()) {
                userOpt = userService.findByEmail(request.userInput);
            }
            
            if (userOpt.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "User not found"));
            }
            
            User user = userOpt.get();
            
            // Check password (should use proper hashing in production)
            if (!user.getPassword().equals(request.password)) {
                return ResponseEntity.badRequest().body(Map.of("error", "Invalid password"));
            }
            
            return ResponseEntity.ok().body(Map.of(
                "message", "Login successful",
                "user_id", user.getId(),
                "full_name", user.getFullName(),
                "username", user.getUsername(),
                "email", user.getEmail()
            ));
            
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Login failed: " + e.getMessage()));
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout() {
        return ResponseEntity.ok().body(Map.of("message", "Logout successful"));
    }

    @PutMapping("/profile")
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

    @PostMapping(value = "/apply-job", consumes = {"multipart/form-data"})
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

    @GetMapping("/applied-jobs")
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

    @PostMapping("/approve-application")
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

    @DeleteMapping("/delete-application/{applicationId}")
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

    @GetMapping("/approved-jobs")
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

    // DTO classes for request bodies
    public static class SignupRequest {
        public String fullName;
        public String username;
        public String email;
        public String password;
        public String confirmPassword;
    }
    
    public static class LoginRequest {
        public String userInput; // username or email
        public String password;
    }
    
    public static class UpdateProfileRequest {
        public Long userId;
        public String fullName;
        public String username;
        public String email;
        // Profile fields for AI recommendations
        public String skills;
        public String experience;
        public String preferredLocation;
        public String salaryExpectation;
        public String bio;
        public String preferredJobType;
        // Additional fields for history-based recommendations
        public String jobTitle;
        public Integer yearsExperience;
        public String industries;
        public String certifications;
    }
    
    public static class ApplyJobRequest {
        public Long userId;
        public Long jobId;
    }
    
    public static class ApproveApplicationRequest {
        public Long applicationId;
        public Long jobId;
    }
}
