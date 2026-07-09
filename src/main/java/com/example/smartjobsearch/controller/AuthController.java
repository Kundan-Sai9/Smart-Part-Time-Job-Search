package com.example.smartjobsearch.controller;

import com.example.smartjobsearch.model.User;
import com.example.smartjobsearch.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final com.example.smartjobsearch.security.JwtService jwtService;

    @Autowired
    public AuthController(UserService userService,
                           PasswordEncoder passwordEncoder,
                           com.example.smartjobsearch.security.JwtService jwtService) {
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @PostMapping("/signup")
    public ResponseEntity<?> signup(@RequestBody SignupRequest request) {

        try {
            // Validate input
            if (request.fullName == null || request.fullName.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Full name is required"));
            }
            if (request.username == null || request.username.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Username is required"));
            }
            if (request.email == null || request.email.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Email is required"));
            }
            if (request.password == null || request.password.length() < 8) {
                return ResponseEntity.badRequest().body(Map.of("error", "Password must be at least 8 characters"));
            }
            if (!request.password.equals(request.confirmPassword)) {
                return ResponseEntity.badRequest().body(Map.of("error", "Passwords do not match"));
            }
            
            // Check if user already exists
            if (userService.findByUsername(request.username).isPresent()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Username already exists"));
            }
            if (userService.findByEmail(request.email).isPresent()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Email already exists"));
            }
            
            String encodedPassword = passwordEncoder.encode(request.password);
            User user = new User(request.fullName, request.username, request.email, encodedPassword);
            User savedUser = userService.saveUser(user);
            
            String token = jwtService.generateAccessToken(savedUser.getId(), savedUser.getUsername());

            return ResponseEntity.ok().body(Map.of(
                "message", "User registered successfully",
                "user_id", savedUser.getId(),
                "access_token", token,
                "token_type", "Bearer"
            ));
            
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Registration failed: " + e.getMessage()));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        try {
            if (request.userInput == null || request.userInput.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Username/Email is required"));
            }
            if (request.password == null || request.password.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Password is required"));
            }
            
            // Find user by username or email
            Optional<User> userOpt = userService.findByUsername(request.userInput);
            if (userOpt.isEmpty()) {
                userOpt = userService.findByEmail(request.userInput);
            }
            
            if (userOpt.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "User not found"));
            }
            
            User user = userOpt.get();
            
            // Support both hashed and legacy plaintext passwords.
            boolean passwordOk = false;
            try {
                passwordOk = passwordEncoder.matches(request.password, user.getPassword());
            } catch (Exception ignored) {
                passwordOk = false;
            }
            if (!passwordOk && user.getPassword() != null && user.getPassword().equals(request.password)) {
                passwordOk = true;
            }

            if (!passwordOk) {
                return ResponseEntity.badRequest().body(Map.of("error", "Invalid password"));
            }
            
            String token = jwtService.generateAccessToken(user.getId(), user.getUsername());

            return ResponseEntity.ok().body(Map.of(
                "message", "Login successful",
                "user_id", user.getId(),
                "full_name", user.getFullName(),
                "username", user.getUsername(),
                "email", user.getEmail(),
                "access_token", token,
                "token_type", "Bearer"
            ));
            
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Login failed: " + e.getMessage()));
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout() {
        return ResponseEntity.ok().body(Map.of("message", "Logout successful"));
    }

    public static class SignupRequest {
        public String fullName,username,email, password, confirmPassword;
    }
    
    public static class LoginRequest {
        public String userInput,password;
    }
}
