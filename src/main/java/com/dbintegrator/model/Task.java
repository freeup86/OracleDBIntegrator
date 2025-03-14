package com.dbintegrator.model;

import java.util.Objects;

public class Task {
    private int id;
    private String name;
    private String description;
    private int projectId;
    private String status;
    private String assignee;
    private String priority;
    private java.util.Date startDate;
    private java.util.Date endDate;

    public Task(int id, String name, String description, int projectId,
                String status, String assignee) {
        this(id, name, description, projectId, status, assignee, null, null, null);
    }

    public Task(int id, String name, String description, int projectId,
                String status, String assignee, String priority,
                java.util.Date startDate, java.util.Date endDate) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.projectId = projectId;
        this.status = status;
        this.assignee = assignee;
        this.priority = priority;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    // Getters
    public int getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public int getProjectId() { return projectId; }
    public String getStatus() { return status; }
    public String getAssignee() { return assignee; }
    public String getPriority() { return priority; }
    public java.util.Date getStartDate() { return startDate; }
    public java.util.Date getEndDate() { return endDate; }

    // Setters
    public void setId(int id) { this.id = id; }
    public void setName(String name) { this.name = name; }
    public void setDescription(String description) { this.description = description; }
    public void setProjectId(int projectId) { this.projectId = projectId; }
    public void setStatus(String status) { this.status = status; }
    public void setAssignee(String assignee) { this.assignee = assignee; }
    public void setPriority(String priority) { this.priority = priority; }
    public void setStartDate(java.util.Date startDate) { this.startDate = startDate; }
    public void setEndDate(java.util.Date endDate) { this.endDate = endDate; }

    @Override
    public String toString() {
        return name + " (ID: " + id + ", Status: " + status + ")";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Task task = (Task) o;
        return id == task.id && projectId == task.projectId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, projectId);
    }
}