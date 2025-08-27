package com.example.smartjobsearch.service;

import com.example.smartjobsearch.model.Job;
import com.example.smartjobsearch.repo.JobRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class JobService {

    @Autowired
    private JobRepository jobRepository;

    public List<Job> getAllJobs() {
        List<Job> jobs = jobRepository.findAll();
        System.out.println("DEBUG JobService - getAllJobs() returned " + jobs.size() + " jobs");
        for (Job job : jobs) {
            System.out.println("DEBUG JobService - Job found: ID=" + job.getId() + ", Title=" + job.getTitle() + ", Company=" + job.getCompany());
        }
        return jobs;
    }

    public Optional<Job> getJobById(Long id) {
        return jobRepository.findById(id);
    }

    public Job saveJob(Job job) {
        return jobRepository.save(job);
    }

    public void deleteJob(Long id) {
        jobRepository.deleteById(id);
    }

    public List<Job> searchJobs(String search) {
        // Simple in-memory search for demonstration; replace with custom query for production
        String lower = search.toLowerCase();
        return jobRepository.findAll().stream()
                .filter(j -> (j.getTitle() != null && j.getTitle().toLowerCase().contains(lower)) ||
                             (j.getDescription() != null && j.getDescription().toLowerCase().contains(lower)))
                .collect(Collectors.toList());
    }

    public List<Job> getJobsByUser(Long userId) {
        return jobRepository.findByPostedBy(userId);
    }
}
