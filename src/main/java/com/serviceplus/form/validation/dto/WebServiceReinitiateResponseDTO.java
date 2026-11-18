package com.serviceplus.form.validation.dto;

public class WebServiceReinitiateResponseDTO {

    private String message;
    private int totalProcessed;
    private int type;

    public WebServiceReinitiateResponseDTO() {
    }

	public WebServiceReinitiateResponseDTO(String message, int totalProcessed, int type) {
		super();
		this.message = message;
		this.totalProcessed = totalProcessed;
		this.type = type;
	}

	public String getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message;
	}

	public int getTotalProcessed() {
		return totalProcessed;
	}

	public void setTotalProcessed(int totalProcessed) {
		this.totalProcessed = totalProcessed;
	}

	public int getType() {
		return type;
	}

	public void setType(int type) {
		this.type = type;
	}

    
}
