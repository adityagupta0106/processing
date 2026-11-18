package com.serviceplus.form.validation.dto;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class UsedAttributeListDTO {

    private Set<String> mvelUsedAttrList = new HashSet<>();
    private Set<String> documentUsedAttrList = new HashSet<>();
    private Set<String> paymentUsedAttrList = new HashSet<>();
    private Set<String> patternGenerationUsedAttrList = new HashSet<>();
    private Set<String> routingUsedAttrList = new HashSet<>();
    private Set<String> inboxSentboxFiltersUsedAttrList = new HashSet<>();
    private Set<String> notificationUsedAttrList = new HashSet<>();

    /**
     * Map capturing any future or custom attribute list nodes dynamically from incoming JSON.
     */
    private Map<String, Set<String>> additionalUsedAttrLists = new HashMap<>();

    public Set<String> getMvelUsedAttrList() {
        return mvelUsedAttrList;
    }

    public void setMvelUsedAttrList(Set<String> mvelUsedAttrList) {
        this.mvelUsedAttrList = mvelUsedAttrList;
    }

    public Set<String> getDocumentUsedAttrList() {
        return documentUsedAttrList;
    }

    public void setDocumentUsedAttrList(Set<String> documentUsedAttrList) {
        this.documentUsedAttrList = documentUsedAttrList;
    }

    public Set<String> getPaymentUsedAttrList() {
        return paymentUsedAttrList;
    }

    public void setPaymentUsedAttrList(Set<String> paymentUsedAttrList) {
        this.paymentUsedAttrList = paymentUsedAttrList;
    }

    public Set<String> getPatternGenerationUsedAttrList() {
        return patternGenerationUsedAttrList;
    }

    public void setPatternGenerationUsedAttrList(Set<String> patternGenerationUsedAttrList) {
        this.patternGenerationUsedAttrList = patternGenerationUsedAttrList;
    }

    public Set<String> getRoutingUsedAttrList() {
        return routingUsedAttrList;
    }

    public void setRoutingUsedAttrList(Set<String> routingUsedAttrList) {
        this.routingUsedAttrList = routingUsedAttrList;
    }

    public Set<String> getInboxSentboxFiltersUsedAttrList() {
        return inboxSentboxFiltersUsedAttrList;
    }

    public void setInboxSentboxFiltersUsedAttrList(Set<String> inboxSentboxFiltersUsedAttrList) {
        this.inboxSentboxFiltersUsedAttrList = inboxSentboxFiltersUsedAttrList;
    }

    public Set<String> getNotificationUsedAttrList() {
        return notificationUsedAttrList;
    }

    public void setNotificationUsedAttrList(Set<String> notificationUsedAttrList) {
        this.notificationUsedAttrList = notificationUsedAttrList;
    }

    @JsonAnySetter
    public void setAdditionalUsedAttrList(String key, Set<String> value) {
        if (additionalUsedAttrLists == null) {
            additionalUsedAttrLists = new HashMap<>();
        }
        additionalUsedAttrLists.put(key, value);
    }

    @JsonAnyGetter
    public Map<String, Set<String>> getAdditionalUsedAttrLists() {
        return additionalUsedAttrLists;
    }

    /**
     * Consolidates all used attribute lists into a single unified Set.
     * Automatically includes payment, pattern generation, routing, document, mvel, notification, inbox/sentbox,
     * as well as any future nodes added dynamically via additionalUsedAttrLists.
     */
    public Set<String> getAllUsedAttributes() {
        Set<String> all = new HashSet<>();
        if (paymentUsedAttrList != null) all.addAll(paymentUsedAttrList);
        if (patternGenerationUsedAttrList != null) all.addAll(patternGenerationUsedAttrList);
        if (routingUsedAttrList != null) all.addAll(routingUsedAttrList);
        if (mvelUsedAttrList != null) all.addAll(mvelUsedAttrList);
        if (documentUsedAttrList != null) all.addAll(documentUsedAttrList);
        if (inboxSentboxFiltersUsedAttrList != null) all.addAll(inboxSentboxFiltersUsedAttrList);
        if (notificationUsedAttrList != null) all.addAll(notificationUsedAttrList);
        if (additionalUsedAttrLists != null) {
            for (Set<String> set : additionalUsedAttrLists.values()) {
                if (set != null) {
                    all.addAll(set);
                }
            }
        }
        return all;
    }
}