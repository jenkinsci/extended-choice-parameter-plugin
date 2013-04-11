package com.cwctravel.hudson.plugins.extended_choice_parameter;

import hudson.Extension;
import hudson.model.ParameterValue;
import hudson.model.ParameterDefinition;
import hudson.util.FormValidation;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.ServletException;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import org.apache.commons.lang.StringUtils;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.Property;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

public class ExtendedChoiceParameterDefinition extends ParameterDefinition {
	private static final long serialVersionUID = -2946187268529865645L;

	public static final String PARAMETER_TYPE_SINGLE_SELECT = "PT_SINGLE_SELECT";

	public static final String PARAMETER_TYPE_MULTI_SELECT = "PT_MULTI_SELECT";

	public static final String PARAMETER_TYPE_CHECK_BOX = "PT_CHECKBOX";

	public static final String PARAMETER_TYPE_RADIO = "PT_RADIO";

	public static final String PARAMETER_TYPE_TEXT_BOX = "PT_TEXTBOX";

	@Extension
	public static class DescriptorImpl extends ParameterDescriptor {
		@Override
		public String getDisplayName() {
			return Messages.ExtendedChoiceParameterDefinition_DisplayName();
		}

		public FormValidation doCheckPropertyFile(@QueryParameter final String propertyFile, @QueryParameter final String propertyKey) throws IOException, ServletException {
			if(StringUtils.isBlank(propertyFile)) {
				return FormValidation.ok();
			}

			Project project = new Project();
			Property property = new Property();
			property.setProject(project);

			File prop = new File(propertyFile);
			try {
				if(prop.exists()) {
					property.setFile(prop);
				}
				else {
					URL propertyFileUrl = new URL(propertyFile);
					property.setUrl(propertyFileUrl);
				}
				property.execute();
			}
			catch(Exception e) {
				return FormValidation.warning(Messages.ExtendedChoiceParameterDefinition_PropertyFileDoesntExist(), propertyFile);
			}

			if(StringUtils.isNotBlank(propertyKey)) {
				if(project.getProperty(propertyKey) != null) {
					return FormValidation.ok();
				}
				else {
					return FormValidation.warning(Messages.ExtendedChoiceParameterDefinition_PropertyFileExistsButProvidedKeyIsInvalid(), propertyFile, propertyKey);
				}
			}
			else {
				return FormValidation.warning(Messages.ExtendedChoiceParameterDefinition_PropertyFileExistsButNoProvidedKey(), propertyFile);
			}
		}

		public FormValidation doCheckPropertyKey(@QueryParameter final String propertyFile, @QueryParameter final String propertyKey) throws IOException, ServletException {
			return doCheckPropertyFile(propertyFile, propertyKey);
		}

		public FormValidation doCheckDefaultPropertyFile(@QueryParameter final String defaultPropertyFile,
				@QueryParameter final String defaultPropertyKey) throws IOException, ServletException {
			return doCheckPropertyFile(defaultPropertyFile, defaultPropertyKey);
		}

		public FormValidation doCheckDefaultPropertyKey(@QueryParameter final String defaultPropertyFile,
				@QueryParameter final String defaultPropertyKey) throws IOException, ServletException {
			return doCheckPropertyFile(defaultPropertyFile, defaultPropertyKey);
		}
	}

	private boolean quoteValue;

	private int visibleItemCount;

	private String type;

	private String value;

	private String propertyFile;

	private String propertyKey;

	private String defaultValue;

	private String defaultPropertyFile;

	private String defaultPropertyKey;
	
	private String multiSelectDelimiter;

	@DataBoundConstructor
	public ExtendedChoiceParameterDefinition(String name, String type, String value, String propertyFile, String propertyKey, String defaultValue,
			String defaultPropertyFile, String defaultPropertyKey, boolean quoteValue, int visibleItemCount, String description,
			String multiSelectDelimiter) {
		super(name, description);
		this.type = type;

		this.propertyFile = propertyFile;
		this.propertyKey = propertyKey;

		this.defaultPropertyFile = defaultPropertyFile;
		this.defaultPropertyKey = defaultPropertyKey;
		this.value = value;
		this.defaultValue = defaultValue;
		this.quoteValue = quoteValue;
		if(visibleItemCount == 0) {
			visibleItemCount = 5;
		}
		this.visibleItemCount = visibleItemCount;
		
		if(multiSelectDelimiter.equals("")) {
			multiSelectDelimiter = ",";
		}
		this.multiSelectDelimiter = multiSelectDelimiter;
	}

	private Map<String, Boolean> computeDefaultValueMap() {
		Map<String, Boolean> defaultValueMap = null;
		String effectiveDefaultValue = getEffectiveDefaultValue();
		if(!StringUtils.isBlank(effectiveDefaultValue)) {
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
		String[] requestValues = request.getParameterValues(getName());
		if(requestValues == null || requestValues.length == 0) {
			return getDefaultParameterValue();
		}
		if(PARAMETER_TYPE_TEXT_BOX.equals(type)) {
			return new ExtendedChoiceParameterValue(getName(), requestValues[0]);
		}
		else {
			String valueStr = getEffectiveValue();
			if(valueStr != null) {
				List<String> result = new ArrayList<String>();

				String[] values = valueStr.split(",");
				Set<String> valueSet = new HashSet<String>();
				for(String value: values) {
					valueSet.add(value);
				}

				for(String requestValue: requestValues) {
					if(valueSet.contains(requestValue)) {
						result.add(requestValue);
					}
				}

				return new ExtendedChoiceParameterValue(getName(), StringUtils.join(result, getMultiSelectDelimiter()));
			}
		}
		return null;
	}

	@Override
	public ParameterValue createValue(StaplerRequest request, JSONObject jO) {
		Object value = jO.get("value");
		String strValue = "";
		if(value instanceof String) {
			strValue = (String)value;
		}
		else if(value instanceof JSONArray) {
			JSONArray jsonValues = (JSONArray)value;
			strValue = StringUtils.join(jsonValues.iterator(), getMultiSelectDelimiter());
		}

		if(quoteValue) {
			strValue = "\"" + strValue + "\"";
		}
		return new ExtendedChoiceParameterValue(getName(), strValue);
	}

	@Override
	public ParameterValue getDefaultParameterValue() {
		String defaultValue = getEffectiveDefaultValue();
		if(!StringUtils.isBlank(defaultValue)) {
			if(quoteValue) {
				defaultValue = "\"" + defaultValue + "\"";
			}
			return new ExtendedChoiceParameterValue(getName(), defaultValue);
		}
		return super.getDefaultParameterValue();
	}

	private String computeValue(String value, String propertyFilePath, String propertyKey) {
		if(!StringUtils.isBlank(propertyFile) && !StringUtils.isBlank(propertyKey)) {
			try {

				Project project = new Project();
				Property property = new Property();
				property.setProject(project);

				File propertyFile = new File(propertyFilePath);
				if(propertyFile.exists()) {
					property.setFile(propertyFile);
				}
				else {
					URL propertyFileUrl = new URL(propertyFilePath);
					property.setUrl(propertyFileUrl);
				}
				property.execute();

				return project.getProperty(propertyKey);
			}
			catch(Exception e) {

			}
		}
		else if(!StringUtils.isBlank(value)) {
			return value;
		}
		return null;
	}

	@Override
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

	public int getVisibleItemCount() {
		return visibleItemCount;
	}

	public void setVisibleItemCount(int visibleItemCount) {
		this.visibleItemCount = visibleItemCount;
	}
	
	public String getMultiSelectDelimiter() {
		return this.multiSelectDelimiter;
	}
	
	public void setMultiSelectDelimiter(final String multiSelectDelimiter) {
		this.multiSelectDelimiter = multiSelectDelimiter;
	}

	public void setDefaultPropertyFile(String defaultPropertyFile) {
		this.defaultPropertyFile = defaultPropertyFile;
	}

	public Map<String, Boolean> getDefaultValueMap() {
		return computeDefaultValueMap();
	}

}
