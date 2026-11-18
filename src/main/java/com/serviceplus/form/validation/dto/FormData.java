package com.serviceplus.form.validation.dto;

import java.util.Map;

public class FormData {

    private Map<String, Task> tasks;

    public Map<String, Task> getTasks() {
        return tasks;
    }

    public void setTasks(Map<String, Task> tasks) {
        this.tasks = tasks;
    }

    public static class Task {
        private Map<String, Form> forms;

        public Map<String, Form> getForms() {
            return forms;
        }

        public void setForms(Map<String, Form> forms) {
            this.forms = forms;
        }
    }

    public static class Form {
        private Map<String, Attribute> attributes;

        public Map<String, Attribute> getAttributes() {
            return attributes;
        }

        public void setAttributes(Map<String, Attribute> attributes) {
            this.attributes = attributes;
        }
    }

    public static class Attribute {
        private Map<String,Object> versions;
        private Integer current;

        public Map<String,Object> getVersions() {
            return versions;
        }

        public void setVersions(Map<String,Object> versions) {
            this.versions = versions;
        }

        public Integer getCurrent() {
            return current;
        }

        public void setCurrent(Integer current) {
            this.current = current;
        }
    }

}
