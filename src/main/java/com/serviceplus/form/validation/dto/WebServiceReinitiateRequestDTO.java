package com.serviceplus.form.validation.dto;

import java.util.List;

public class WebServiceReinitiateRequestDTO {

	private Integer serviceId;

    private String taskId;
    /**
     * Type 1: Re-execute those applications which are failed and does not have any attempts left (exhausted).
     * Type 2: Re-execute only those applications which have attempts left.
     */
    private Integer type;

    private List<String> applicationId;

    public WebServiceReinitiateRequestDTO() {
    }

	public Integer getType() {
		return type;
	}

	public void setType(Integer type) {
		this.type = type;
	}

	public List<String> getApplicationId() {
		return applicationId;
	}

	public void setApplicationId(List<String> applicationId) {
		this.applicationId = applicationId;
	}

	public Integer getServiceId() {
		return serviceId;
	}

	public void setServiceId(Integer serviceId) {
		this.serviceId = serviceId;
	}

	public String getTaskId() {
		return taskId;
	}

	public void setTaskId(String taskId) {
		this.taskId = taskId;
	}

    
}
