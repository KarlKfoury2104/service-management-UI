package com.mouchy.app.models;

/**
 * Abstract class representing a user in the system.
 * Demonstrates the concept of abstraction and inheritance.
 */
public abstract class User {
    private int id;
    private String username;
    private String password;
    private String fullName;
    private String email;
    private String phoneNumber;

    public User(int id, String username, String password, String fullName, String email, String phoneNumber) {
        this.id = id;
        this.username = username;
        this.password = password;
        this.fullName = fullName;
        this.email = email;
        this.phoneNumber = phoneNumber;
    }

    // Getters and Setters
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

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

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    /**
     * Abstract method that must be implemented by subclasses to return the role of the user.
     * Demonstrates polymorphism.
     */
    public abstract String getRole();
}
