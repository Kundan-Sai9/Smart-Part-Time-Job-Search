
package com.example.smartjobsearch.controller;
import com.example.smartjobsearch.model.AppliedJob;
import com.example.smartjobsearch.repo.AppliedJobRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.stereotype.Controller;

@Controller
public class FileController {

    @Autowired
    private AppliedJobRepository appliedJobRepository;

    // Endpoint to download/view resume by applicationId
    @GetMapping("/api/files/resume/{applicationId}")
    @ResponseBody
    public ResponseEntity<?> downloadResume(@PathVariable Long applicationId) throws IOException {
        AppliedJob app = appliedJobRepository.findById(applicationId).orElse(null);
        if (app == null || app.getResumePath() == null) {
            return ResponseEntity.notFound().build();
        }
        File file = new File(app.getResumePath());
        if (!file.exists()) {
            return ResponseEntity.notFound().build();
        }
        String fileName = file.getName();
        String contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
        if (fileName.endsWith(".pdf")) contentType = "application/pdf";
        else if (fileName.endsWith(".doc")) contentType = "application/msword";
        else if (fileName.endsWith(".docx")) contentType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        InputStreamResource resource = new InputStreamResource(new FileInputStream(file));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .contentType(MediaType.parseMediaType(contentType))
                .contentLength(file.length())
                .body(resource);
    }

    @GetMapping("/login")
    public String login() {
        return "redirect:/login.html";
    }

    @GetMapping("/dashboard")
    public String dashboard() {
        return "redirect:/dashboard.html";
    }

    @GetMapping("/account")
    public String account() {
        return "redirect:/account.html";
    }

    @GetMapping("/jobpost")
    public String jobPost() {
        return "redirect:/jobpost.html";
    }

    @GetMapping("/appliedjobs")
    public String appliedJobs() {
        return "redirect:/Appliedjobs.html";
    }

    @GetMapping("/all_applications")
    public String allApplications() {
        return "redirect:/all_applications.html";
    }

    @GetMapping("/home")
    public String homePage() {
        return "redirect:/home.html";
    }
}
