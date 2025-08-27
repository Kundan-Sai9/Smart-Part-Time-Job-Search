package com.example.smartjobsearch.repo;

import com.example.smartjobsearch.model.AppliedJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface AppliedJobRepository extends JpaRepository<AppliedJob, Long> {
    List<AppliedJob> findByUserId(Long userId);
    List<AppliedJob> findByJobId(Long jobId);
}
