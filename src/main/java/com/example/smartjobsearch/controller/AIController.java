package com.example.smartjobsearch.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpEntity;

import java.util.*;

@RestController
@RequestMapping("/api/ai")
public class AIController {

    @Value("${cohere.api.key}")
    private String cohereApiKey;
    
    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostMapping("/job-recommendations")
    public ResponseEntity<?> getJobRecommendations(@RequestBody Map<String, Object> request) {
        try {
            String userProfile = (String) request.get("userProfile");
            List<Map<String, Object>> jobs = (List<Map<String, Object>>) request.get("jobs");
            Long userId = Long.valueOf(request.get("userId").toString());
            
            System.out.println("AI Job Recommendations - User Profile: " + userProfile);
            System.out.println("AI Job Recommendations - Jobs count: " + jobs.size());
            
            // Prepare prompt for Cohere AI
            StringBuilder prompt = new StringBuilder();
            prompt.append("You are an AI job matching expert. Analyze the user profile and recommend the best matching jobs.\n\n");
            prompt.append("USER PROFILE:\n").append(userProfile).append("\n\n");
            prompt.append("AVAILABLE JOBS:\n");
            
            for (int i = 0; i < jobs.size(); i++) {
                Map<String, Object> job = jobs.get(i);
                prompt.append("Job ").append(i + 1).append(":\n");
                prompt.append("- ID: ").append(job.get("id")).append("\n");
                prompt.append("- Title: ").append(job.get("title")).append("\n");
                prompt.append("- Company: ").append(job.get("company")).append("\n");
                prompt.append("- Location: ").append(job.get("location")).append("\n");
                prompt.append("- Skills Required: ").append(job.get("skills")).append("\n");
                prompt.append("- Job Type: ").append(job.get("jobType")).append("\n");
                prompt.append("- Description: ").append(job.get("description")).append("\n\n");
            }
            
            prompt.append("TASK: Rank these jobs based on how well they match the user's profile. Consider:\n");
            prompt.append("1. Skills alignment (most important)\n");
            prompt.append("2. Location preferences\n");
            prompt.append("3. Job type preferences\n");
            prompt.append("4. Experience level match\n");
            prompt.append("5. Career growth potential\n\n");
            
            prompt.append("RESPONSE FORMAT: Return ONLY a JSON array with the top 5 recommendations in this exact format:\n");
            prompt.append("RESPONSE FORMAT: Return ONLY a JSON array with the top 5 recommendations. Each object must use the exact 'ID' value from the job list above as 'jobId'. Example:\n");
            prompt.append("[\n");
            prompt.append("  {\n");
            prompt.append("    \"jobId\": (use the actual job ID from above),\n");
            prompt.append("    \"matchScore\": 85,\n");
            prompt.append("    \"reasons\": [\"Your Node.js skills match perfectly\", \"Location matches your preference\"]\n");
            prompt.append("  }\n");
            prompt.append("]\n");
            prompt.append("Match scores should be 0-100. Only include jobs with scores above 30.\n");
            
            // Call Cohere API
            String cohereResponse = callCohereAPI(prompt.toString());
            
            // Parse the AI response
            List<Map<String, Object>> recommendations = parseAIResponse(cohereResponse);
            
            // Create response
            Map<String, Object> response = new HashMap<>();
            response.put("recommendations", recommendations);
            response.put("insights", Arrays.asList(
                "Cohere AI analyzed job compatibility based on your profile",
                "Recommendations ranked by skills match and career fit",
                "AI considered " + jobs.size() + " available positions"
            ));
            response.put("ai_analysis", "Powered by Cohere AI language model");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            System.err.println("AI recommendation error: " + e.getMessage());
            e.printStackTrace();
            
            // Return fallback response
            Map<String, Object> fallbackResponse = new HashMap<>();
            fallbackResponse.put("recommendations", new ArrayList<>());
            fallbackResponse.put("insights", Arrays.asList("AI analysis temporarily unavailable"));
            fallbackResponse.put("error", "AI service unavailable: " + e.getMessage());
            
            return ResponseEntity.ok(fallbackResponse);
        }
    }
    
    private String callCohereAPI(String prompt) throws Exception {
        RestTemplate restTemplate = new RestTemplate();
        
        // Prepare Cohere API request
        Map<String, Object> cohereRequest = new HashMap<>();
        cohereRequest.put("model", "command-xlarge");
        cohereRequest.put("prompt", prompt);
        cohereRequest.put("max_tokens", 1000);
        cohereRequest.put("temperature", 0.3);
        cohereRequest.put("k", 0);
        cohereRequest.put("stop_sequences", new String[]{});
        cohereRequest.put("return_likelihoods", "NONE");
        
        // Set headers
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + cohereApiKey);
        headers.set("Content-Type", "application/json");
        headers.set("Cohere-Version", "2022-12-06");
        
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(cohereRequest, headers);
        
        // Make API call
        String cohereUrl = "https://api.cohere.ai/v1/generate";
        Map<String, Object> cohereResponse = restTemplate.postForObject(cohereUrl, entity, Map.class);
        
        if (cohereResponse != null && cohereResponse.containsKey("generations")) {
            List<Map<String, Object>> generations = (List<Map<String, Object>>) cohereResponse.get("generations");
            if (!generations.isEmpty()) {
                return (String) generations.get(0).get("text");
            }
        }
        
        throw new Exception("Invalid response from Cohere API");
    }
    
    private List<Map<String, Object>> parseAIResponse(String aiResponse) {
        try {
            System.out.println("Raw AI Response: " + aiResponse);
            
            // Find JSON array in the response
            String jsonStr = aiResponse.trim();
            int startIndex = jsonStr.indexOf('[');
            int endIndex = jsonStr.lastIndexOf(']') + 1;
            
            if (startIndex >= 0 && endIndex > startIndex) {
                jsonStr = jsonStr.substring(startIndex, endIndex);
                
                // Clean up common JSON formatting issues
                jsonStr = jsonStr
                    .replaceAll(",\\s*]", "]")  // Remove trailing commas before closing bracket
                    .replaceAll(",\\s*}", "}")  // Remove trailing commas before closing brace
                    .replaceAll("\\s+", " ")    // Normalize whitespace
                    .trim();
                
                try {
                    // Use Jackson for proper JSON parsing
                    JsonNode jsonArray = objectMapper.readTree(jsonStr);
                    List<Map<String, Object>> recommendations = new ArrayList<>();
                    
                    for (JsonNode jobNode : jsonArray) {
                        Map<String, Object> rec = new HashMap<>();
                        
                        if (jobNode.has("jobId")) {
                            rec.put("jobId", jobNode.get("jobId").asInt());
                        }
                        if (jobNode.has("matchScore")) {
                            rec.put("matchScore", jobNode.get("matchScore").asInt());
                        }
                        if (jobNode.has("reasons")) {
                            JsonNode reasonsNode = jobNode.get("reasons");
                            List<String> reasons = new ArrayList<>();
                            if (reasonsNode.isArray()) {
                                for (JsonNode reason : reasonsNode) {
                                    reasons.add(reason.asText());
                                }
                            }
                            rec.put("reasons", reasons);
                        }
                        
                        if (rec.containsKey("jobId") && rec.containsKey("matchScore")) {
                            recommendations.add(rec);
                        }
                    }
                    
                    return recommendations;
                } catch (Exception jsonEx) {
                    System.err.println("JSON parsing failed, trying fallback parser: " + jsonEx.getMessage());
                    return fallbackJsonParse(jsonStr);
                }
            }
        } catch (Exception e) {
            System.err.println("Error parsing AI response: " + e.getMessage());
        }
        
        return new ArrayList<>();
    }
    
    private List<Map<String, Object>> fallbackJsonParse(String jsonStr) {
        List<Map<String, Object>> recommendations = new ArrayList<>();
        
        try {
            // Fallback simple parsing
            String[] jobs = jsonStr.replace("[", "").replace("]", "").split("\\},\\s*\\{");
            
            for (String job : jobs) {
                job = job.replace("{", "").replace("}", "").trim();
                if (!job.isEmpty()) {
                    Map<String, Object> rec = new HashMap<>();
                    String[] fields = job.split(",(?=\\s*\"\\w+\":|\\s*\\w+:)");
                    
                    for (String field : fields) {
                        if (field.contains("jobId")) {
                            String value = field.split(":")[1].trim().replaceAll("[^0-9]", "");
                            if (!value.isEmpty()) {
                                rec.put("jobId", Integer.valueOf(value));
                            }
                        } else if (field.contains("matchScore")) {
                            String value = field.split(":")[1].trim().replaceAll("[^0-9]", "");
                            if (!value.isEmpty()) {
                                rec.put("matchScore", Integer.valueOf(value));
                            }
                        } else if (field.contains("reasons")) {
                            // Extract reasons array - simplified parsing
                            int start = field.indexOf('[');
                            int end = field.lastIndexOf(']');
                            if (start >= 0 && end > start) {
                                String reasonsStr = field.substring(start + 1, end);
                                String[] reasonsArray = reasonsStr.split("\",\\s*\"");
                                List<String> reasons = new ArrayList<>();
                                for (String reason : reasonsArray) {
                                    String cleanReason = reason.replace("\"", "").trim();
                                    if (!cleanReason.isEmpty()) {
                                        reasons.add(cleanReason);
                                    }
                                }
                                rec.put("reasons", reasons);
                            }
                        }
                    }
                    
                    if (rec.containsKey("jobId") && rec.containsKey("matchScore")) {
                        recommendations.add(rec);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Fallback parsing failed: " + e.getMessage());
        }
        
        return recommendations;
    }
}
