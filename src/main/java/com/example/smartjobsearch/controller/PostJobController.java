package com.example.smartjobsearch.controller;


import com.example.smartjobsearch.model.Job;
import com.example.smartjobsearch.service.JobService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.util.StringUtils;
import java.io.File;
import java.io.IOException;

@RestController
@RequestMapping("/api/post-job")
public class PostJobController {
    private final JobService jobService;

    @Autowired
    public PostJobController(JobService jobService) {
        this.jobService = jobService;
    }


    @PostMapping(consumes = {"multipart/form-data"})
    public ResponseEntity<?> postJobMultipart(
            @RequestParam("title") String title,
            @RequestParam("description") String description,
            @RequestParam("company") String company,
            @RequestParam("location") String location,
            @RequestParam("salary") String salary,
            @RequestParam("userId") Long userId,
            @RequestParam("jobType") String jobType,
            @RequestParam(value = "experience", required = false) String experience,
            @RequestParam(value = "skills", required = false) String skills,
            @RequestParam(value = "resume", required = false) MultipartFile resumeFile
    ) {
        if (userId == null || userId <= 0) {
            return ResponseEntity.status(401).body("User ID is required and must be a valid number");
        }
        String resumePath = null;
        if ("Technical".equalsIgnoreCase(jobType) && resumeFile != null && !resumeFile.isEmpty()) {
            try {
                String fileName = System.currentTimeMillis() + "_" + StringUtils.cleanPath(resumeFile.getOriginalFilename());
                // Use absolute path based on project root
                String projectRoot = System.getProperty("user.dir");
                String uploadDir = projectRoot + File.separator + "uploads" + File.separator + "resumes" + File.separator;
                File dir = new File(uploadDir);
                if (!dir.exists()) dir.mkdirs();
                File dest = new File(uploadDir + fileName);
                resumeFile.transferTo(dest);
                resumePath = dest.getAbsolutePath();
            } catch (IOException e) {
                return ResponseEntity.status(500).body("Failed to save resume file: " + e.getMessage());
            }
        }
        Job job = new Job(title, description, company, location, salary, userId, jobType, resumePath, experience, skills);
        Job saved = jobService.saveJob(job);
        return ResponseEntity.ok(saved);
    }

    @PostMapping(consumes = {"application/json"})
    public ResponseEntity<?> postJobJson(@RequestBody PostJobRequest request) {
        if (request.userId == null || request.userId <= 0) {
            return ResponseEntity.status(401).body("User ID is required and must be a valid number");
        }
        Job job = new Job(
            request.title,
            request.description,
            request.company,
            request.location,
            request.salary,
            request.userId,
            request.jobType,
            null,
            request.experience,
            request.skills
        );
        Job saved = jobService.saveJob(job);
        return ResponseEntity.ok(saved);
    }

    public static class PostJobRequest {
        public String title;
        public String description;
        public String company;
        public String location;
        public String salary;
        public Long userId;
        public String jobType;
        public String experience;
        public String skills;
    }
}
