package com.example.smartjobsearch.service;


import com.example.smartjobsearch.model.AppliedJob;
import com.example.smartjobsearch.repo.AppliedJobRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class AppliedJobService {
    private final AppliedJobRepository appliedJobRepository;

    @Autowired
    public AppliedJobService(AppliedJobRepository appliedJobRepository) {
        this.appliedJobRepository = appliedJobRepository;
    }

    public List<AppliedJob> findByUserId(Long userId) {
        return appliedJobRepository.findByUserId(userId);
    }

    public List<AppliedJob> findByJobId(Long jobId) {
        return appliedJobRepository.findByJobId(jobId);
    }

    public AppliedJob save(AppliedJob appliedJob) {
        return appliedJobRepository.save(appliedJob);
    }

    public Optional<AppliedJob> findById(Long id) {
        return appliedJobRepository.findById(id);
    }

    public void deleteById(Long id) {
        appliedJobRepository.deleteById(id);
    }
}

