

package com.example.smartjobsearch.service;

import com.example.smartjobsearch.model.AppliedJob;
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
    
    @Autowired
    private AppliedJobService appliedJobService;
    
    /**
     * Get personalized job recommendations for a user using AI-powered matching with history analysis
     */
    public JobRecommendationResult getPersonalizedRecommendations(User user, int limit) {
        try {
            // Get all available jobs
            List<Job> allJobs = jobService.getAllJobs();
            
            // Debug logging
            System.out.println("DEBUG - Total jobs in database: " + allJobs.size());
            System.out.println("DEBUG - User skills: " + user.getSkills());
            
            // Get user's job history for analysis
            List<AppliedJob> userApplicationHistory = appliedJobService.findByUserId(user.getId());
            
            // Get jobs user has already applied to (to exclude them)
            Set<Long> appliedJobIds = userApplicationHistory.stream()
                .map(AppliedJob::getJobId)
                .collect(Collectors.toSet());
            
            // Filter out applied jobs and user's own posted jobs
            List<Job> availableJobs = allJobs.stream()
                .filter(job -> !appliedJobIds.contains(job.getId()))
                .filter(job -> !job.getPostedBy().equals(user.getId()))
                .collect(Collectors.toList());
            
            System.out.println("DEBUG - Available jobs after filtering: " + availableJobs.size());
            
            // TEMPORARY: Always return fallback recommendations for debugging
            List<JobRecommendationScore> topRecommendations = getFallbackRecommendations(availableJobs, limit);
            
            // Calculate profile completeness
            double profileCompleteness = calculateProfileCompleteness(user);
            
            System.out.println("DEBUG - Returning " + topRecommendations.size() + " recommendations");
            
            return new JobRecommendationResult(
                topRecommendations,
                profileCompleteness,
                Arrays.asList("Debug mode: Showing fallback recommendations"),
                availableJobs.size()
            );
            
        } catch (Exception e) {
            System.out.println("DEBUG - Exception in recommendations: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Failed to generate job recommendations: " + e.getMessage(), e);
        }
    }
    
    /**
     * Analyze user's job application history to understand preferences
     */
    private UserJobPreferences analyzeUserJobHistory(User user, List<AppliedJob> applicationHistory) {
        UserJobPreferences preferences = new UserJobPreferences();
        
        if (applicationHistory.isEmpty()) {
            return preferences; // Return empty preferences if no history
        }
        
        Map<String, Integer> companyFrequency = new HashMap<>();
        Map<String, Integer> locationFrequency = new HashMap<>();
        List<String> historicalJobTitles = new ArrayList<>();
        List<String> historicalDescriptions = new ArrayList<>();
        List<String> approvedJobTitles = new ArrayList<>();
        
        // Analyze historical job applications
        for (AppliedJob appliedJob : applicationHistory) {
            try {
                Optional<Job> jobOpt = jobService.getJobById(appliedJob.getJobId());
                if (jobOpt.isPresent()) {
                    Job job = jobOpt.get();
                    
                    // Track company preferences
                    companyFrequency.merge(job.getCompany().toLowerCase(), 1, Integer::sum);
                    
                    // Track location preferences
                    if (job.getLocation() != null) {
                        locationFrequency.merge(job.getLocation().toLowerCase(), 1, Integer::sum);
                    }
                    
                    // Collect job titles and descriptions
                    historicalJobTitles.add(job.getTitle().toLowerCase());
                    if (job.getDescription() != null) {
                        historicalDescriptions.add(job.getDescription().toLowerCase());
                    }
                    
                    // Track approved/successful applications
                    if ("Accepted".equalsIgnoreCase(appliedJob.getStatus())) {
                        approvedJobTitles.add(job.getTitle().toLowerCase());
                        preferences.hasSuccessfulApplications = true;
                    }
                }
            } catch (Exception e) {
                // Continue if individual job lookup fails
                continue;
            }
        }
        
        // Extract preferred companies (those applied to multiple times)
        preferences.preferredCompanies = companyFrequency.entrySet().stream()
            .filter(entry -> entry.getValue() > 1)
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());
        
        // Extract preferred locations
        preferences.preferredLocations = locationFrequency.entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .limit(5)
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());
        
        // Extract common keywords from historical job titles and descriptions
        preferences.historicalKeywords = extractKeywordsFromHistory(historicalJobTitles, historicalDescriptions);
        
        // Keywords from successful applications get higher weight
        preferences.successfulJobKeywords = extractKeywordsFromHistory(approvedJobTitles, new ArrayList<>());
        
        preferences.totalApplications = applicationHistory.size();
        preferences.successfulApplications = (int) applicationHistory.stream()
            .filter(app -> "Accepted".equalsIgnoreCase(app.getStatus()))
            .count();
        
        return preferences;
    }
    
    /**
     * Extract relevant keywords from job history
     */
    private List<String> extractKeywordsFromHistory(List<String> titles, List<String> descriptions) {
        Set<String> keywords = new HashSet<>();
        
        // Common job-related keywords to prioritize
        String[] jobKeywords = {"developer", "engineer", "manager", "analyst", "designer", "consultant", 
                              "specialist", "coordinator", "director", "lead", "senior", "junior", 
                              "software", "data", "marketing", "sales", "finance", "hr", "operations"};
        
        // Extract keywords from titles
        for (String title : titles) {
            String[] words = title.split("\\s+");
            for (String word : words) {
                word = word.replaceAll("[^a-zA-Z]", "").toLowerCase();
                if (word.length() > 2 && Arrays.asList(jobKeywords).contains(word)) {
                    keywords.add(word);
                }
            }
        }
        
        // Extract keywords from descriptions (limited to avoid too many generic words)
        for (String description : descriptions) {
            for (String keyword : jobKeywords) {
                if (description.contains(keyword)) {
                    keywords.add(keyword);
                }
            }
        }
        
        return new ArrayList<>(keywords);
    }
    
    /**
     * Calculate recommendation score with historical preferences
     */
    private double calculateHistoryAwareRecommendationScore(User user, Job job, UserJobPreferences preferences) {
        double score = 0.0;
        double maxScore = 0.0;
        
        // Base profile matching (60% weight for users with little history, 40% for experienced users)
        double profileWeight = preferences.totalApplications > 5 ? 0.4 : 0.6;
        double baseScore = calculateRecommendationScore(user, job);
        score += baseScore * profileWeight;
        maxScore += profileWeight;
        
        // Historical preferences weight (increases with more application history)
        double historyWeight = Math.min(0.6, preferences.totalApplications * 0.1);
        
        if (preferences.totalApplications > 0) {
            // Historical keyword matching (30% of history weight)
            if (!preferences.historicalKeywords.isEmpty()) {
                double keywordScore = calculateHistoricalKeywordMatch(job, preferences.historicalKeywords);
                score += keywordScore * historyWeight * 0.3;
            }
            maxScore += historyWeight * 0.3;
            
            // Successful application patterns (40% of history weight - highest priority)
            if (preferences.hasSuccessfulApplications && !preferences.successfulJobKeywords.isEmpty()) {
                double successPatternScore = calculateHistoricalKeywordMatch(job, preferences.successfulJobKeywords);
                score += successPatternScore * historyWeight * 0.4;
            }
            maxScore += historyWeight * 0.4;
            
            // Company preference matching (15% of history weight)
            if (!preferences.preferredCompanies.isEmpty()) {
                double companyScore = preferences.preferredCompanies.contains(job.getCompany().toLowerCase()) ? 1.0 : 0.0;
                score += companyScore * historyWeight * 0.15;
            }
            maxScore += historyWeight * 0.15;
            
            // Location preference from history (15% of history weight)
            if (!preferences.preferredLocations.isEmpty() && job.getLocation() != null) {
                double locationHistoryScore = calculateLocationHistoryMatch(job.getLocation(), preferences.preferredLocations);
                score += locationHistoryScore * historyWeight * 0.15;
            }
            maxScore += historyWeight * 0.15;
        }
        
        // Normalize score to 0-1 range
        return maxScore > 0 ? score / maxScore : 0.0;
    }
    
    /**
     * Calculate match score based on historical keywords
     */
    private double calculateHistoricalKeywordMatch(Job job, List<String> historicalKeywords) {
        if (historicalKeywords.isEmpty()) return 0.0;
        
        String jobText = (job.getTitle() + " " + job.getDescription()).toLowerCase();
        
        int matchCount = 0;
        for (String keyword : historicalKeywords) {
            if (jobText.contains(keyword.toLowerCase())) {
                matchCount++;
            }
        }
        
        return (double) matchCount / historicalKeywords.size();
    }
    
    /**
     * Calculate location preference match from history
     */
    private double calculateLocationHistoryMatch(String jobLocation, List<String> preferredLocations) {
        if (preferredLocations.isEmpty() || jobLocation == null) return 0.0;
        
        String jobLocationLower = jobLocation.toLowerCase();
        
        for (int i = 0; i < preferredLocations.size(); i++) {
            String prefLocation = preferredLocations.get(i);
            if (jobLocationLower.contains(prefLocation) || prefLocation.contains(jobLocationLower)) {
                // Give higher score to more frequently preferred locations
                return 1.0 - (i * 0.1); // First preference gets 1.0, second gets 0.9, etc.
            }
        }
        
        return 0.0;
    }
    
    /**
     * Generate match reasons with historical context
     */
    private List<String> getHistoryAwareMatchReasons(User user, Job job, UserJobPreferences preferences) {
        List<String> reasons = new ArrayList<>();
        
        // Add base profile-based reasons
        reasons.addAll(getMatchReasons(user, job));
        
        // Add history-based reasons
        if (preferences.totalApplications > 0) {
            // Historical keyword matches
            String jobText = (job.getTitle() + " " + job.getDescription()).toLowerCase();
            for (String keyword : preferences.historicalKeywords) {
                if (jobText.contains(keyword.toLowerCase())) {
                    reasons.add("Matches your past interest in " + keyword + " roles");
                }
            }
            
            // Successful application pattern matches
            for (String keyword : preferences.successfulJobKeywords) {
                if (jobText.contains(keyword.toLowerCase())) {
                    reasons.add("Similar to your successfully accepted " + keyword + " applications");
                }
            }
            
            // Company preference
            if (preferences.preferredCompanies.contains(job.getCompany().toLowerCase())) {
                reasons.add("You've previously applied to " + job.getCompany());
            }
            
            // Location preference from history
            if (job.getLocation() != null) {
                for (String prefLocation : preferences.preferredLocations) {
                    if (job.getLocation().toLowerCase().contains(prefLocation)) {
                        reasons.add("Location matches your application history preferences");
                        break;
                    }
                }
            }
        }
        
        // Limit to most relevant reasons
        return reasons.stream().limit(5).collect(Collectors.toList());
    }
    
    /**
     * Generate insights with historical context
     */
    private List<String> generateHistoryAwareInsights(User user, List<JobRecommendationScore> recommendations, UserJobPreferences preferences) {
        List<String> insights = new ArrayList<>();
        
        if (preferences.totalApplications == 0) {
            insights.add("Start building your job history by applying to positions that match your skills");
            insights.add("As you apply to more jobs, our AI will learn your preferences for better recommendations");
        } else if (preferences.totalApplications < 5) {
            insights.add("Based on your " + preferences.totalApplications + " applications, we're learning your preferences");
            insights.add("Apply to more positions to help our AI understand your career interests better");
        } else {
            insights.add("Our AI analyzed your " + preferences.totalApplications + " job applications to personalize these recommendations");
            
            if (preferences.hasSuccessfulApplications) {
                insights.add("Prioritizing jobs similar to your " + preferences.successfulApplications + " successful applications");
            }
            
            if (!preferences.preferredLocations.isEmpty()) {
                insights.add("Focusing on your preferred locations: " + String.join(", ", preferences.preferredLocations));
            }
            
            if (!preferences.historicalKeywords.isEmpty()) {
                insights.add("Matching your interests in: " + String.join(", ", preferences.historicalKeywords));
            }
        }
        
        // Add profile-based insights
        insights.addAll(generateRecommendationInsights(user, recommendations));
        
        return insights.stream().limit(4).collect(Collectors.toList());
    }
    
    /**
     * Original calculation method (kept as fallback)
     */
    private double calculateRecommendationScore(User user, Job job) {
        double score = 0.0;
        double maxScore = 0.0;
        
        // Skills matching (40% weight) - now includes job skills field
        String jobSkillsText = job.getTitle() + " " + job.getDescription();
        if (job.getSkills() != null && !job.getSkills().trim().isEmpty()) {
            jobSkillsText += " " + job.getSkills(); // Add the dedicated skills field
        }
        double skillsScore = calculateSkillsMatch(user.getSkills(), jobSkillsText);
        score += skillsScore * 0.4;
        maxScore += 0.4;
        
        // Location preference (20% weight)
        if (user.getPreferredLocation() != null && !user.getPreferredLocation().trim().isEmpty()) {
            double locationScore = calculateLocationMatch(user.getPreferredLocation(), job.getLocation());
            score += locationScore * 0.2;
        }
        maxScore += 0.2;
        
        // Job type preference (15% weight)
        if (user.getPreferredJobType() != null && !user.getPreferredJobType().trim().isEmpty()) {
            double jobTypeScore = calculateJobTypeMatch(user.getPreferredJobType(), job.getDescription());
            score += jobTypeScore * 0.15;
        }
        maxScore += 0.15;
        
        // Experience level matching (15% weight)
        if (user.getExperience() != null && !user.getExperience().trim().isEmpty()) {
            double experienceScore = calculateExperienceMatch(user.getExperience(), job.getDescription());
            score += experienceScore * 0.15;
        }
        maxScore += 0.15;
        
        // Bio/interest matching (10% weight)
        if (user.getBio() != null && !user.getBio().trim().isEmpty()) {
            double bioScore = calculateTextSimilarity(user.getBio(), job.getDescription());
            score += bioScore * 0.1;
        }
        maxScore += 0.1;
        
        // Normalize score to 0-1 range
        return maxScore > 0 ? score / maxScore : 0.0;
    }
    
    /**
     * Calculate skills matching score - improved algorithm
     */
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
        
        // Skills match
        if (user.getSkills() != null && !user.getSkills().trim().isEmpty()) {
            String[] skills = user.getSkills().toLowerCase().split(",");
            String jobText = (job.getTitle() + " " + job.getDescription()).toLowerCase();
            
            List<String> matchedSkills = Arrays.stream(skills)
                .map(String::trim)
                .filter(skill -> !skill.isEmpty() && jobText.contains(skill))
                .collect(Collectors.toList());
            
            if (!matchedSkills.isEmpty()) {
                reasons.add("Skills match: " + String.join(", ", matchedSkills));
            }
        }
        
        // Location match
        if (user.getPreferredLocation() != null && !user.getPreferredLocation().trim().isEmpty() && job.getLocation() != null) {
            if (job.getLocation().toLowerCase().contains(user.getPreferredLocation().toLowerCase())) {
                reasons.add("Location preference: " + job.getLocation());
            }
        }
        
        // Job type match
        if (user.getPreferredJobType() != null && !user.getPreferredJobType().trim().isEmpty()) {
            if (job.getDescription().toLowerCase().contains(user.getPreferredJobType().toLowerCase())) {
                reasons.add("Job type match: " + user.getPreferredJobType());
            }
        }
        
        if (reasons.isEmpty()) {
            reasons.add("General profile compatibility");
        }
        
        return reasons;
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
            insights.add("🎯 Excellent matches found! Your profile aligns well with available opportunities.");
        } else if (avgScore > 0.5) {
            insights.add("👍 Good matches available. Consider updating your profile for even better recommendations.");
        } else {
            insights.add("💡 Basic matches found. Enhance your profile with more skills and preferences for better results.");
        }
        
        // Profile improvement suggestions
        if (user.getSkills() == null || user.getSkills().trim().isEmpty()) {
            insights.add("📋 Add your skills to get more targeted job recommendations.");
        }
        
        if (user.getPreferredLocation() == null || user.getPreferredLocation().trim().isEmpty()) {
            insights.add("📍 Set your preferred location to find jobs in your desired area.");
        }
        
        if (user.getBio() == null || user.getBio().trim().isEmpty()) {
            insights.add("✍️ Add a professional bio to improve matching accuracy.");
        }
        
        return insights;
    }
    
    /**
     * Inner class to store user's job preferences learned from history
     */
    private static class UserJobPreferences {
        List<String> preferredCompanies = new ArrayList<>();
        List<String> preferredLocations = new ArrayList<>();
        List<String> historicalKeywords = new ArrayList<>();
        List<String> successfulJobKeywords = new ArrayList<>();
        boolean hasSuccessfulApplications = false;
        int totalApplications = 0;
        int successfulApplications = 0;
    }
    
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
    private List<JobRecommendationScore> getFallbackRecommendations(List<Job> availableJobs, int limit) {
        // Sort by most recent jobs (assuming higher ID = more recent)
        return availableJobs.stream()
            .sorted(Comparator.comparingLong(Job::getId).reversed())
            .limit(limit)
            .map(job -> new JobRecommendationScore(
                job, 
                0.65, // Give a good base score for recommendations
                Arrays.asList(
                    "Recently posted opportunity",
                    "Explore this new opening",
                    "Great company with growth potential"
                )
            ))
            .collect(Collectors.toList());
    }
}