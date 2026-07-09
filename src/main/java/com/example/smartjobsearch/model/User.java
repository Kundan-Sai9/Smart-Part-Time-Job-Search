package com.example.smartjobsearch.model;

import jakarta.persistence.*;

import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@NoArgsConstructor
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String fullName,username,email,password;
    
    // Profile fields for AI recommendations
    @Column(columnDefinition = "TEXT")
    private String skills; 
    @Column(columnDefinition = "TEXT")
    private String experience,preferredLocation,salaryExpectation;
    @Column(columnDefinition = "TEXT")
    private String bio,preferredJobType;
    
    // Fields for history-based recommendations
    private String jobTitle; // Current or desired job title
    private Integer yearsExperience; // Years of professional experience
    @Column(columnDefinition = "TEXT")
    private String industries; // Comma-separated preferred industries
    @Column(columnDefinition = "TEXT")
    private String certifications; // Professional certifications

    public User(String fullName, String username, String email, String password) {
        this.fullName = fullName;
        this.username = username;
        this.email = email;
        this.password = password;
        // Initialize profile fields with defaults
        this.skills = "";
        this.experience = "";
        this.preferredLocation = "";
        this.salaryExpectation = "";
        this.bio = "";
        this.preferredJobType = "";
    }
}

