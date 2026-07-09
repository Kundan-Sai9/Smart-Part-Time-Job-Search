package com.example.smartjobsearch.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@NoArgsConstructor
public class Job {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long postedBy;

    private String title;
    private String description;
    private String company;
    private String location;
    private String salary;
    private String jobType;
    private String resumePath;
    private String experience;
    private String skills;
    private String status = "OPEN"; // Default to OPEN

    public Job(String title, String description, String company, String location, String salary, Long postedBy, String jobType, String resumePath, String experience, String skills) {
        this.title = title;
        this.description = description;
        this.company = company;
        this.location = location;
        this.salary = salary;
        this.postedBy = postedBy;
        this.jobType = jobType;
        this.resumePath = resumePath;
        this.experience = experience;
        this.skills = skills;
    }
}
