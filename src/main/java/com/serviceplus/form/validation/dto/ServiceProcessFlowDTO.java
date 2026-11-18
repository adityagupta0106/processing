package com.serviceplus.form.validation.dto;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.serviceplus.form.validation.dto.OfficeDetailsDTO.OfficeUnitData;
import com.serviceplus.form.validation.dto.WebServiceDetails.FormDetail;

public class ServiceProcessFlowDTO {
	private Integer serviceId;
	private List<Data> data;
	private List<AssociatedActivity> associatedActivities;
	private Map<String, TaskRelationDTO> taskRelation;

	public static class TaskRelationDTO {

		private List<String> previousTask = new ArrayList<>();
		private List<String> nextTask = new ArrayList<>();

		public List<String> getPreviousTask() {
			return previousTask;
		}

		public void setPreviousTask(List<String> previousTask) {
			this.previousTask = previousTask;
		}

		public List<String> getNextTask() {
			return nextTask;
		}

		public void setNextTask(List<String> nextTask) {
			this.nextTask = nextTask;
		}
	}

	public static class Data {

		private Nodes node;
		private List<MappedTask> mappedTasks;
		private List<OfficeUnitData> allowedOffices;
		private WorkflowElementData workflowElementData;
		private PaymentDetailsDTO paymentDetails;

		public static class WorkflowElementData {

			private List<ActionAttribute> actionAttribute;

			private TaskAttribute taskAttribute;

			private UserAttribute userAttribute;

			private Boolean priorityEnabled;

			private Boolean bulkEnabled;

			private Boolean isGlobal;

			@JsonProperty("editPermission")
			@JsonInclude(JsonInclude.Include.NON_EMPTY)
			private List<EditPermissionData> editPermission;

			@JsonProperty("fallBackEditPermission")
			@JsonInclude(JsonInclude.Include.NON_EMPTY)
			private Map<String, Object> fallBackEditPermission;

			public Boolean getIsGlobal() {
				return isGlobal;
			}

			public void setIsGlobal(Boolean isGlobal) {
				this.isGlobal = isGlobal;
			}

			public List<EditPermissionData> getEditPermission() {
				return editPermission;
			}

			public void setEditPermission(List<EditPermissionData> editPermission) {
				this.editPermission = editPermission;
			}

			public Map<String, Object> getFallBackEditPermission() {
				return fallBackEditPermission;
			}

			public void setFallBackEditPermission(Map<String, Object> fallBackEditPermission) {
				this.fallBackEditPermission = fallBackEditPermission;
			}

			public List<ActionAttribute> getActionAttribute() {
				return actionAttribute;
			}

			public void setActionAttribute(List<ActionAttribute> actionAttribute) {
				this.actionAttribute = actionAttribute;
			}

			public TaskAttribute getTaskAttribute() {
				return taskAttribute;
			}

			public void setTaskAttribute(TaskAttribute taskAttribute) {
				this.taskAttribute = taskAttribute;
			}

			public UserAttribute getUserAttribute() {
				return userAttribute;
			}

			public void setUserAttribute(UserAttribute userAttribute) {
				this.userAttribute = userAttribute;
			}

			public Boolean getPriorityEnabled() {
				return priorityEnabled;
			}

			public void setPriorityEnabled(Boolean priorityEnabled) {
				this.priorityEnabled = priorityEnabled;
			}

			public Boolean getBulkEnabled() {
				return bulkEnabled;
			}

			public void setBulkEnabled(Boolean bulkEnabled) {
				this.bulkEnabled = bulkEnabled;
			}

		}

		public static class ActionAttribute {

			private String key;
			private String label;
			private String trackLabel;
			private Boolean logicalClosure;
			private Boolean completeClosure;

			public String getKey() {
				return key;
			}

			public void setKey(String key) {
				this.key = key;
			}

			public String getLabel() {
				return label;
			}

			public void setLabel(String label) {
				this.label = label;
			}

			public String getTrackLabel() {
				return trackLabel;
			}

			public void setTrackLabel(String trackLabel) {
				this.trackLabel = trackLabel;
			}

			public Boolean getLogicalClosure() {
				return logicalClosure;
			}

			public void setLogicalClosure(Boolean logicalClosure) {
				this.logicalClosure = logicalClosure;
			}

			public Boolean getCompleteClosure() {
				return completeClosure;
			}

			public void setCompleteClosure(Boolean completeClosure) {
				this.completeClosure = completeClosure;
			}
		}

		public static class TaskAttribute {
			private String selectionType;
			private String gatewayType;
			private List<TaskNode> taskNodes;

			public String getSelectionType() {
				return selectionType;
			}

			public void setSelectionType(String selectionType) {
				this.selectionType = selectionType;
			}

			public List<TaskNode> getTaskNodes() {
				return taskNodes;
			}

			public void setTaskNodes(List<TaskNode> taskNodes) {
				this.taskNodes = taskNodes;
			}

			public String getGatewayType() {
				return gatewayType;
			}

			public void setGatewayType(String gatewayType) {
				this.gatewayType = gatewayType;
			}
		}

		public static class TaskNode {

			private String taskId;

			private String taskName;
			private Boolean showSelection;

			public String getTaskId() {
				return taskId;
			}

			public void setTaskId(String taskId) {
				this.taskId = taskId;
			}

			public String getTaskName() {
				return taskName;
			}

			public void setTaskName(String taskName) {
				this.taskName = taskName;
			}

			public Boolean getShowSelection() {
				return showSelection;
			}

			public void setShowSelection(Boolean showSelection) {
				this.showSelection = showSelection;
			}
		}

		public static class UserAttribute {

			private String selectionType;

			private List<UserNode> userNodes;

			public String getSelectionType() {
				return selectionType;
			}

			public void setSelectionType(String selectionType) {
				this.selectionType = selectionType;
			}

			public List<UserNode> getUserNodes() {
				return userNodes;
			}

			public void setUserNodes(List<UserNode> userNodes) {
				this.userNodes = userNodes;
			}
		}

		public static class EditPermissionData {

			private String selectedHolderId;
			private String selectedFormId;
			private String label;
			private Specific specific;
			private Integer sortOrder;
			private Integer modifyChoice;
			private String placement;
			
			public static class Specific {
				@JsonSerialize(contentUsing = LabelValueSerializer.class)
				@JsonDeserialize(contentUsing = LabelValueStringDeserializer.class)
				private List<LabelValue> allowed = new ArrayList<>();

				@JsonSerialize(contentUsing = LabelValueSerializer.class)
				@JsonDeserialize(contentUsing = LabelValueStringDeserializer.class)
				private List<LabelValue> restricted = new ArrayList<>();

				@JsonSerialize(contentUsing = LabelValueSerializer.class)
				@JsonDeserialize(contentUsing = LabelValueStringDeserializer.class)
				private List<LabelValue> hide = new ArrayList<>();

				public static class LabelValueSerializer extends JsonSerializer<LabelValue> {
					@Override
					public void serialize(LabelValue value, JsonGenerator generator, SerializerProvider serializers)
							throws IOException {
						if (value == null) {
							generator.writeNull();
							return;
						}
						generator.writeStartObject();
						String label = value.getLabel();
						Object val = value.getValue();
						if (label == null && val != null) {
							label = val.toString();
						}
						if (val == null && label != null) {
							val = label;
						}
						generator.writeStringField("label", label != null ? label : "");
						generator.writeObjectField("value", val != null ? val : "");
						generator.writeEndObject();
					}
				}

				public static class LabelValueStringDeserializer extends JsonDeserializer<LabelValue> {
					@Override
					public LabelValue deserialize(JsonParser parser, DeserializationContext context)
							throws IOException {
						JsonNode node = parser.getCodec().readTree(parser);
						LabelValue result = new LabelValue();
						if (node == null || node.isNull()) {
							return result;
						}
						if (node.isTextual() || node.isNumber() || node.isBoolean()) {
							String value = node.asText();
							result.setLabel(value);
							result.setValue(value);
							return result;
						}
						if (node.isObject()) {
							JsonNode labelNode = node.get("label");
							JsonNode valueNode = node.get("value");
							String label = labelNode == null || labelNode.isNull() ? null : labelNode.asText();
							Object value = valueNode == null || valueNode.isNull() ? null
									: parser.getCodec().treeToValue(valueNode, Object.class);
							if (label == null && value != null) {
								label = value.toString();
							}
							if (value == null && label != null) {
								value = label;
							}
							result.setLabel(label != null ? label : "");
							result.setValue(value != null ? value : "");
							return result;
						}
						throw context.weirdStringException(node.toString(), LabelValue.class,
								"Expected a string ID or a LabelValue object");
					}
				}

				public List<LabelValue> getAllowed() {
					return allowed;
				}

				public void setAllowed(List<LabelValue> allowed) {
					this.allowed = allowed;
				}

				public List<LabelValue> getRestricted() {
					return restricted;
				}

				public void setRestricted(List<LabelValue> restricted) {
					this.restricted = restricted;
				}

				public List<LabelValue> getHide() {
					return hide;
				}

				public void setHide(List<LabelValue> hide) {
					this.hide = hide;
				}
			}

			public String getSelectedHolderId() {
				return selectedHolderId;
			}

			public void setSelectedHolderId(String selectedHolderId) {
				this.selectedHolderId = selectedHolderId;
			}

			public String getSelectedFormId() {
				return selectedFormId;
			}

			public void setSelectedFormId(String selectedFormId) {
				this.selectedFormId = selectedFormId;
			}

			public String getLabel() {
				return label;
			}

			public void setLabel(String label) {
				this.label = label;
			}

			public Specific getSpecific() {
				return specific;
			}

			public void setSpecific(Specific specific) {
				this.specific = specific;
			}

			public Integer getSortOrder() {
				return sortOrder;
			}

			public void setSortOrder(Integer sortOrder) {
				this.sortOrder = sortOrder;
			}

			public Integer getModifyChoice() {
				return modifyChoice;
			}

			public void setModifyChoice(Integer modifyChoice) {
				this.modifyChoice = modifyChoice;
			}

			public String getPlacement() {
				return placement;
			}

			public void setPlacement(String placement) {
				this.placement = placement;
			}
			
		}

		public static class UserNode {

			private String taskId;

			private Long locationId;

			private String locationName;

			private Boolean showSelection;

			private String holderId;

			private String holderName;

			public String getTaskId() {
				return taskId;
			}

			public void setTaskId(String taskId) {
				this.taskId = taskId;
			}

			public Long getLocationId() {
				return locationId;
			}

			public void setLocationId(Long locationId) {
				this.locationId = locationId;
			}

			public String getLocationName() {
				return locationName;
			}

			public void setLocationName(String locationName) {
				this.locationName = locationName;
			}

			public Boolean getShowSelection() {
				return showSelection;
			}

			public void setShowSelection(Boolean showSelection) {
				this.showSelection = showSelection;
			}

			public String getHolderId() {
				return holderId;
			}

			public void setHolderId(String holderId) {
				this.holderId = holderId;
			}

			public String getHolderName() {
				return holderName;
			}

			public void setHolderName(String holderName) {
				this.holderName = holderName;
			}
		}

		public static class MappedTask {
			private Nodes node;

			public Nodes getNode() {
				return node;
			}

			public void setNode(Nodes node) {
				this.node = node;
			}

		}

		public static class Nodes {
			private String id;
			private String type;
			private Integer taskType;
			private String name;
			private String behaviour;
			private String formId;
			private Applicant applicant;
			private TaskPaymentMappingDTO payment;
			private List<LabelValue> kioskType;
			private List<Integer> identifiedUserSetIds;
			private Boolean applicantRegistrationRequired;
			private Boolean displayPreviewOfFilledForm;

			public Boolean getDisplayPreviewOfFilledForm() {
				return displayPreviewOfFilledForm;
			}

			public void setDisplayPreviewOfFilledForm(Boolean displayPreviewOfFilledForm) {
				this.displayPreviewOfFilledForm = displayPreviewOfFilledForm;
			}

			public Boolean getApplicantRegistrationRequired() {
				return applicantRegistrationRequired;
			}

			public void setApplicantRegistrationRequired(Boolean applicantRegistrationRequired) {
				this.applicantRegistrationRequired = applicantRegistrationRequired;
			}

			public String getId() {
				return id;
			}

			public void setId(String id) {
				this.id = id;
			}

			public String getType() {
				return type;
			}

			public void setType(String type) {
				this.type = type;
			}

			public Integer getTaskType() {
				return taskType;
			}

			public void setTaskType(Integer taskType) {
				this.taskType = taskType;
			}

			public String getName() {
				return name;
			}

			public void setName(String name) {
				this.name = name;
			}

			public String getBehaviour() {
				return behaviour;
			}

			public void setBehaviour(String behaviour) {
				this.behaviour = behaviour;
			}

			public String getFormId() {
				return formId;
			}

			public void setFormId(String formId) {
				this.formId = formId;
			}

			public Applicant getApplicant() {
				return applicant;
			}

			public void setApplicant(Applicant applicant) {
				this.applicant = applicant;
			}

			public TaskPaymentMappingDTO getPayment() {
				return payment;
			}

			public void setPayment(TaskPaymentMappingDTO payment) {
				this.payment = payment;
			}

			public List<LabelValue> getKioskType() {
				return kioskType;
			}

			public void setKioskType(List<LabelValue> kioskType) {
				this.kioskType = kioskType;
			}

			public List<Integer> getIdentifiedUserSetIds() {
				return identifiedUserSetIds;
			}

			public void setIdentifiedUserSetIds(List<Integer> identifiedUserSetIds) {
				this.identifiedUserSetIds = identifiedUserSetIds;
			}
		}

		public static class Applicant {
			private boolean deoSubmit;
			private boolean uploadRejectedEnclosures;
			private boolean submissionToSameOfficial;

			public boolean isDeoSubmit() {
				return deoSubmit;
			}

			public void setDeoSubmit(boolean deoSubmit) {
				this.deoSubmit = deoSubmit;
			}

			public boolean isUploadRejectedEnclosures() {
				return uploadRejectedEnclosures;
			}

			public void setUploadRejectedEnclosures(boolean uploadRejectedEnclosures) {
				this.uploadRejectedEnclosures = uploadRejectedEnclosures;
			}

			public boolean isSubmissionToSameOfficial() {
				return submissionToSameOfficial;
			}

			public void setSubmissionToSameOfficial(boolean submissionToSameOfficial) {
				this.submissionToSameOfficial = submissionToSameOfficial;
			}
		}

		public Nodes getNode() {
			return node;
		}

		public void setNode(Nodes node) {
			this.node = node;
		}

		public List<MappedTask> getMappedTasks() {
			return mappedTasks;
		}

		public void setMappedTasks(List<MappedTask> mappedTasks) {
			this.mappedTasks = mappedTasks;
		}

		public List<OfficeUnitData> getAllowedOffices() {
			return allowedOffices;
		}

		public void setAllowedOffices(List<OfficeUnitData> allowedOffices) {
			this.allowedOffices = allowedOffices;
		}

		public WorkflowElementData getWorkflowElementData() {
			return workflowElementData;
		}

		public void setWorkflowElementData(WorkflowElementData workflowElementData) {
			this.workflowElementData = workflowElementData;
		}

		public PaymentDetailsDTO getPaymentDetails() {
			return paymentDetails;
		}

		public void setPaymentDetails(PaymentDetailsDTO paymentDetails) {
			this.paymentDetails = paymentDetails;
		}
	}

	public static class AssociatedActivity {

		private String id;

		private String name;

		private String type;

		private String sourceTaskId;

		private String edgeId;

		private List<String> triggerPoint;

		private List<String> triggerOnAction;

		private List<String> executionTasks;

		private FormDetail formDetail;

		private OfficialIntimation officialIntimation;

		private WebServiceDetails webServiceDetails;

		public String getId() {
			return id;
		}

		public void setId(String id) {
			this.id = id;
		}

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public String getType() {
			return type;
		}

		public void setType(String type) {
			this.type = type;
		}

		public String getSourceTaskId() {
			return sourceTaskId;
		}

		public void setSourceTaskId(String sourceTaskId) {
			this.sourceTaskId = sourceTaskId;
		}

		public String getEdgeId() {
			return edgeId;
		}

		public void setEdgeId(String edgeId) {
			this.edgeId = edgeId;
		}

		public List<String> getTriggerPoint() {
			return triggerPoint;
		}

		public void setTriggerPoint(List<String> triggerPoint) {
			this.triggerPoint = triggerPoint;
		}

		public List<String> getTriggerOnAction() {
			return triggerOnAction;
		}

		public void setTriggerOnAction(List<String> triggerOnAction) {
			this.triggerOnAction = triggerOnAction;
		}

		public List<String> getExecutionTasks() {
			return executionTasks;
		}

		public void setExecutionTasks(List<String> executionTasks) {
			this.executionTasks = executionTasks;
		}

		public FormDetail getFormDetail() {
			return formDetail;
		}

		public void setFormDetail(FormDetail formDetail) {
			this.formDetail = formDetail;
		}

		public OfficialIntimation getOfficialIntimation() {
			return officialIntimation;
		}

		public void setOfficialIntimation(OfficialIntimation officialIntimation) {
			this.officialIntimation = officialIntimation;
		}

		public WebServiceDetails getWebServiceDetails() {
			return webServiceDetails;
		}

		public void setWebServiceDetails(WebServiceDetails webServiceDetails) {
			this.webServiceDetails = webServiceDetails;
		}
	}

	public static class OfficialIntimation {

		private List<OfficeUnitData> allowedOffices;

		private Boolean allowApplicationView;

		private Boolean allowHistoryView;

		private Boolean autoClear;

		public List<OfficeUnitData> getAllowedOffices() {
			return allowedOffices;
		}

		public void setAllowedOffices(List<OfficeUnitData> allowedOffices) {
			this.allowedOffices = allowedOffices;
		}

		public Boolean getAllowApplicationView() {
			return allowApplicationView;
		}

		public void setAllowApplicationView(Boolean allowApplicationView) {
			this.allowApplicationView = allowApplicationView;
		}

		public Boolean getAllowHistoryView() {
			return allowHistoryView;
		}

		public void setAllowHistoryView(Boolean allowHistoryView) {
			this.allowHistoryView = allowHistoryView;
		}

		public Boolean getAutoClear() {
			return autoClear;
		}

		public void setAutoClear(Boolean autoClear) {
			this.autoClear = autoClear;
		}
	}

	public Integer getServiceId() {
		return serviceId;
	}

	public void setServiceId(Integer serviceId) {
		this.serviceId = serviceId;
	}

	public List<Data> getData() {
		return data;
	}

	public void setData(List<Data> data) {
		this.data = data;
	}

	public List<AssociatedActivity> getAssociatedActivities() {
		return associatedActivities;
	}

	public void setAssociatedActivities(List<AssociatedActivity> associatedActivities) {
		this.associatedActivities = associatedActivities;
	}

	public Map<String, TaskRelationDTO> getTaskRelation() {
		return taskRelation;
	}

	public void setTaskRelation(Map<String, TaskRelationDTO> taskRelation) {
		this.taskRelation = taskRelation;
	}

}
