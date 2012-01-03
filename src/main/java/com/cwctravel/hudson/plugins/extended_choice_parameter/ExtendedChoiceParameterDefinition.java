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

	private boolean quoteValue;

	private String type;

	private String value;

	private String propertyFile;

	private String propertyKey;

	private String defaultValue;

	private String defaultPropertyFile;

	private String defaultPropertyKey;

	@DataBoundConstructor
	public ExtendedChoiceParameterDefinition(String name, String type, String value, String propertyFile, String propertyKey, String defaultValue,
			String defaultPropertyFile, String defaultPropertyKey, boolean quoteValue, String description) {
		super(name, description);
		this.type = type;

		this.propertyFile = propertyFile;
		this.propertyKey = propertyKey;

		this.defaultPropertyFile = defaultPropertyFile;
		this.defaultPropertyKey = defaultPropertyKey;
		this.value = value;
		this.defaultValue = defaultValue;
		this.quoteValue = quoteValue;
	}

	private Map<String,Boolean> computeDefaultValueMap() {
		Map<String,Boolean> defaultValueMap = null;
		String effectiveDefaultValue = getEffectiveDefaultValue();
		if (!StringUtils.isBlank(effectiveDefaultValue)) {
			defaultValueMap = new HashMap<String, Boolean>();
			String[] defaultValues = StringUtils.split(effectiveDefaultValue, ',');
			for(String value: defaultValues) {
				defaultValueMap.put(StringUtils.trim(value), true);
			}
		}
		return defaultValueMap;
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
			strValue = StringUtils.join(jsonValues.iterator(), ',');	
		}

		if (quoteValue) {
			strValue = "\"" + strValue + "\"";
		}
		return new ExtendedChoiceParameterValue(getName(), strValue);
	}

	@Override
	public ParameterValue getDefaultParameterValue() {
		String defaultValue = getEffectiveDefaultValue();
		if (!StringUtils.isBlank(defaultValue)) {
			if (quoteValue) {
				defaultValue = "\"" + defaultValue + "\"";
			}
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

	public String getEffectiveDefaultValue() {
		return computeValue(defaultValue, defaultPropertyFile, defaultPropertyKey);
	}
	
	public String getDefaultValue() {
		return defaultValue;
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
	
	public String getEffectiveValue() {
		return computeValue(value, propertyFile, propertyKey);
	}

	public String getValue() {
		return value;
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

	public boolean isQuoteValue() {
		return quoteValue;
	}

	public void setQuoteValue(boolean quoteValue) {
		this.quoteValue = quoteValue;
	}

	public void setDefaultPropertyFile(String defaultPropertyFile) {
		this.defaultPropertyFile = defaultPropertyFile;
	}

	public Map<String, Boolean> getDefaultValueMap() {
		return computeDefaultValueMap();
	}

}
