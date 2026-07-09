package com.example.smartjobsearch.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

import lombok.Data;

@Entity
@Data
public class AppliedJob {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;
    private Long jobId;

    private String jobTitle;
    private String company;
    private String status;
    private LocalDateTime appliedAt;
    private String resumePath;
}

