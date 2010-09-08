package com.cwctravel.hudson.plugins.extended_choice_parameter;

import hudson.Extension;
import hudson.model.ParameterValue;
import hudson.model.ParameterDefinition;

import java.io.File;
import java.io.FileInputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

public class ExtendedChoiceParameterDefinition extends ParameterDefinition {
	private static final long serialVersionUID = -2946187268529865645L;

	public static final String PARAMETER_TYPE_SINGLE_SELECT = "PT_SINGLE_SELECT";

	public static final String PARAMETER_TYPE_MULTI_SELECT = "PT_MULTI_SELECT";

	@Extension
	public static class DescriptorImpl extends ParameterDescriptor {
		@Override
		public String getDisplayName() {
			return "Extended Choice Parameter";
		}
	}

	private String type;

	private String value;

	private String propertyFile;

	private String propertyKey;

	private String defaultValue;

	private String defaultPropertyFile;

	private String defaultPropertyKey;

	private Map<String, Boolean> defaultValueMap;

	@DataBoundConstructor
	public ExtendedChoiceParameterDefinition(String name, String type, String value, String propertyFile, String propertyKey, String defaultValue,
			String defaultPropertyFile, String defaultPropertyKey, String description) {
		super(name, description);
		this.type = type;

		this.propertyFile = propertyFile;
		this.propertyKey = propertyKey;

		this.defaultPropertyFile = defaultPropertyFile;
		this.defaultPropertyKey = defaultPropertyKey;

		this.value = computeValue(value, propertyFile, propertyKey);
		this.defaultValue = computeValue(defaultValue, defaultPropertyFile, defaultPropertyKey);

		computeDefaultValueMap();
	}

	private void computeDefaultValueMap() {
		if (!StringUtils.isBlank(defaultValue)) {
			defaultValueMap = new HashMap<String, Boolean>();
			String[] defaultValues = StringUtils.split(defaultValue, ',');
			for(String value: defaultValues) {
				defaultValueMap.put(value, true);
			}
		}
	}

	@Override
	public ParameterValue createValue(StaplerRequest request) {
		String value[] = request.getParameterValues(getName());
		if (value == null) {
			return getDefaultParameterValue();
		}
		return null;
	}

	@Override
	public ParameterValue createValue(StaplerRequest request, JSONObject jO) {
		Object value = jO.get("value");
		String strValue = "";
		if (value instanceof String) {
			strValue = (String)value;
		}
		else if (value instanceof JSONArray) {
			JSONArray jsonValues = (JSONArray)value;
			for(int i = 0; i < jsonValues.size(); i++) {
				strValue += jsonValues.getString(i);
				if (i < jsonValues.size() - 1) {
					strValue += ",";
				}
			}
		}
		strValue = "\"" + strValue + "\"";
		ExtendedChoiceParameterValue extendedChoiceParameterValue = new ExtendedChoiceParameterValue(jO.getString("name"), strValue);
		return extendedChoiceParameterValue;
	}

	@Override
	public ParameterValue getDefaultParameterValue() {
		String defaultValue = getDefaultValue();
		if (!StringUtils.isBlank(defaultValue)) {
			defaultValue = "\"" + defaultValue + "\"";
			return new ExtendedChoiceParameterValue(getName(), defaultValue);
		}
		return super.getDefaultParameterValue();
	}

	private String computeValue(String value, String propertyFile, String propertyKey) {
		if (!StringUtils.isBlank(propertyFile) && !StringUtils.isBlank(propertyKey)) {
			FileInputStream fIS = null;
			try {
				fIS = new FileInputStream(new File(propertyFile));
				Properties properties = new Properties();
				properties.load(fIS);
				return properties.getProperty(propertyKey);
			}
			catch(Exception e) {

			}
			finally {
				IOUtils.closeQuietly(fIS);
			}
		}
		else if (!StringUtils.isBlank(value)) {
			return value;
		}
		return null;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public String getDefaultValue() {
		return computeValue(defaultValue, defaultPropertyFile, defaultPropertyKey);
	}

	public void setDefaultValue(String defaultValue) {
		this.defaultValue = defaultValue;
	}

	public String getPropertyFile() {
		return propertyFile;
	}

	public void setPropertyFile(String propertyFile) {
		this.propertyFile = propertyFile;
	}

	public String getDefaultPropertyKey() {
		return defaultPropertyKey;
	}

	public void setDefaultPropertyKey(String defaultPropertyKey) {
		this.defaultPropertyKey = defaultPropertyKey;
	}

	public String getValue() {
		return computeValue(value, propertyFile, propertyKey);
	}

	public void setValue(String value) {
		this.value = value;
	}

	public String getPropertyKey() {
		return propertyKey;
	}

	public void setPropertyKey(String propertyKey) {
		this.propertyKey = propertyKey;
	}

	public String getDefaultPropertyFile() {
		return defaultPropertyFile;
	}

	public void setDefaultPropertyFile(String defaultPropertyFile) {
		this.defaultPropertyFile = defaultPropertyFile;
	}

	public Map<String, Boolean> getDefaultValueMap() {
		return defaultValueMap;
	}

}
