package com.oj.rc.repository;

import com.oj.rc.entity.Submission;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubmissionRepository extends JpaRepository<Submission, Integer> {

    // Newest submission first, like the Node "gethistory" endpoint
    List<Submission> findByTeamIdOrderBySubmittedAtDesc(String teamId);
}
