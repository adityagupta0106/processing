package com.serviceplus.form.validation.kafka.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.serviceplus.form.validation.dto.FormData;
import com.serviceplus.form.validation.dto.FormData.Attribute;
import com.serviceplus.form.validation.dto.FormData.Form;
import com.serviceplus.form.validation.dto.FormData.Task;
import com.serviceplus.form.validation.dto.FormDataEvent;
import com.serviceplus.form.validation.entity.ApplicationData;
import com.serviceplus.form.validation.repository.ApplicationDataRepository;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class AppAttrKafkaConsumer implements KafkaEventHandler<FormDataEvent> {

    private static final Logger logger = LoggerFactory.getLogger("kafkaLogger");
    private static final String ATTR_VERSION_KEY = "v";

    private final ApplicationDataRepository applicationDataRepository;
    private final String topic;

    public AppAttrKafkaConsumer(ApplicationDataRepository applicationDataRepository, @Value("${kafka.topic.form-data}") String topic) {
        this.applicationDataRepository = applicationDataRepository;
        this.topic = topic;
    }

    @Override
    public String getTopic() {
        return topic;
    }

    @Override
    public Class<FormDataEvent> getPayloadType() {
        return FormDataEvent.class;
    }

    @Override
    public Mono<Void> process(FormDataEvent event) {
        if (event == null) {
            logger.warn("Received null form data event");
            return Mono.empty();
        }

        if (event.getApplicationId() == null || event.getApplicationId().isBlank()) {
            logger.warn("Application ID missing in form data event");
            return Mono.empty();
        }

        if (event.getFormData() == null || event.getFormData().isEmpty()) {
            logger.info(
                    "No form data received. applicationId={}",
                    event.getApplicationId());
            return Mono.empty();
        }


        return saveApplicationData(event).then();
    }

    private Mono<ApplicationData> saveApplicationData(FormDataEvent event) {
        String applicationId = event.getApplicationId();

        return applicationDataRepository
                .findById(applicationId)
                .defaultIfEmpty(newApplicationData(event))
                .flatMap(applicationData -> {
                    FormData attrData;

                    try {
                        applicationData.convertAttrValToObject();
                        attrData = applicationData.getAttrValObject();

                        if (attrData == null) {
                            attrData = new FormData();
                        }

                        setAttributesData(attrData, event.getFormData(), event);

                        applicationData.setAttrValObject(attrData);
                        applicationData.convertAttrValToJson();

                        applicationData.setuDate(LocalDateTime.now());

                        if (applicationData.getApplicationId() == null) {
                            applicationData.setApplicationId(applicationId);
                        }

                        if (applicationData.getServiceId() == null) {
                            applicationData.setServiceId(event.getServiceId());
                        }

                        if (applicationData.getBaseServiceId() == null
                                && event.getServiceId() != null) {
                            applicationData.setBaseServiceId(
                                    event.getServiceId() / 10000);
                        }

                        if (applicationData.getTenantId() == null) {
                            applicationData.setTenantId(event.getTenantId());
                        }

                        return applicationDataRepository.save(applicationData);
                    } catch (Exception e) {
                        logger.error(
                                "Error preparing application attribute data. applicationId={}",
                                applicationId,
                                e);
                        return Mono.error(e);
                    }
                })
                .doOnSuccess(applicationData ->
                        logger.info(
                                "Application attribute data saved. applicationId={}",
                                applicationId))
                .doOnError(error ->
                        logger.error(
                                "Error saving application attribute data. applicationId={}",
                                applicationId,
                                error));
    }

    private ApplicationData newApplicationData(FormDataEvent event) {
        ApplicationData applicationData = new ApplicationData();

        applicationData.setApplicationId(event.getApplicationId());
        applicationData.setServiceId(event.getServiceId());
        applicationData.setTenantId(event.getTenantId());

        if (event.getServiceId() != null) {
            applicationData.setBaseServiceId(
                    event.getServiceId() / 10000);
        }

        applicationData.setNewEntity(true);

        return applicationData;
    }

    
	private void setAttributesData(FormData attrData, Map<String, Object> formData, FormDataEvent event) {

		List<String> taskIds = event.getTaskIds();

		if (taskIds == null || taskIds.isEmpty()) {
			return;
		}

		Map<String, Task> taskMap = attrData.getTasks() != null ? attrData.getTasks() : new HashMap<>();

		Set<String> usedAttributes = event.getUsedAttributeList() != null
				? event.getUsedAttributeList().getAllUsedAttributes()
				: Collections.emptySet();

		for (String taskId : taskIds) {

			if (taskId == null || taskId.isBlank()) {
				continue;
			}

			Task task = taskMap.get(taskId) != null ? taskMap.get(taskId) : new Task();

			Map<String, Form> forms = task.getForms() != null ? task.getForms() : new HashMap<>();

			Form form = forms.get(event.getHolderId()) != null ? forms.get(event.getHolderId()) : new Form();

			Map<String, Attribute> attributes = form.getAttributes() != null ? form.getAttributes() : new HashMap<>();

			for (Map.Entry<String, Object> entry : formData.entrySet()) {
				String attributeId = entry.getKey();
				Object rawValue = entry.getValue();

				String attributeKey = taskId + "$" + event.getHolderId() + "$" + attributeId;

				if (!usedAttributes.contains(attributeKey)) {
					continue;
				}

				String finalValue;
				String labelValue;

				if (rawValue instanceof String str) {
					try {
						JSONObject json = new JSONObject(str);
						finalValue = json.optString("value", str);
						labelValue = json.optString("value", str);
					} catch (Exception e) {
						finalValue = str;
						labelValue = str;
					}

				} else if (rawValue instanceof Map<?, ?> map) {
					Object value = map.get("value");
					finalValue = value != null ? value.toString() : null;

					Object label = map.get("label");
					labelValue = label != null ? label.toString() : finalValue;

				} else if (rawValue instanceof List<?> list) {
					List<String> values = new ArrayList<>();
					List<String> labels = new ArrayList<>();

					for (Object item : list) {
						if (item instanceof Map<?, ?> itemMap) {
							Object value = itemMap.get("value");
							Object label = itemMap.get("label");

							if (value != null) {
								values.add(value.toString());
							}

							if (label != null) {
								labels.add(label.toString());
							} else if (value != null) {
								labels.add(value.toString());
							}

						} else if (item != null) {
							values.add(item.toString());
							labels.add(item.toString());
						}
					}

					finalValue = String.join(",", values);
					labelValue = String.join(",", labels);

				} else {
					finalValue = rawValue != null ? rawValue.toString() : null;

					labelValue = rawValue != null ? rawValue.toString() : null;
				}

				Attribute attribute = attributes.get(attributeId) != null ? attributes.get(attributeId)
						: new Attribute();

				Map<String, Object> versionsMap;
				int current;

				if (attribute.getCurrent() == null) {
					current = 1;
					versionsMap = new HashMap<>();
				} else {
					current = attribute.getCurrent() + 1;
					versionsMap = attribute.getVersions();

					if (versionsMap == null) {
						versionsMap = new HashMap<>();
					}
				}

				versionsMap.put(ATTR_VERSION_KEY + current, finalValue);
				versionsMap.put(ATTR_VERSION_KEY + current + "_label", labelValue);

				attribute.setVersions(versionsMap);
				attribute.setCurrent(current);
				attributes.put(attributeId, attribute);
			}

			form.setAttributes(attributes);
			forms.put(event.getHolderId(), form);

			task.setForms(forms);

			taskMap.put(taskId, task);
		}

		attrData.setTasks(taskMap);
	}

}