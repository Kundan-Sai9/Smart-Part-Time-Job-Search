package com.example.smartjobsearch.service;

import com.example.smartjobsearch.model.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class ProfileScoringService {

    @Autowired
    private CohereApiService cohereApiService;

    public Map<String, Object> analyzeProfile(User user) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // Calculate profile completeness score
            int score = calculateProfileCompleteness(user);
            
            // Generate AI-powered suggestions
            String suggestion = generateAISuggestion(user, score);
            
            result.put("score", score);
            result.put("suggestion", suggestion);
            result.put("user_id", user.getId());
            result.put("analysis_date", new Date());
            
        } catch (Exception e) {
            result.put("error", "Failed to analyze profile: " + e.getMessage());
            result.put("score", 0);
            result.put("suggestion", "Unable to analyze profile at this time. Please try again later.");
        }
        
        return result;
    }
    
    private int calculateProfileCompleteness(User user) {
        int score = 0;
        int maxScore = 100;
        
        // Basic information (30 points)
        if (user.getFullName() != null && !user.getFullName().trim().isEmpty()) score += 10;
        if (user.getEmail() != null && !user.getEmail().trim().isEmpty()) score += 10;
        if (user.getUsername() != null && !user.getUsername().trim().isEmpty()) score += 10;
        
        // Profile details (70 points)
        if (user.getBio() != null && !user.getBio().trim().isEmpty()) score += 20;
        if (user.getSkills() != null && !user.getSkills().trim().isEmpty()) score += 20;
        if (user.getExperience() != null && !user.getExperience().trim().isEmpty()) score += 15;
        if (user.getPreferredJobType() != null && !user.getPreferredJobType().trim().isEmpty()) score += 5;
        if (user.getPreferredLocation() != null && !user.getPreferredLocation().trim().isEmpty()) score += 5;
        if (user.getSalaryExpectation() != null && !user.getSalaryExpectation().trim().isEmpty()) score += 5;
        
        return Math.min(score, maxScore);
    }
    
    private String generateAISuggestion(User user, int score) {
        try {
            StringBuilder prompt = new StringBuilder();
            prompt.append("Analyze this job seeker's profile and provide personalized improvement suggestions:\n\n");
            
            prompt.append("Profile Completeness Score: ").append(score).append("/100\n");
            prompt.append("Name: ").append(user.getFullName() != null ? user.getFullName() : "Not provided").append("\n");
            prompt.append("Bio: ").append(user.getBio() != null ? user.getBio() : "Not provided").append("\n");
            prompt.append("Skills: ").append(user.getSkills() != null ? user.getSkills() : "Not provided").append("\n");
            prompt.append("Experience: ").append(user.getExperience() != null ? user.getExperience() : "Not provided").append("\n");
            prompt.append("Preferred Job Type: ").append(user.getPreferredJobType() != null ? user.getPreferredJobType() : "Not provided").append("\n");
            prompt.append("Preferred Location: ").append(user.getPreferredLocation() != null ? user.getPreferredLocation() : "Not provided").append("\n");
            
            prompt.append("\nProvide a concise, actionable suggestion (max 100 words) to improve their profile for better job matches.");
            
            return cohereApiService.generateText(prompt.toString());
            
        } catch (Exception e) {
            // Fallback suggestions based on score
            if (score < 30) {
                return "Complete your basic profile information including name, email, and add a professional summary to improve your visibility to employers.";
            } else if (score < 60) {
                return "Add more details about your skills and experience. A well-detailed profile gets 3x more job matches.";
            } else if (score < 80) {
                return "Great progress! Add job preferences and expand your skills section to get more targeted recommendations.";
            } else {
                return "Excellent profile! Keep it updated with new skills and experiences to maintain your competitive edge.";
            }
        }
    }
}