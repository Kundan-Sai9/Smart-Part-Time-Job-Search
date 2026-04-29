

package com.example.smartjobsearch.service;

import com.example.smartjobsearch.model.Job;
import com.example.smartjobsearch.model.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class JobRecommendationService {
    
    @Autowired
    private JobService jobService;
    // History-aware personalization helpers were intentionally removed in favor of ML-based recommendations.
    // The remaining matching utilities below are kept as simple fallbacks.

    /**
     * Get personalized job recommendations for a user using AI-powered matching with history analysis
     */
    public JobRecommendationResult getPersonalizedRecommendations(User user, int limit) {
        try {
            // Build a concise user profile text to send to the ML service
            StringBuilder profileBuilder = new StringBuilder();
            if (user.getSkills() != null) profileBuilder.append(user.getSkills()).append(" ");
            if (user.getBio() != null) profileBuilder.append(user.getBio()).append(" ");
            if (user.getExperience() != null) profileBuilder.append(user.getExperience()).append(" ");

            String profileText = profileBuilder.toString().trim();

            // Call the external Python ML service (FastAPI) for recommendations
            org.springframework.web.client.RestTemplate rest = new org.springframework.web.client.RestTemplate();
            String mlUrl = System.getenv().getOrDefault("ML_RECOMMENDER_URL", "http://localhost:8000/recommend");

            Map<String, Object> payload = new HashMap<>();
            payload.put("user_profile_text", profileText);
            payload.put("top_k", limit);

            @SuppressWarnings("unchecked")
            Map<String, Object> resp = rest.postForObject(mlUrl, payload, Map.class);

            List<JobRecommendationScore> recommendations = new ArrayList<>();

            if (resp != null && resp.containsKey("recommendations")) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> recs = (List<Map<String, Object>>) resp.get("recommendations");
                for (Map<String, Object> r : recs) {
                    // Try to map returned job to internal Job entity by id if possible
                    Job job = null;
                    if (r.containsKey("job_id") && r.get("job_id") != null) {
                        try {
                            long jid = ((Number) r.get("job_id")).longValue();
                            java.util.Optional<Job> jopt = jobService.getJobById(jid);
                            if (jopt.isPresent()) job = jopt.get();
                        } catch (Exception ex) {
                            // ignore parsing issues and fall back to constructing a Job object below
                        }
                    }

                    if (job == null) {
                        job = new Job();
                        if (r.get("title") != null) job.setTitle(String.valueOf(r.get("title")));
                        if (r.get("company") != null) job.setCompany(String.valueOf(r.get("company")));
                        if (r.get("location") != null) job.setLocation(String.valueOf(r.get("location")));
                        if (r.get("description") != null) job.setDescription(String.valueOf(r.get("description")));
                    }

                    double score = 0.0;
                    if (r.get("score") instanceof Number) score = ((Number) r.get("score")).doubleValue();

                    @SuppressWarnings("unchecked")
                    List<String> reasons = r.get("reasons") != null ? (List<String>) r.get("reasons") : getMatchReasons(user, job);

                    recommendations.add(new JobRecommendationScore(job, score, reasons));
                }
            }

            double profileCompleteness = calculateProfileCompleteness(user);
            List<String> insights = generateRecommendationInsights(user, recommendations);

            return new JobRecommendationResult(recommendations, profileCompleteness, insights, jobService.getAllJobs().size());

        } catch (Exception e) {
            // If ML service fails, fall back to personalized heuristic recommendations
            System.out.println("ML recommender failed, falling back to local recommendations: " + e.getMessage());
            e.printStackTrace();
            List<Job> allJobs = jobService.getAllJobs();
            List<JobRecommendationScore> fallback = getFallbackRecommendations(user, allJobs, limit);
            double profileCompleteness = calculateProfileCompleteness(user);
            return new JobRecommendationResult(fallback, profileCompleteness, generateRecommendationInsights(user, fallback.stream().collect(Collectors.toList())), allJobs.size());
        }
    }
    
    /**
     * Original calculation method (kept as fallback)
     */
    @SuppressWarnings("unused")
    private double calculateRecommendationScore(User user, Job job) {
        double score = 0.0;
        double maxScore = 0.0;
        
        // Skills matching (40% weight) - include only if user provided skills
        String jobSkillsText = (job.getTitle() == null ? "" : job.getTitle()) + " " + (job.getDescription() == null ? "" : job.getDescription());
        if (job.getSkills() != null && !job.getSkills().trim().isEmpty()) {
            jobSkillsText += " " + job.getSkills(); // Add the dedicated skills field
        }
        if (user.getSkills() != null && !user.getSkills().trim().isEmpty()) {
            double skillsScore = calculateSkillsMatch(user.getSkills(), jobSkillsText);
            score += skillsScore * 0.4;
            maxScore += 0.4;
        }

        // Location preference (20% weight) - include only if user provided preferred location
        if (user.getPreferredLocation() != null && !user.getPreferredLocation().trim().isEmpty()) {
            double locationScore = calculateLocationMatch(user.getPreferredLocation(), job.getLocation());
            score += locationScore * 0.2;
            maxScore += 0.2;
        }

        // Job type preference (15% weight) - include only if user provided preferred job type
        if (user.getPreferredJobType() != null && !user.getPreferredJobType().trim().isEmpty()) {
            double jobTypeScore = calculateJobTypeMatch(user.getPreferredJobType(), job.getDescription());
            score += jobTypeScore * 0.15;
            maxScore += 0.15;
        }

        // Experience level matching (15% weight) - include only if user provided experience
        if (user.getExperience() != null && !user.getExperience().trim().isEmpty()) {
            double experienceScore = calculateExperienceMatch(user.getExperience(), job.getDescription());
            score += experienceScore * 0.15;
            maxScore += 0.15;
        }

        // Bio/interest matching (10% weight) - include only if user provided bio
        if (user.getBio() != null && !user.getBio().trim().isEmpty()) {
            double bioScore = calculateTextSimilarity(user.getBio(), job.getDescription());
            score += bioScore * 0.1;
            maxScore += 0.1;
        }
        
        // Normalize score to 0-1 range
        return maxScore > 0 ? score / maxScore : 0.0;
    }
    
    /**
     * Calculate skills matching score - improved algorithm
     */
    @SuppressWarnings("unused")
    private double calculateSkillsMatch(String userSkills, String jobText) {
        if (userSkills == null || userSkills.trim().isEmpty()) return 0.0;
        
        String[] skills = userSkills.toLowerCase().split(",");
        String jobTextLower = jobText.toLowerCase();
        
        int exactMatches = 0;
        int partialMatches = 0;
        int totalSkills = 0;
        
        for (String skill : skills) {
            skill = skill.trim();
            if (skill.isEmpty()) continue;
            
            totalSkills++;
            
            // Check for exact match
            if (jobTextLower.contains(skill)) {
                exactMatches++;
            } else {
                // Check for partial matches (for compound skills like "javascript" matching "js")
                if (checkPartialSkillMatch(skill, jobTextLower)) {
                    partialMatches++;
                }
            }
        }
        
        if (totalSkills == 0) return 0.0;
        
        // Calculate weighted score: exact matches worth 1.0, partial matches worth 0.5
        double score = ((double) exactMatches + (partialMatches * 0.5)) / totalSkills;
        
        // Bonus for high match percentage
        if (score > 0.7) score = Math.min(1.0, score * 1.1);
        
        return score;
    }
    
    /**
     * Check for partial skill matches (e.g., "js" matches "javascript")
     */
    @SuppressWarnings("unused")
    private boolean checkPartialSkillMatch(String userSkill, String jobText) {
        // Common skill mappings
        Map<String, String[]> skillMappings = new HashMap<>();
        skillMappings.put("javascript", new String[]{"js", "node", "react", "angular", "vue"});
        skillMappings.put("js", new String[]{"javascript", "node", "react", "angular", "vue"});
        skillMappings.put("python", new String[]{"django", "flask", "fastapi", "py"});
        skillMappings.put("java", new String[]{"spring", "springboot", "hibernate"});
        skillMappings.put("c#", new String[]{"csharp", "dotnet", ".net", "asp.net"});
        skillMappings.put("react", new String[]{"javascript", "js", "frontend", "reactjs"});
        skillMappings.put("angular", new String[]{"javascript", "js", "frontend", "typescript"});
        skillMappings.put("node", new String[]{"nodejs", "javascript", "js", "backend"});
        skillMappings.put("sql", new String[]{"mysql", "postgresql", "database", "db"});
        skillMappings.put("html", new String[]{"frontend", "web", "css"});
        skillMappings.put("css", new String[]{"frontend", "web", "html", "scss", "sass"});
        
        // Check if user skill has related terms in job text
        String[] relatedTerms = skillMappings.get(userSkill);
        if (relatedTerms != null) {
            for (String term : relatedTerms) {
                if (jobText.contains(term)) {
                    return true;
                }
            }
        }
        
        // Check reverse mapping (if job contains skill, check if user skill is related)
        for (Map.Entry<String, String[]> entry : skillMappings.entrySet()) {
            if (jobText.contains(entry.getKey())) {
                for (String relatedSkill : entry.getValue()) {
                    if (relatedSkill.equals(userSkill)) {
                        return true;
                    }
                }
            }
        }
        
        return false;
    }
    
    /**
     * Calculate location matching score
     */
    @SuppressWarnings("unused")
    private double calculateLocationMatch(String preferredLocation, String jobLocation) {
        if (preferredLocation == null || jobLocation == null) return 0.0;
        
        String prefLower = preferredLocation.toLowerCase().trim();
        String jobLower = jobLocation.toLowerCase().trim();
        
        if (prefLower.equals(jobLower)) return 1.0;
        if (jobLower.contains(prefLower) || prefLower.contains(jobLower)) return 0.7;
        
        // Check for common location keywords
        String[] prefWords = prefLower.split(" ");
        String[] jobWords = jobLower.split(" ");
        
        int matchCount = 0;
        for (String prefWord : prefWords) {
            for (String jobWord : jobWords) {
                if (prefWord.equals(jobWord) && prefWord.length() > 2) {
                    matchCount++;
                    break;
                }
            }
        }
        
        return Math.min(1.0, (double) matchCount / Math.max(prefWords.length, jobWords.length));
    }
    
    /**
     * Calculate job type matching score
     */
    @SuppressWarnings("unused")
    private double calculateJobTypeMatch(String preferredJobType, String jobDescription) {
        if (preferredJobType == null || jobDescription == null) return 0.0;
        
        String prefLower = preferredJobType.toLowerCase();
        String descLower = jobDescription.toLowerCase();
        
        // Direct matches
        if (descLower.contains(prefLower)) return 1.0;
        
        // Check for job type keywords
        Map<String, String[]> jobTypeKeywords = Map.of(
            "full-time", new String[]{"full-time", "full time", "permanent", "regular"},
            "part-time", new String[]{"part-time", "part time", "flexible", "hourly"},
            "contract", new String[]{"contract", "contractor", "freelance", "temporary", "temp"},
            "remote", new String[]{"remote", "work from home", "telecommute", "distributed"}
        );
        
        String[] keywords = jobTypeKeywords.get(prefLower);
        if (keywords != null) {
            for (String keyword : keywords) {
                if (descLower.contains(keyword)) return 0.8;
            }
        }
        
        return 0.0;
    }
    
    /**
     * Calculate experience level matching score
     */
    @SuppressWarnings("unused")
    private double calculateExperienceMatch(String userExperience, String jobDescription) {
        if (userExperience == null || jobDescription == null) return 0.0;
        
        String expLower = userExperience.toLowerCase();
        String descLower = jobDescription.toLowerCase();
        
        // Extract experience level indicators
        boolean userIsEntry = expLower.contains("entry") || expLower.contains("junior") || expLower.contains("beginner");
        boolean userIsMid = expLower.contains("mid") || expLower.contains("intermediate") || expLower.contains("3-5") || expLower.contains("2-4");
        boolean userIsSenior = expLower.contains("senior") || expLower.contains("lead") || expLower.contains("5+") || expLower.contains("expert");
        
        boolean jobIsEntry = descLower.contains("entry") || descLower.contains("junior") || descLower.contains("0-2 years");
        boolean jobIsMid = descLower.contains("mid") || descLower.contains("intermediate") || descLower.contains("3-5") || descLower.contains("2-4");
        boolean jobIsSenior = descLower.contains("senior") || descLower.contains("lead") || descLower.contains("5+") || descLower.contains("manager");
        
        // Match experience levels
        if ((userIsEntry && jobIsEntry) || (userIsMid && jobIsMid) || (userIsSenior && jobIsSenior)) {
            return 1.0;
        }
        
        // Adjacent levels get partial credit
        if ((userIsEntry && jobIsMid) || (userIsMid && jobIsEntry) || (userIsMid && jobIsSenior) || (userIsSenior && jobIsMid)) {
            return 0.6;
        }
        
        return 0.3; // Default partial match
    }
    
    /**
     * Calculate text similarity using simple keyword matching
     */
    @SuppressWarnings("unused")
    private double calculateTextSimilarity(String text1, String text2) {
        if (text1 == null || text2 == null) return 0.0;
        
        String[] words1 = text1.toLowerCase().split("\\W+");
        String[] words2 = text2.toLowerCase().split("\\W+");
        
        Set<String> set1 = Arrays.stream(words1)
            .filter(word -> word.length() > 3) // Filter out short words
            .collect(Collectors.toSet());
        
        Set<String> set2 = Arrays.stream(words2)
            .filter(word -> word.length() > 3)
            .collect(Collectors.toSet());
        
        Set<String> intersection = new HashSet<>(set1);
        intersection.retainAll(set2);
        
        Set<String> union = new HashSet<>(set1);
        union.addAll(set2);
        
        return union.size() > 0 ? (double) intersection.size() / union.size() : 0.0;
    }
    
    /**
     * Get reasons why a job was recommended
     */
    private List<String> getMatchReasons(User user, Job job) {
        List<String> reasons = new ArrayList<>();

        // Build a human-friendly skills reason (mention count and examples)
        if (user.getSkills() != null && !user.getSkills().trim().isEmpty()) {
            String[] skills = user.getSkills().toLowerCase().split(",");
            String jobText = ((job.getTitle() == null ? "" : job.getTitle()) + " " + (job.getDescription() == null ? "" : job.getDescription()) + " " + (job.getSkills() == null ? "" : job.getSkills())).toLowerCase();

            List<String> matchedSkills = Arrays.stream(skills)
                .map(String::trim)
                .filter(skill -> !skill.isEmpty() && jobText.contains(skill))
                .distinct()
                .collect(Collectors.toList());

            if (!matchedSkills.isEmpty()) {
                String skillSample = matchedSkills.size() > 4 ? String.join(", ", matchedSkills.subList(0, 4)) + ", ..." : String.join(", ", matchedSkills);
                reasons.add("Matched skills (" + matchedSkills.size() + "): " + skillSample);
            }
        }

        // Location reason (exact vs partial)
        if (user.getPreferredLocation() != null && !user.getPreferredLocation().trim().isEmpty() && job.getLocation() != null) {
            String pref = user.getPreferredLocation().toLowerCase().trim();
            String jobLoc = job.getLocation().toLowerCase().trim();
            if (pref.equals(jobLoc)) {
                reasons.add("Located in your preferred location: " + job.getLocation());
            } else if (jobLoc.contains(pref) || pref.contains(jobLoc)) {
                reasons.add("Near your preferred location: " + job.getLocation());
            }
        }

        // Job type reason
        if (user.getPreferredJobType() != null && !user.getPreferredJobType().trim().isEmpty() && job.getDescription() != null) {
            String prefType = user.getPreferredJobType().toLowerCase().trim();
            String desc = job.getDescription().toLowerCase();
            if (desc.contains(prefType)) {
                reasons.add("Job type matches your preference: " + user.getPreferredJobType());
            }
        }

        // Experience reason
        if (user.getExperience() != null && !user.getExperience().trim().isEmpty() && job.getDescription() != null) {
            String userLevel = getExperienceLabel(user.getExperience());
            String jobLevel = getExperienceLabel(job.getDescription());
            if (!"Unknown".equals(userLevel) && !"Unknown".equals(jobLevel)) {
                if (userLevel.equals(jobLevel)) {
                    reasons.add("Experience level matches: " + userLevel);
                } else {
                    reasons.add("Experience level: your profile=" + userLevel + ", job=" + jobLevel);
                }
            }
        }

        // Profile completeness hint (useful to explain low scores)
        double completeness = calculateProfileCompleteness(user);
        reasons.add("Profile completeness: " + (int)Math.round(completeness) + "%");

        // If we couldn't find any specific signal, fall back to a generic but helpful reason
        if (reasons.isEmpty()) {
            reasons.add("General profile compatibility — add skills and preferences for stronger matches");
        }

        return reasons;
    }

    /**
     * Extract a simple experience level label from free-text (Entry, Mid, Senior)
     */
    private String getExperienceLabel(String text) {
        if (text == null) return "Unknown";
        String lower = text.toLowerCase();
        if (lower.contains("entry") || lower.contains("junior") || lower.contains("0-2") || lower.contains("0 - 2") || lower.contains("beginner")) return "Entry";
        if (lower.contains("mid") || lower.contains("intermediate") || lower.contains("2-4") || lower.contains("3-5")) return "Mid";
        if (lower.contains("senior") || lower.contains("lead") || lower.contains("5+") || lower.contains("5 -") || lower.contains("manager") || lower.contains("expert")) return "Senior";
        return "Unknown";
    }
    
    /**
     * Calculate profile completeness percentage
     */
    private double calculateProfileCompleteness(User user) {
        int totalFields = 6;
        int completedFields = 0;
        
        if (user.getSkills() != null && !user.getSkills().trim().isEmpty()) completedFields++;
        if (user.getExperience() != null && !user.getExperience().trim().isEmpty()) completedFields++;
        if (user.getPreferredLocation() != null && !user.getPreferredLocation().trim().isEmpty()) completedFields++;
        if (user.getSalaryExpectation() != null && !user.getSalaryExpectation().trim().isEmpty()) completedFields++;
        if (user.getBio() != null && !user.getBio().trim().isEmpty()) completedFields++;
        if (user.getPreferredJobType() != null && !user.getPreferredJobType().trim().isEmpty()) completedFields++;
        
        return (double) completedFields / totalFields * 100;
    }
    
    /**
     * Generate insights about the recommendations
     */
    private List<String> generateRecommendationInsights(User user, List<JobRecommendationScore> recommendations) {
        List<String> insights = new ArrayList<>();
        
        if (recommendations.isEmpty()) {
            insights.add("No personalized recommendations available. Complete your profile to get better matches.");
            return insights;
        }
        
        // Analyze top matches
        double avgScore = recommendations.stream()
            .mapToDouble(JobRecommendationScore::getScore)
            .average()
            .orElse(0.0);
        
        if (avgScore > 0.7) {
            insights.add(" Excellent matches found! Your profile aligns well with available opportunities.");
        } else if (avgScore > 0.5) {
            insights.add(" Good matches available. Consider updating your profile for even better recommendations.");
        } else {
            insights.add("Basic matches found. Enhance your profile with more skills and preferences for better results.");
        }
        
        // Profile improvement suggestions
        if (user.getSkills() == null || user.getSkills().trim().isEmpty()) {
            insights.add(" Add your skills to get more targeted job recommendations.");
        }
        
        if (user.getPreferredLocation() == null || user.getPreferredLocation().trim().isEmpty()) {
            insights.add("Set your preferred location to find jobs in your desired area.");
        }
        
        if (user.getBio() == null || user.getBio().trim().isEmpty()) {
            insights.add(" Add a professional bio to improve matching accuracy.");
        }
        
        return insights;
    }
    
    /**
     * Inner class to store user's job preferences learned from history
     */
    // Removed UserJobPreferences type (history-based personalization replaced by ML service)
    
    // Helper classes
    public static class JobRecommendationResult {
        private final List<JobRecommendationScore> recommendations;
        private final double profileCompleteness;
        private final List<String> insights;
        private final int totalJobsAnalyzed;
        
        public JobRecommendationResult(List<JobRecommendationScore> recommendations, double profileCompleteness, 
                                     List<String> insights, int totalJobsAnalyzed) {
            this.recommendations = recommendations;
            this.profileCompleteness = profileCompleteness;
            this.insights = insights;
            this.totalJobsAnalyzed = totalJobsAnalyzed;
        }
        
        // Getters
        public List<JobRecommendationScore> getRecommendations() { return recommendations; }
        public double getProfileCompleteness() { return profileCompleteness; }
        public List<String> getInsights() { return insights; }
        public int getTotalJobsAnalyzed() { return totalJobsAnalyzed; }
    }
    
    public static class JobRecommendationScore {
        private final Job job;
        private final double score;
        private final List<String> reasons;
        
        public JobRecommendationScore(Job job, double score, List<String> reasons) {
            this.job = job;
            this.score = score;
            this.reasons = reasons;
        }
        
        // Getters
        public Job getJob() { return job; }
        public double getScore() { return score; }
        public List<String> getReasons() { return reasons; }
    }
    
    /**
     * Provide fallback recommendations for users - always show jobs
     */
    private List<JobRecommendationScore> getFallbackRecommendations(User user, List<Job> availableJobs, int limit) {
        // Compute a heuristic score per job using existing matching functions
        return availableJobs.stream()
            .sorted(Comparator.comparingDouble(job -> -calculateRecommendationScore(user, job)))
            .limit(limit)
            .map(job -> new JobRecommendationScore(
                job,
                calculateRecommendationScore(user, job),
                getMatchReasons(user, job)
            ))
            .collect(Collectors.toList());
    }
}