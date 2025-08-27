package com.example.smartjobsearch.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import java.util.*;
import java.util.Collections;

@Service
public class CohereApiService {
    @Value("${cohere.api.key}")
    private String apiKey;

    private static final String API_URL = "https://api.cohere.ai/v1/embed";

    public List<Double> getEmbedding(String text) {
        // For now, let's create a simple mock embedding based on text characteristics
        // This allows the feature to work while we debug the API issue
        return generateMockEmbedding(text);
        
        /* TODO: Uncomment when API key is working
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + apiKey);
        headers.set("Content-Type", "application/json");
        headers.set("Cohere-Version", "2022-12-06");
        
        Map<String, Object> body = new HashMap<>();
        body.put("texts", Collections.singletonList(text));
        body.put("model", "embed-english-v2.0");
        
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        
        try {
            Map response = restTemplate.postForObject(API_URL, entity, Map.class);
            List<List<Double>> embeddings = (List<List<Double>>) response.get("embeddings");
            return embeddings.get(0);
        } catch (Exception e) {
            System.err.println("Cohere API Error: " + e.getMessage());
            return generateMockEmbedding(text);
        }
        */
    }
    
    private List<Double> generateMockEmbedding(String text) {
        // Generate a deterministic "embedding" based on text characteristics
        List<Double> embedding = new ArrayList<>(Collections.nCopies(384, 0.0));
        
        String lowerText = text.toLowerCase();
        
        // Create base embedding with very small random component
        int hash = lowerText.hashCode();
        Random random = new Random(hash);
        for (int i = 0; i < 384; i++) {
            embedding.set(i, random.nextGaussian() * 0.01); // Very small random component
        }
        
        // Add much stronger keyword-based features
        double keywordWeight = 2.0; // Increased weight for better similarity
        
        // Technology keywords - these should have high similarity with each other
        if (lowerText.contains("java")) {
            for (int i = 0; i < 30; i++) embedding.set(i, embedding.get(i) + keywordWeight);
        }
        if (lowerText.contains("python")) {
            for (int i = 30; i < 60; i++) embedding.set(i, embedding.get(i) + keywordWeight);
        }
        if (lowerText.contains("javascript") || lowerText.contains("js")) {
            for (int i = 60; i < 90; i++) embedding.set(i, embedding.get(i) + keywordWeight);
        }
        if (lowerText.contains("react") || lowerText.contains("angular") || lowerText.contains("vue")) {
            for (int i = 90; i < 120; i++) embedding.set(i, embedding.get(i) + keywordWeight);
        }
        
        // Programming/Development general - should be similar to all tech jobs
        if (lowerText.contains("software") || lowerText.contains("developer") || lowerText.contains("programming")) {
            for (int i = 120; i < 150; i++) embedding.set(i, embedding.get(i) + keywordWeight);
        }
        if (lowerText.contains("engineer") || lowerText.contains("coding") || lowerText.contains("development")) {
            for (int i = 150; i < 180; i++) embedding.set(i, embedding.get(i) + keywordWeight);
        }
        
        // Seniority level - similar roles should cluster
        if (lowerText.contains("senior") || lowerText.contains("lead") || lowerText.contains("principal")) {
            for (int i = 180; i < 210; i++) embedding.set(i, embedding.get(i) + keywordWeight);
        }
        if (lowerText.contains("junior") || lowerText.contains("entry") || lowerText.contains("intern")) {
            for (int i = 210; i < 240; i++) embedding.set(i, embedding.get(i) + keywordWeight);
        }
        
        // Job type specialization
        if (lowerText.contains("full stack") || lowerText.contains("fullstack")) {
            for (int i = 240; i < 270; i++) embedding.set(i, embedding.get(i) + keywordWeight);
        }
        if (lowerText.contains("backend") || lowerText.contains("back-end") || lowerText.contains("server")) {
            for (int i = 270; i < 300; i++) embedding.set(i, embedding.get(i) + keywordWeight);
        }
        if (lowerText.contains("frontend") || lowerText.contains("front-end") || lowerText.contains("ui") || lowerText.contains("web")) {
            for (int i = 300; i < 330; i++) embedding.set(i, embedding.get(i) + keywordWeight);
        }
        
        // Common job terms - all jobs should have some similarity here
        if (lowerText.contains("job") || lowerText.contains("position") || lowerText.contains("role")) {
            for (int i = 330; i < 360; i++) embedding.set(i, embedding.get(i) + keywordWeight * 0.5);
        }
        if (lowerText.contains("experience") || lowerText.contains("skills") || lowerText.contains("team")) {
            for (int i = 360; i < 384; i++) embedding.set(i, embedding.get(i) + keywordWeight * 0.5);
        }
        
        return embedding;
    }
    
    public String generateText(String prompt) {
        // For now, provide rule-based suggestions until we can fix the API
        return generateMockSuggestion(prompt);
        
        /* TODO: Uncomment when API key is working properly
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + apiKey);
        headers.set("Content-Type", "application/json");
        headers.set("Cohere-Version", "2022-12-06");
        
        Map<String, Object> body = new HashMap<>();
        body.put("prompt", prompt);
        body.put("model", "command");
        body.put("max_tokens", 100);
        body.put("temperature", 0.7);
        
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        
        try {
            Map response = restTemplate.postForObject("https://api.cohere.ai/v1/generate", entity, Map.class);
            List<Map<String, Object>> generations = (List<Map<String, Object>>) response.get("generations");
            return (String) generations.get(0).get("text");
        } catch (Exception e) {
            System.err.println("Cohere Text Generation Error: " + e.getMessage());
            return generateMockSuggestion(prompt);
        }
        */
    }
    
    private String generateMockSuggestion(String prompt) {
        String lowerPrompt = prompt.toLowerCase();
        
        // Parse profile completeness score from prompt
        int score = 0;
        if (lowerPrompt.contains("score: ")) {
            try {
                String scoreStr = lowerPrompt.substring(lowerPrompt.indexOf("score: ") + 7);
                scoreStr = scoreStr.substring(0, scoreStr.indexOf("/"));
                score = Integer.parseInt(scoreStr.trim());
            } catch (Exception e) {
                score = 50; // default
            }
        }
        
        // Analyze what's missing
        boolean hasName = lowerPrompt.contains("name:") && !lowerPrompt.contains("name: not provided");
        boolean hasBio = lowerPrompt.contains("bio:") && !lowerPrompt.contains("bio: not provided");
        boolean hasSkills = lowerPrompt.contains("skills:") && !lowerPrompt.contains("skills: not provided");
        boolean hasExperience = lowerPrompt.contains("experience:") && !lowerPrompt.contains("experience: not provided");
        boolean hasJobType = lowerPrompt.contains("preferred job type:") && !lowerPrompt.contains("preferred job type: not provided");
        boolean hasLocation = lowerPrompt.contains("preferred location:") && !lowerPrompt.contains("preferred location: not provided");
        
        // Generate targeted suggestions
        if (score < 30) {
            return "Start by completing your basic profile: add your full name, write a professional bio highlighting your key strengths, and list your main skills. These fundamental details help employers find and evaluate you.";
        } else if (score < 60) {
            if (!hasBio) {
                return "Add a compelling professional bio that showcases your unique value proposition. Highlight your key achievements and what makes you stand out to potential employers.";
            } else if (!hasSkills) {
                return "List your technical and soft skills comprehensively. Include programming languages, tools, frameworks, and interpersonal abilities relevant to your target roles.";
            } else {
                return "Expand your experience section with specific achievements and quantifiable results. Detail your responsibilities and impact in previous roles.";
            }
        } else if (score < 80) {
            if (!hasJobType) {
                return "Add job type preferences to help our AI recommend the most relevant positions. Specify whether you prefer full-time, part-time, contract, or remote work.";
            } else if (!hasLocation) {
                return "Add your preferred work location to get more targeted job recommendations in your desired area or specify if you're open to remote work.";
            } else {
                return "Fine-tune your profile by adding more specific skills and updating your experience with recent projects. Consider adding salary expectations.";
            }
        } else {
            return "Excellent profile! Keep it fresh by regularly updating your skills, adding new experiences, and refining your bio to reflect your career growth.";
        }
    }
}
