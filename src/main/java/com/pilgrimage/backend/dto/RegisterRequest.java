package com.pilgrimage.backend.dto;

public class RegisterRequest {
    private String fullName;
    private String email;
    private String password;
    // NOTE: no 'role' field - self-registration must never be able to set a role.
    
    public String getFullName() {
        return fullName;
    }
    
    public void setFullName(String fullName) {
        this.fullName = fullName;
    }
    
    public String getEmail() {
        return email;
    }
    
    public void setEmail(String email) {
        this.email = email;
    }
    
    public String getPassword() {
        return password;
    }
    
    public void setPassword(String password) {
        this.password = password;
    }
}
