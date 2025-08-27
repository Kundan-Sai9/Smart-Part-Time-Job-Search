package com.example.smartjobsearch.controller;

import com.example.smartjobsearch.service.AppliedJobService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/applied-jobs")
public class AppliedJobsController {
    private final AppliedJobService appliedJobService;

    @Autowired
    public AppliedJobsController(AppliedJobService appliedJobService) {
        this.appliedJobService = appliedJobService;
    }

    @GetMapping("/view-applications/{jobId}")
    public ResponseEntity<List<?>> viewApplications(@PathVariable Long jobId) {
        List<?> applications = appliedJobService.findByJobId(jobId);
        return ResponseEntity.ok(applications);
    }

    // You can add more endpoints for applying to jobs, etc.
}
