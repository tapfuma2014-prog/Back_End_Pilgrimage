package com.pilgrimage.backend.dto;

public class JwtResponse {
    private String token;
    private String type = "Bearer";
    private String id;
    private String fullName;
    private String email;
    private String role;
    
    public JwtResponse(String token, String id, String fullName, String email, String role) {
        this.token = token;
        this.id = id;
        this.fullName = fullName;
        this.email = email;
        this.role = role;
    }
    
    // Getters and Setters
    public String getToken() {
        return token;
    }
    
    public String getType() {
        return type;
    }
    
    public String getId() {
        return id;
    }
    
    public String getFullName() {
        return fullName;
    }
    
    public String getEmail() {
        return email;
    }
    
    public String getRole() {
        return role;
    }
}
