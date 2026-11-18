package com.serviceplus.form.validation.dto;

import java.util.ArrayList;
import java.util.List;

public class NotificationDetails {

    private Long notificationId;
    private String taskId;
    private String triggerPointId;
    private List<String> requiredPatterns = new ArrayList<>();

    public NotificationDetails() {
    }

    public NotificationDetails(Long notificationId, String taskId, String triggerPointId, List<String> requiredPatterns) {
        this.notificationId = notificationId;
        this.taskId = taskId;
        this.triggerPointId = triggerPointId;
        this.requiredPatterns = requiredPatterns != null ? requiredPatterns : new ArrayList<>();
    }

    public Long getNotificationId() {
        return notificationId;
    }

    public void setNotificationId(Long notificationId) {
        this.notificationId = notificationId;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getTriggerPointId() {
        return triggerPointId;
    }

    public void setTriggerPointId(String triggerPointId) {
        this.triggerPointId = triggerPointId;
    }

    public List<String> getRequiredPatterns() {
        return requiredPatterns;
    }

    public void setRequiredPatterns(List<String> requiredPatterns) {
        this.requiredPatterns = requiredPatterns != null ? requiredPatterns : new ArrayList<>();
    }
}
