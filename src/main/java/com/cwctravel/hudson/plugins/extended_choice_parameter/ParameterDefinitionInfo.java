package com.cwctravel.hudson.plugins.extended_choice_parameter;

import java.util.Map;

public class ParameterDefinitionInfo {
	private String effectiveValue;
	private Map<String, Boolean> defaultValueMap;
	private Map<String, String> descriptionPropertyValueMap;

	public String getEffectiveValue() {
		return effectiveValue;
	}

	public void setEffectiveValue(String effectiveValue) {
		this.effectiveValue = effectiveValue;
	}

	public Map<String, Boolean> getDefaultValueMap() {
		return defaultValueMap;
	}

	public void setDefaultValueMap(Map<String, Boolean> defaultValueMap) {
		this.defaultValueMap = defaultValueMap;
	}

	public Map<String, String> getDescriptionPropertyValueMap() {
		return descriptionPropertyValueMap;
	}

	public void setDescriptionPropertyValueMap(Map<String, String> descriptionPropertyValueMap) {
		this.descriptionPropertyValueMap = descriptionPropertyValueMap;
	}

}
