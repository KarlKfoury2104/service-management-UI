package com.mouchy.app.models;

/**
 * Model representing a booking or request for a service made by a client.
 */
public class ServiceRequest {
    private int id;
    private int clientId;
    private String clientName; // Joined for convenient UI binding
    private int serviceId;
    private String serviceName; // Joined for convenient UI binding
    private String title;
    private String description;
    private String status; // "PENDING", "IN_PROGRESS", "COMPLETED", "REJECTED"
    private double price;
    private String preferredDate;
    private String createdAt;
    private String comments;

    public ServiceRequest(int id, int clientId, String clientName, int serviceId, String serviceName,
                          String title, String description, String status, double price,
                          String preferredDate, String createdAt, String comments) {
        this.id = id;
        this.clientId = clientId;
        this.clientName = clientName;
        this.serviceId = serviceId;
        this.serviceName = serviceName;
        this.title = title;
        this.description = description;
        this.status = status;
        this.price = price;
        this.preferredDate = preferredDate;
        this.createdAt = createdAt;
        this.comments = comments;
    }

    // Getters and Setters
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getClientId() {
        return clientId;
    }

    public void setClientId(int clientId) {
        this.clientId = clientId;
    }

    public String getClientName() {
        return clientName;
    }

    public void setClientName(String clientName) {
        this.clientName = clientName;
    }

    public int getServiceId() {
        return serviceId;
    }

    public void setServiceId(int serviceId) {
        this.serviceId = serviceId;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    public String getPreferredDate() {
        return preferredDate;
    }

    public void setPreferredDate(String preferredDate) {
        this.preferredDate = preferredDate;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getComments() {
        return comments;
    }

    public void setComments(String comments) {
        this.comments = comments;
    }
}
