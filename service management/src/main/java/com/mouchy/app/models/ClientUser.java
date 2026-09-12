package com.mouchy.app.models;

/**
 * Subclass representing a client user.
 * Demonstrates inheritance and polymorphism.
 */
public class ClientUser extends User {

    public ClientUser(int id, String username, String password, String fullName, String email, String phoneNumber) {
        super(id, username, password, fullName, email, phoneNumber);
    }

    @Override
    public String getRole() {
        return "CLIENT";
    }
}
