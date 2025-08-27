// Update frontend to use new Spring Boot REST API endpoints
// This script will be used to replace Servlet calls with proper REST API calls

// Base API URL
const API_BASE = '/api';

// Authentication endpoints
const AUTH_API = {
    signup: `${API_BASE}/auth/signup`,
    login: `${API_BASE}/auth/login`,
    logout: `${API_BASE}/auth/logout`,
    userInfo: `${API_BASE}/auth/user-info`,
    updateProfile: `${API_BASE}/auth/update-profile`,
    applyJob: `${API_BASE}/auth/apply-job`,
    appliedJobs: `${API_BASE}/auth/applied-jobs`,
    dashboard: `${API_BASE}/auth/dashboard`,
    getAllPostedApplications: `${API_BASE}/auth/get-all-posted-applications`,
    approveApplication: `${API_BASE}/auth/approve-application`,
    deleteApplication: (id) => `${API_BASE}/auth/delete-application/${id}`,
    approvedJobs: `${API_BASE}/auth/approved-jobs`
};

// Job endpoints
const JOB_API = {
    getAllJobs: `${API_BASE}/jobs`,
    getJobById: (id) => `${API_BASE}/jobs/${id}`,
    searchJobs: (query) => `${API_BASE}/jobs/search?query=${encodeURIComponent(query)}`,
    postJob: `${API_BASE}/jobs/post`,
    getJobsByUser: (userId) => `${API_BASE}/jobs/user/${userId}`,
    updateJob: `${API_BASE}/jobs`,
    deleteJob: (id) => `${API_BASE}/jobs/${id}`
};

// Applied jobs endpoints
const APPLIED_JOBS_API = {
    viewApplications: (jobId) => `${API_BASE}/applied-jobs/view-applications/${jobId}`
};

// AI and Profile endpoints
const AI_API = {
    profileScore: (userId) => `${API_BASE}/profile/score?userId=${userId}`,
    jobRecommendations: (userId) => `${API_BASE}/jobs/ai/recommendations?userId=${userId}`,
    profileAnalysis: (userId) => `${API_BASE}/profile/analyze?userId=${userId}`
};

// Common utility functions
const ApiUtils = {
    // Make authenticated API call
    makeAuthenticatedCall: async (url, options = {}) => {
        const defaultOptions = {
            credentials: 'include',
            headers: {
                'Content-Type': 'application/json',
                ...options.headers
            }
        };
        
        const mergedOptions = { ...defaultOptions, ...options };
        
        try {
            const response = await fetch(url, mergedOptions);
            return await response.json();
        } catch (error) {
            console.error('API call failed:', error);
            throw error;
        }
    },
    
    // Make form data call
    makeFormCall: async (url, formData, options = {}) => {
        const defaultOptions = {
            method: 'POST',
            credentials: 'include',
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded',
                ...options.headers
            },
            body: formData
        };
        
        const mergedOptions = { ...defaultOptions, ...options };
        
        try {
            const response = await fetch(url, mergedOptions);
            return await response.json();
        } catch (error) {
            console.error('API call failed:', error);
            throw error;
        }
    },

    // AI-specific utility functions
    AI: {
        // Get profile score using Cohere AI
        getProfileScore: async (user) => {
            try {
                // Handle both user object and direct user ID
                const userId = typeof user === 'object' ? (user.id || user.user_id) : user;
                
                if (!userId) {
                    throw new Error('User ID is required for profile score');
                }
                
                const response = await fetch(AI_API.profileScore(userId), {
                    method: 'GET',
                    credentials: 'include',
                    headers: {
                        'Content-Type': 'application/json'
                    }
                });
                
                if (!response.ok) {
                    throw new Error(`HTTP error! status: ${response.status}`);
                }
                
                return await response.json();
            } catch (error) {
                console.error('Failed to get profile score:', error);
                throw error;
            }
        },

        // Get job recommendations using AI
        getJobRecommendations: async (user) => {
            try {
                // Handle both user object and direct user ID
                const userId = typeof user === 'object' ? (user.id || user.user_id) : user;
                
                if (!userId) {
                    throw new Error('User ID is required for recommendations');
                }
                
                const response = await fetch(AI_API.jobRecommendations(userId), {
                    method: 'GET',
                    credentials: 'include',
                    headers: {
                        'Content-Type': 'application/json',
                        'Authorization': `Bearer ${(typeof user === 'object' ? user.token : '') || ''}`
                    }
                });
                
                if (!response.ok) {
                    const errorText = await response.text();
                    throw new Error(`AI recommendations failed: ${response.status} - ${errorText}`);
                }
                
                return await response.json();
            } catch (error) {
                console.error('Failed to get job recommendations:', error);
                throw error;
            }
        }
    }
};
