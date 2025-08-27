package com.example.smartjobsearch.model;

import jakarta.persistence.*;

@Entity
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String fullName;
    private String username;
    private String email;
    private String password;
    
    // Profile fields for AI recommendations
    @Column(columnDefinition = "TEXT")
    private String skills; // Comma-separated skills
    @Column(columnDefinition = "TEXT")
    private String experience; // Experience level and description
    private String preferredLocation;
    private String salaryExpectation;
    @Column(columnDefinition = "TEXT")
    private String bio; // Professional bio/summary
    private String preferredJobType; // Full-time, Part-time, Contract, etc.
    
    // Fields for history-based recommendations
    private String jobTitle; // Current or desired job title
    private Integer yearsExperience; // Years of professional experience
    @Column(columnDefinition = "TEXT")
    private String industries; // Comma-separated preferred industries
    @Column(columnDefinition = "TEXT")
    private String certifications; // Professional certifications

    // Constructors
    public User() {}

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

    // Getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    
    // Profile getters and setters
    public String getSkills() { return skills; }
    public void setSkills(String skills) { this.skills = skills; }
    public String getExperience() { return experience; }
    public void setExperience(String experience) { this.experience = experience; }
    public String getPreferredLocation() { return preferredLocation; }
    public void setPreferredLocation(String preferredLocation) { this.preferredLocation = preferredLocation; }
    public String getSalaryExpectation() { return salaryExpectation; }
    public void setSalaryExpectation(String salaryExpectation) { this.salaryExpectation = salaryExpectation; }
    public String getBio() { return bio; }
    public void setBio(String bio) { this.bio = bio; }
    public String getPreferredJobType() { return preferredJobType; }
    public void setPreferredJobType(String preferredJobType) { this.preferredJobType = preferredJobType; }
    
    // Additional profile getters and setters for history-based recommendations
    public String getJobTitle() { return jobTitle; }
    public void setJobTitle(String jobTitle) { this.jobTitle = jobTitle; }
    public Integer getYearsExperience() { return yearsExperience; }
    public void setYearsExperience(Integer yearsExperience) { this.yearsExperience = yearsExperience; }
    public String getIndustries() { return industries; }
    public void setIndustries(String industries) { this.industries = industries; }
    public String getCertifications() { return certifications; }
    public void setCertifications(String certifications) { this.certifications = certifications; }
}

