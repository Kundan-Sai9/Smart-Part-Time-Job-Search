
package com.example.smartjobsearch.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.Collections;

@Service
@SuppressWarnings("unused")
public class CohereApiService {
    @Value("${cohere.api.key}")
    private String apiKey;

    private static final String API_URL = "https://api.cohere.ai/v1/embed";
    @Value("${cohere.embedding.model:embed-english-v2.0}")
    private String embeddingModel;

    @SuppressWarnings("unused")
    private static final String UNUSED_NOTE = "API_URL kept for future real integration";

    public List<Double> getEmbedding(String text) {
        // If API key is not configured, fall back to deterministic mock embedding
        if (apiKey == null || apiKey.trim().isEmpty()) {
            System.out.println("Cohere API key missing; using mock embedding");
            return generateMockEmbedding(text);
        }

        try {
            org.springframework.web.client.RestTemplate rest = new org.springframework.web.client.RestTemplate();
            java.util.Map<String, Object> payload = new java.util.HashMap<>();
            // Cohere expects a list of texts
            payload.put("model", embeddingModel);
            payload.put("texts", java.util.Collections.singletonList(text));

            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.set("Authorization", "Bearer " + apiKey);
            headers.set("Content-Type", "application/json");

            org.springframework.http.HttpEntity<java.util.Map<String, Object>> entity = new org.springframework.http.HttpEntity<>(payload, headers);

            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> resp = rest.postForObject(API_URL, entity, java.util.Map.class);

            if (resp == null) {
                throw new RuntimeException("Empty response from Cohere embed API");
            }

            Object embeddingsObj = resp.get("embeddings");
            if (embeddingsObj instanceof java.util.List) {
                java.util.List<?> embList = (java.util.List<?>) embeddingsObj;
                if (!embList.isEmpty()) {
                    Object first = embList.get(0);
                    java.util.List<Number> vector = null;
                    if (first instanceof java.util.List) {
                        // shape: { "embeddings": [[0.1, 0.2, ...]] }
                        vector = (java.util.List<Number>) first;
                    } else if (first instanceof java.util.Map) {
                        // shape: { "embeddings": [ { "embedding": [...] } ] }
                        Object inner = ((java.util.Map<?,?>) first).get("embedding");
                        if (inner instanceof java.util.List) {
                            vector = (java.util.List<Number>) inner;
                        }
                    }

                    if (vector != null) {
                        java.util.List<Double> out = new java.util.ArrayList<>(vector.size());
                        for (Number n : vector) out.add(n.doubleValue());
                        return out;
                    }
                }
            }

            // If response shape unexpected, fall back to mock
            System.err.println("Unexpected embedding response format from Cohere; falling back to mock");
            return generateMockEmbedding(text);

        } catch (Exception e) {
            System.err.println("Cohere embed call failed: " + e.getMessage());
            e.printStackTrace();
            return generateMockEmbedding(text);
        }
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
