package com.mouchy.app.models;

/**
 * Subclass representing an administrator user.
 * Demonstrates inheritance and polymorphism.
 */
public class AdminUser extends User {

    public AdminUser(int id, String username, String password, String fullName, String email, String phoneNumber) {
        super(id, username, password, fullName, email, phoneNumber);
    }

    @Override
    public String getRole() {
        return "ADMIN";
    }
}
