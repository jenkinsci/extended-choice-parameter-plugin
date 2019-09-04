/*
 *Copyright (c) 2013 Costco, Vimil Saju
 *Copyright (c) 2013 John DiMatteo
 *See the file license.txt for copying permission.
 */

package com.cwctravel.hudson.plugins.extended_choice_parameter;

import com.opencsv.CSVReader;
import groovy.lang.Binding;
import hudson.EnvVars;
import hudson.Extension;
import hudson.Util;
import hudson.cli.CLICommand;
import hudson.model.AbstractProject;
import hudson.model.ParameterDefinition;
import hudson.model.ParameterValue;
import hudson.model.User;
import hudson.util.FormValidation;
import hudson.util.LogTaskListener;
import jenkins.model.Jenkins;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.Property;
import org.boon.Boon;
import org.boon.core.value.LazyValueMap;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.scriptsecurity.sandbox.RejectedAccessException;
import org.jenkinsci.plugins.scriptsecurity.sandbox.groovy.SecureGroovyScript;
import org.jenkinsci.plugins.scriptsecurity.sandbox.whitelists.Whitelisted;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;

import javax.servlet.ServletException;
import java.io.*;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ExtendedChoiceParameterDefinition extends ParameterDefinition {
	private static final long serialVersionUID = -2946187268529865645L;

	private static final Logger LOGGER = Logger.getLogger(ExtendedChoiceParameterDefinition.class.getName());

	private static final String ATTR_REQUEST_GROOVY_BINDING = "com.cwctravel.hudson.plugins.extended_choice_parameter.groovyBinding";

	public static final String PARAMETER_TYPE_SINGLE_SELECT = "PT_SINGLE_SELECT";

	public static final String PARAMETER_TYPE_MULTI_SELECT = "PT_MULTI_SELECT";

	public static final String PARAMETER_TYPE_CHECK_BOX = "PT_CHECKBOX";

	public static final String PARAMETER_TYPE_RADIO = "PT_RADIO";

	public static final String PARAMETER_TYPE_TEXT_BOX = "PT_TEXTBOX";

	public static final String PARAMETER_TYPE_HIDDEN = "PT_HIDDEN";

	public static final String PARAMETER_TYPE_MULTI_LEVEL_SINGLE_SELECT = "PT_MULTI_LEVEL_SINGLE_SELECT";

	public static final String PARAMETER_TYPE_MULTI_LEVEL_MULTI_SELECT = "PT_MULTI_LEVEL_MULTI_SELECT";

	public static final String PARAMETER_TYPE_JSON = "PT_JSON";

	public enum ScriptResult {
		NotRun,
		Unapproved,
		GeneralError,
		OK
	}

	private Object groovyScriptResult;

	private ScriptResult groovyScriptResultStatus = ScriptResult.NotRun;

	@Extension @Symbol({"extendedChoice"})
	public static class DescriptorImpl extends ParameterDescriptor {
		@Override
		public String getDisplayName() {
			return Messages.ExtendedChoiceParameterDefinition_DisplayName();
		}

		public FormValidation doCheckPropertyFile(@QueryParameter final String propertyFile, @QueryParameter final String propertyKey,
				@QueryParameter final String type) throws IOException, ServletException {
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
			catch(MalformedURLException e) {
				return FormValidation.warning(Messages.ExtendedChoiceParameterDefinition_PropertyFileDoesntExist(), propertyFile);
			}
			catch(BuildException e) {
				return FormValidation.warning(Messages.ExtendedChoiceParameterDefinition_PropertyFileDoesntExist(), propertyFile);
			}

			if(PARAMETER_TYPE_MULTI_LEVEL_SINGLE_SELECT.equals(type) || PARAMETER_TYPE_MULTI_LEVEL_MULTI_SELECT.equals(type)) {
				return FormValidation.ok();
			}
			else if(StringUtils.isNotBlank(propertyKey)) {
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

		public FormValidation doCheckPropertyKey(@QueryParameter final String propertyFile, @QueryParameter final String propertyKey,
				@QueryParameter final String type) throws IOException, ServletException {
			return doCheckPropertyFile(propertyFile, propertyKey, type);
		}

		public FormValidation doCheckDefaultPropertyFile(@QueryParameter final String defaultPropertyFile,
				@QueryParameter final String defaultPropertyKey, @QueryParameter final String type) throws IOException, ServletException {
			return doCheckPropertyFile(defaultPropertyFile, defaultPropertyKey, type);
		}

		public FormValidation doCheckDefaultPropertyKey(@QueryParameter final String defaultPropertyFile,
				@QueryParameter final String defaultPropertyKey, @QueryParameter final String type) throws IOException, ServletException {
			return doCheckPropertyFile(defaultPropertyFile, defaultPropertyKey, type);
		}

		@Override
		public ExtendedChoiceParameterDefinition newInstance(StaplerRequest req, JSONObject formData) throws FormException {
			String name = null;
			String type = null;
			String description = null;
			String multiSelectDelimiter = null;
			boolean quoteValue = false;
			boolean saveJSONParameterToFile = false;
			int visibleItemCount = 5;

			String propertyValue = null;
			String propertyKey = null;
			String propertyFile = null;
			String groovyScript = null;
			String groovyScriptFile = null;
			boolean useGroovySandbox = false;
			String jsonScript = null;
			String bindings = null;
			String javascriptFile = null;
			String javascript = null;

			String defaultPropertyValue = null;
			String defaultPropertyKey = null;
			String defaultPropertyFile = null;
			String defaultGroovyScript = null;
			String defaultGroovyScriptFile = null;
			String defaultBindings = null;

			String descriptionPropertyValue = null;
			String descriptionPropertyKey = null;
			String descriptionPropertyFile = null;
			String descriptionGroovyScript = null;
			String descriptionGroovyScriptFile = null;
			String descriptionBindings = null;
			String projectName = null;
			name = formData.getString("name");
			description = formData.getString("description");

			AbstractProject<?, ?> project = Stapler.getCurrentRequest().findAncestorObject(AbstractProject.class);
			if(project != null) {
				projectName = project.getName();
			}

			JSONObject parameterGroup = formData.getJSONObject("parameterGroup");
			if(parameterGroup != null) {
				int value = parameterGroup.getInt("value");
				if(value == 0) {
					type = parameterGroup.getString("type");
					quoteValue = parameterGroup.getBoolean("quoteValue");
					visibleItemCount = parameterGroup.optInt("visibleItemCount", 5);
					multiSelectDelimiter = parameterGroup.getString("multiSelectDelimiter");
					if(StringUtils.isEmpty(multiSelectDelimiter)) {
						multiSelectDelimiter = ",";
					}

					JSONObject propertySourceJSON = (JSONObject)parameterGroup.get("propertySource");
					if(propertySourceJSON != null) {
						if(propertySourceJSON.getInt("value") == 0) {
							propertyValue = propertySourceJSON.getString("propertyValue");
						}
						else if(propertySourceJSON.getInt("value") == 1) {
							propertyFile = propertySourceJSON.getString("propertyFile");
							propertyKey = propertySourceJSON.getString("propertyKey");
						}
						else if(propertySourceJSON.getInt("value") == 2) {
							groovyScript = propertySourceJSON.getString("groovyScript");
							bindings = propertySourceJSON.getString("bindings");
							useGroovySandbox = propertySourceJSON.getBoolean("useGroovySandbox");
						}
						else if(propertySourceJSON.getInt("value") == 3) {
							groovyScriptFile = propertySourceJSON.getString("groovyScriptFile");
							bindings = propertySourceJSON.getString("bindings");
							useGroovySandbox = propertySourceJSON.getBoolean("useGroovySandbox");
						}
					}

					JSONObject defaultPropertySourceJSON = (JSONObject)parameterGroup.get("defaultPropertySource");
					if(defaultPropertySourceJSON != null) {
						if(defaultPropertySourceJSON.getInt("value") == 0) {
							defaultPropertyValue = defaultPropertySourceJSON.getString("defaultPropertyValue");
						}
						else if(defaultPropertySourceJSON.getInt("value") == 1) {
							defaultPropertyFile = defaultPropertySourceJSON.getString("defaultPropertyFile");
							defaultPropertyKey = defaultPropertySourceJSON.getString("defaultPropertyKey");
						}
						else if(defaultPropertySourceJSON.getInt("value") == 2) {
							defaultGroovyScript = defaultPropertySourceJSON.getString("defaultGroovyScript");
							defaultBindings = defaultPropertySourceJSON.getString("defaultBindings");
						}
						else if(defaultPropertySourceJSON.getInt("value") == 3) {
							defaultGroovyScriptFile = defaultPropertySourceJSON.getString("defaultGroovyScriptFile");
							defaultBindings = defaultPropertySourceJSON.getString("defaultBindings");
						}
					}

					JSONObject descriptionPropertySourceJSON = (JSONObject)parameterGroup.get("descriptionPropertySource");
					if(descriptionPropertySourceJSON != null) {
						if(descriptionPropertySourceJSON.getInt("value") == 0) {
							descriptionPropertyValue = descriptionPropertySourceJSON.getString("descriptionPropertyValue");
						}
						else if(descriptionPropertySourceJSON.getInt("value") == 1) {
							descriptionPropertyFile = descriptionPropertySourceJSON.getString("descriptionPropertyFile");
							descriptionPropertyKey = descriptionPropertySourceJSON.getString("descriptionPropertyKey");
						}
						else if(descriptionPropertySourceJSON.getInt("value") == 2) {
							descriptionGroovyScript = descriptionPropertySourceJSON.getString("descriptionGroovyScript");
							descriptionBindings = descriptionPropertySourceJSON.getString("descriptionBindings");
						}
						else if(descriptionPropertySourceJSON.getInt("value") == 3) {
							descriptionGroovyScriptFile = descriptionPropertySourceJSON.getString("descriptionGroovyScriptFile");
							descriptionBindings = descriptionPropertySourceJSON.getString("descriptionBindings");
						}
					}
				}
				else if(value == 1) {
					type = parameterGroup.getString("type");
					propertyFile = parameterGroup.getString("propertyFile");
					propertyValue = parameterGroup.optString("propertyValue");
				}
				else if(value == 2) {
					type = PARAMETER_TYPE_JSON;
					JSONObject jsonParameterConfigSourceJSON = (JSONObject)parameterGroup.get("jsonParameterConfigSource");
					if(jsonParameterConfigSourceJSON != null) {
						if(jsonParameterConfigSourceJSON.getInt("value") == 0) {
							jsonScript = null;
							groovyScript = jsonParameterConfigSourceJSON.getString("groovyScript");
							groovyScriptFile = null;
							bindings = jsonParameterConfigSourceJSON.getString("bindings");
							useGroovySandbox = jsonParameterConfigSourceJSON.getBoolean("useGroovySandbox");
						}
						else if(jsonParameterConfigSourceJSON.getInt("value") == 1) {
							groovyScript = null;
							jsonScript = null;
							groovyScriptFile = jsonParameterConfigSourceJSON.getString("groovyScriptFile");
							bindings = jsonParameterConfigSourceJSON.getString("bindings");
							useGroovySandbox = jsonParameterConfigSourceJSON.getBoolean("useGroovySandbox");
						}
						else if (jsonParameterConfigSourceJSON.getInt("value") == 2) {
							jsonScript = jsonParameterConfigSourceJSON.getString("jsonScript");
							groovyScript = null;
							groovyScriptFile = null;
							bindings = null;
						}
					}

					JSONObject jsonParameterConfigJavascriptSourceJSON = (JSONObject)parameterGroup.get("jsonParameterConfigJavascriptSource");
					if(jsonParameterConfigJavascriptSourceJSON != null) {
						if(jsonParameterConfigJavascriptSourceJSON.getInt("value") == 0) {
							javascript = jsonParameterConfigJavascriptSourceJSON.optString("javascript");
							javascriptFile = null;
						}
						else if(jsonParameterConfigJavascriptSourceJSON.getInt("value") == 1) {
							javascriptFile = jsonParameterConfigJavascriptSourceJSON.optString("javascriptFile");
							javascript = null;
						}
					}

					saveJSONParameterToFile = parameterGroup.optBoolean("saveJSONParameterToFile");
				}
			}
			else {
				type = formData.getString("type");
				propertyFile = formData.getString("propertyFile");
				propertyKey = formData.getString("propertyKey");
				propertyValue = formData.optString("value");
				defaultPropertyFile = formData.optString("defaultPropertyFile");
				defaultPropertyKey = formData.optString("defaultPropertyKey");
				defaultPropertyValue = formData.optString("defaultValue");
			}

			//@formatter:off
			return new ExtendedChoiceParameterDefinition(name,
														type,
														propertyValue,
														projectName,
														propertyFile,
														groovyScript,
														groovyScriptFile,
														jsonScript,
														useGroovySandbox,
														bindings,
														propertyKey,
														defaultPropertyValue,
														defaultPropertyFile,
														defaultGroovyScript,
														defaultGroovyScriptFile,
														defaultBindings,
														defaultPropertyKey,
														descriptionPropertyValue,
														descriptionPropertyFile,
														descriptionGroovyScript,
														descriptionGroovyScriptFile,
														descriptionBindings,
														descriptionPropertyKey,
														javascriptFile,
														javascript,
														saveJSONParameterToFile,
														quoteValue,
														visibleItemCount,
														description,
														multiSelectDelimiter);
			//@formatter:on
		}
	}

	private boolean quoteValue;

	private boolean saveJSONParameterToFile;

	private int visibleItemCount;

	private String type;

	private String value;

	private String propertyFile;

	private String groovyScript;

	private String groovyScriptFile;

	private String jsonScript;

	private String bindings;

	private String propertyKey;

	private String defaultValue;

	private String defaultPropertyFile;

	private String defaultGroovyScript;

	private String defaultGroovyScriptFile;

	private boolean useGroovySandbox;

	private String defaultBindings;

	private String defaultPropertyKey;

	private String multiSelectDelimiter;

	private String descriptionPropertyValue;

	private String descriptionPropertyFile;

	private String descriptionGroovyScript;

	private String descriptionGroovyScriptFile;

	private String descriptionBindings;

	private String descriptionPropertyKey;

	private String javascriptFile;

	private String javascript;

	private String projectName;

	//@formatter:off
	@Deprecated
	public ExtendedChoiceParameterDefinition(String name,
	                                         String type,
	                                         String value,
	                                         String projectName,
	                                         String propertyFile,
	                                         String groovyScript,
	                                         String groovyScriptFile,
	                                         String bindings,
	                                         String groovyClasspath,
	                                         String propertyKey,
	                                         String defaultValue,
	                                         String defaultPropertyFile,
	                                         String defaultGroovyScript,
	                                         String defaultGroovyScriptFile,
	                                         String defaultBindings,
	                                         String defaultGroovyClasspath,
	                                         String defaultPropertyKey,
	                                         String descriptionPropertyValue,
	                                         String descriptionPropertyFile,
	                                         String descriptionGroovyScript,
	                                         String descriptionGroovyScriptFile,
	                                         String descriptionBindings,
	                                         String descriptionGroovyClasspath,
	                                         String descriptionPropertyKey,
	                                         String javascriptFile,
	                                         String javascript,
	                                         boolean saveJSONParameterToFile,
	                                         boolean quoteValue,
	                                         int visibleItemCount,
	                                         String description,
	                                         String multiSelectDelimiter) {
		this(name,
				type,
				value,
				projectName,
				propertyFile,
				groovyScript,
				groovyScriptFile,
				null,
				true,
				bindings,
				propertyKey,
				defaultValue,
				defaultPropertyFile,
				defaultGroovyScript,
				defaultGroovyScriptFile,
				defaultBindings,
				defaultPropertyKey,
				descriptionPropertyValue,
				descriptionPropertyFile,
				descriptionGroovyScript,
				descriptionGroovyScriptFile,
				descriptionBindings,
				descriptionPropertyKey,
				javascriptFile,
				javascript,
				saveJSONParameterToFile,
				quoteValue,
				visibleItemCount,
				description,
				multiSelectDelimiter);
	}

	//@formatter:off
	@DataBoundConstructor
	@Whitelisted
	public ExtendedChoiceParameterDefinition(String name,
			String type,
			String value,
			String projectName,
			String propertyFile,
			String groovyScript,
			String groovyScriptFile,
			String jsonScript,
            boolean useGroovySandbox,
			String bindings,
			String propertyKey,
			String defaultValue,
			String defaultPropertyFile,
			String defaultGroovyScript,
			String defaultGroovyScriptFile,
			String defaultBindings,
			String defaultPropertyKey,
			String descriptionPropertyValue,
			String descriptionPropertyFile,
			String descriptionGroovyScript,
			String descriptionGroovyScriptFile,
			String descriptionBindings,
			String descriptionPropertyKey,
			String javascriptFile,
			String javascript,
			boolean saveJSONParameterToFile,
			boolean quoteValue,
			int visibleItemCount,
			String description,
			String multiSelectDelimiter) {
	//@formatter:on

		super(name, description);

		this.type = type;
		this.value = value;
		this.projectName = projectName;
		this.propertyFile = propertyFile;
		this.propertyKey = propertyKey;
		this.groovyScript = groovyScript;
		this.groovyScriptFile = groovyScriptFile;
		this.jsonScript = jsonScript;
		this.useGroovySandbox = useGroovySandbox;
		this.bindings = bindings;

		this.defaultValue = defaultValue;
		this.defaultPropertyFile = defaultPropertyFile;
		this.defaultPropertyKey = defaultPropertyKey;
		this.defaultGroovyScript = defaultGroovyScript;
		this.defaultGroovyScriptFile = defaultGroovyScriptFile;
		this.defaultBindings = defaultBindings;

		this.descriptionPropertyValue = descriptionPropertyValue;
		this.descriptionPropertyFile = descriptionPropertyFile;
		this.descriptionPropertyKey = descriptionPropertyKey;
		this.descriptionGroovyScript = descriptionGroovyScript;
		this.descriptionGroovyScriptFile = descriptionGroovyScriptFile;
		this.descriptionBindings = descriptionBindings;
		this.javascriptFile = javascriptFile;
		this.javascript = javascript;
		this.saveJSONParameterToFile = saveJSONParameterToFile;

		this.quoteValue = quoteValue;
		if(visibleItemCount == 0) {
			visibleItemCount = 5;
		}
		this.visibleItemCount = visibleItemCount;

		if(multiSelectDelimiter == null || "".equals(multiSelectDelimiter)) {
			multiSelectDelimiter = ",";
		}
		this.multiSelectDelimiter = multiSelectDelimiter;
	}

	private Map<String, Boolean> computeDefaultValueMap() {
		Map<String, Boolean> defaultValueMap = null;
		String effectiveDefaultValue = computeEffectiveDefaultValue();
		if(!StringUtils.isBlank(effectiveDefaultValue)) {
			defaultValueMap = new HashMap<String, Boolean>();
			String[] defaultValues = StringUtils.split(effectiveDefaultValue, ',');
			for(String value: defaultValues) {
				defaultValueMap.put(StringUtils.trim(value), true);
			}
		}
		return defaultValueMap;
	}

	private Map<String, String> computeDescriptionPropertyValueMap(String effectiveValue) {
		Map<String, String> descriptionPropertyValueMap = null;
		if(effectiveValue != null) {
			String[] values = effectiveValue.split(",");
			String effectiveDescriptionPropertyValue = computeEffectiveDescriptionPropertyValue();
			if(!StringUtils.isBlank(effectiveDescriptionPropertyValue)) {
				descriptionPropertyValueMap = new HashMap<String, String>();
				String[] descriptionPropertyValues = StringUtils.split(effectiveDescriptionPropertyValue, ',');
				for(int i = 0; i < values.length && i < descriptionPropertyValues.length; i++) {
					descriptionPropertyValueMap.put(values[i], descriptionPropertyValues[i]);
				}
			}
		}
		return descriptionPropertyValueMap;
	}

	@Override
	public ParameterValue createValue(StaplerRequest request) {
		String[] requestValues = request.getParameterValues(getName());
		return createValue(requestValues);
	}

	@Override
	public ParameterValue createValue(CLICommand command, String value) throws IOException, InterruptedException {
		String[] requestValues = (value != null) ? value.split(",") : null;
		return createValue(requestValues);
	}

	/*package*/ParameterValue createValue(String[] requestValues) {
		if(requestValues == null || requestValues.length == 0) {
			return getDefaultParameterValue();
		}
		if(PARAMETER_TYPE_TEXT_BOX.equals(type) || PARAMETER_TYPE_HIDDEN.equals(type)) {
			return new ExtendedChoiceParameterValue(getName(), requestValues[0]);
		}
		else {
			String valueStr = computeEffectiveValue();
			if(valueStr != null) {
				List<String> result = new ArrayList<>();

				String[] values = valueStr.split(",");
				Set<String> valueSet = new HashSet<>();
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
			StringBuilder sB = new StringBuilder();
			JSONArray jsonValues = (JSONArray)value;
			if(isMultiLevelParameterType()) {
				final int valuesBetweenLevels = this.value.split(",").length;

				Iterator<?> it = jsonValues.iterator();
				for(int i = 1; it.hasNext(); i++) {
					String nextValue = it.next().toString();
					if(i % valuesBetweenLevels == 0) {
						if(sB.length() > 0) {
							sB.append(getMultiSelectDelimiter());
						}
						sB.append(nextValue);
					}
				}
				strValue = sB.toString();
			}
			else {
				strValue = StringUtils.join(jsonValues.iterator(), getMultiSelectDelimiter());
			}
		}

		if(quoteValue) {
			strValue = "\"" + strValue + "\"";
		}

		return new ExtendedChoiceParameterValue(getName(), strValue);
	}

	private boolean isMultiLevelParameterType() {
		return type.equals(PARAMETER_TYPE_MULTI_LEVEL_SINGLE_SELECT) || type.equals(PARAMETER_TYPE_MULTI_LEVEL_MULTI_SELECT);
	}

	private boolean isBasicParameterType() {
		return type.equals(PARAMETER_TYPE_SINGLE_SELECT) || type.equals(PARAMETER_TYPE_MULTI_SELECT) || type.equals(PARAMETER_TYPE_CHECK_BOX) || type.equals(PARAMETER_TYPE_RADIO) || type.equals(PARAMETER_TYPE_TEXT_BOX) || type.equals(PARAMETER_TYPE_HIDDEN);
	}

	@Override
	public ParameterValue getDefaultParameterValue() {
		if(isBasicParameterType()) {
			String defaultValue = computeEffectiveDefaultValue();
			if(!StringUtils.isBlank(defaultValue)) {
				if(quoteValue) {
					defaultValue = "\"" + defaultValue + "\"";
				}
				return new ExtendedChoiceParameterValue(getName(), defaultValue);
			}
		}
		else if(type.equals(PARAMETER_TYPE_JSON)) {
			Object test = getJSONEditorOptions();
			if(test instanceof LazyValueMap) {
				String defaultValue = Boon.toJson(((LazyValueMap) test).get("startval"));
				return new ExtendedChoiceParameterValue(getName(), defaultValue);
			}
		}
		return super.getDefaultParameterValue();
	}

	// note that computeValue is not called by multiLevel.jelly
	private String computeValue(String value, String propertyFilePath, String propertyKey, String groovyScript, String groovyScriptFile,
			String bindings, boolean isSingleValued, String jsonScript) {

		if(!StringUtils.isBlank(propertyFilePath) && !StringUtils.isBlank(propertyKey)) {
			try {
				String resolvedPropertyFilePath = expandVariables(propertyFilePath);
				File propertyFile = new File(resolvedPropertyFilePath);
				if(propertyFile.exists()) {
					Project project = new Project();
					Property property = new Property();
					property.setProject(project);
					property.setFile(propertyFile);
					property.execute();
					return project.getProperty(propertyKey);
				}
				else {
					Project project = new Project();
					Property property = new Property();
					property.setProject(project);
					URL propertyFileUrl = new URL(resolvedPropertyFilePath);
					property.setUrl(propertyFileUrl);
					property.execute();
					return project.getProperty(propertyKey);
				}
			}
			catch(Exception e) {
				LOGGER.log(Level.SEVERE, e.getMessage(), e);
			}
		}
		else if(!StringUtils.isBlank(jsonScript)) {
			Object jsonValue = Boon.fromJson(jsonScript);
			return processGroovyValue(isSingleValued, jsonValue);
		}
		else if(!StringUtils.isBlank(groovyScript)) {
			return executeGroovyScriptAndProcessGroovyValue(groovyScript, bindings, isSingleValued);
		}
		else if(!StringUtils.isBlank(groovyScriptFile)) {
			return executeGroovyScriptFile(groovyScriptFile, bindings, isSingleValued);
		}
		else if(!StringUtils.isBlank(value)) {
			return value;
		}
		return null;
	}

	private String executeGroovyScriptFile(String groovyScriptFile, String bindings, boolean isSingleValued) {
		String result = null;
		try {
			String groovyScript = loadGroovyScriptFile(groovyScriptFile);
			result = executeGroovyScriptAndProcessGroovyValue(groovyScript, bindings, isSingleValued);
		}
		catch(Exception e) {
			LOGGER.log(Level.SEVERE, e.getMessage(), e);
		}
		return result;

	}

	private String loadGroovyScriptFile(String groovyScriptFile) throws IOException {
		String resolvedGroovyScriptFile = expandVariables(groovyScriptFile);
		String groovyScript = Util.loadFile(new File(resolvedGroovyScriptFile));
		return groovyScript;
	}

	private String executeGroovyScriptAndProcessGroovyValue(String groovyScript, String bindings, boolean isSingleValued) {
		String result = null;
		try {
			Object groovyValue = executeGroovyScript(groovyScript, bindings);
			result = processGroovyValue(isSingleValued, groovyValue);
		}
		catch(Exception e) {
			LOGGER.log(Level.SEVERE, e.getMessage(), e);
		}
		return result;
	}

	private Object executeGroovyScript(String groovyScript, String bindings) throws URISyntaxException, IOException {
		if (groovyScriptResultStatus == null || groovyScriptResultStatus != ScriptResult.OK || groovyScriptResult == null) {
			try {
				Jenkins jenkins = Jenkins.getInstance();
				ClassLoader classLoader = jenkins.getPluginManager().uberClassLoader;
				SecureGroovyScript secureGroovyScript = new SecureGroovyScript(groovyScript, useGroovySandbox, null);
				secureGroovyScript.configuringWithNonKeyItem();
				Binding binding = this.generateBindings(bindings);
				groovyScriptResult = secureGroovyScript.evaluate(classLoader, binding);
				groovyScriptResultStatus = ScriptResult.OK;
			} catch (RejectedAccessException e) {
				groovyScriptResultStatus = ScriptResult.Unapproved;
			} catch (Exception e) {
				LOGGER.log(Level.SEVERE, e.getMessage(), e);
				groovyScriptResultStatus = ScriptResult.GeneralError;
			}
		}
		return groovyScriptResult;
	}

	private String processGroovyValue(boolean isSingleValued, Object groovyValue) {
		String value = null;
		if(groovyValue instanceof String[]) {
			String[] groovyValues = (String[])groovyValue;
			if(!isSingleValued) {
				value = StringUtils.join((String[])groovyValue, multiSelectDelimiter);
			}
			else if(groovyValues.length > 0) {
				value = groovyValues[0];
			}
		}
		else if(groovyValue instanceof List<?>) {
			List<?> groovyValues = (List<?>)groovyValue;
			if(!isSingleValued) {
				value = StringUtils.join(groovyValues, multiSelectDelimiter);
			}
			else if(!groovyValues.isEmpty()) {
				value = (String)groovyValues.get(0);
			}
		}
		else if(groovyValue instanceof String) {
			value = (String)groovyValue;
		}
		return value;
	}

	private Binding generateBindings(String bindings) throws IOException {
		Binding binding = new Binding();
		if(bindings != null) {
			Properties p = new Properties();
			p.load(new StringReader(bindings));
			for(Map.Entry<Object, Object> entry: p.entrySet()) {
				binding.setProperty((String)entry.getKey(), entry.getValue());
			}
		}
		return binding;
	}

	private String computeEffectiveValue() {
		return computeValue(value, propertyFile, propertyKey, groovyScript, groovyScriptFile, bindings, false, jsonScript);
	}

	private String computeEffectiveDefaultValue() {
		return computeValue(defaultValue, defaultPropertyFile, defaultPropertyKey, defaultGroovyScript, defaultGroovyScriptFile, defaultBindings, isSingleValuedParameterType(type), null);
	}

	private boolean isSingleValuedParameterType(String type) {
		return PARAMETER_TYPE_RADIO.equals(type) || PARAMETER_TYPE_SINGLE_SELECT.equals(type);
	}

	private String computeEffectiveDescriptionPropertyValue() {
		return computeValue(descriptionPropertyValue, descriptionPropertyFile, descriptionPropertyKey, descriptionGroovyScript, descriptionGroovyScriptFile, descriptionBindings, false, null);
	}

	@Override
	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
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

	public String getGroovyScript() {
		return groovyScript;
	}

	public void setGroovyScript(String groovyScript) {
		this.groovyScript = groovyScript;
	}

	public String getGroovyScriptFile() {
		return groovyScriptFile;
	}

	public void setGroovyScriptFile(String groovyScriptFile) {
		this.groovyScriptFile = groovyScriptFile;
	}

	public boolean isUseGroovySandbox() {
		return useGroovySandbox;
	}

	public void setUseGroovySandbox(boolean useGroovySandbox) {
		this.useGroovySandbox = useGroovySandbox;
	}

	public String getBindings() {
		return bindings;
	}

	public void setBindings(String bindings) {
		this.bindings = bindings;
	}

	public String getJsonScript() {
		return jsonScript;
	}

	public void setJsonScript(String jsonScript) {
		this.jsonScript = jsonScript;
	}

	public String getDefaultPropertyKey() {
		return defaultPropertyKey;
	}

	public void setDefaultPropertyKey(String defaultPropertyKey) {
		this.defaultPropertyKey = defaultPropertyKey;
	}

	private ArrayList<Integer> columnIndicesForDropDowns(String[] headerColumns) {
		ArrayList<Integer> columnIndicesForDropDowns = new ArrayList<>();

		String[] dropDownNames = value.split(",");

		for(String dropDownName: dropDownNames) {
			for(int i = 0; i < headerColumns.length; ++i) {
				if(headerColumns[i].equals(dropDownName)) {
					columnIndicesForDropDowns.add(i);
				}
			}
		}

		return columnIndicesForDropDowns;
	}

	Map<String, Set<String>> calculateChoicesByDropdownId() throws Exception {
		String resolvedPropertyFile = expandVariables(propertyFile);
		File file = new File(resolvedPropertyFile);
		List<String[]> fileLines = Collections.emptyList();
		if(file.isFile()) {
			CSVReader csvReader = null;
			try {
				csvReader = new CSVReader(new InputStreamReader(new FileInputStream(file), "UTF-8"), '\t');
				fileLines = csvReader.readAll();
			}
			finally {
				IOUtils.closeQuietly(csvReader);
			}
		}
		else {
			URL propertyFileUrl = new URL(resolvedPropertyFile);
			CSVReader csvReader = null;
			try {
				csvReader = new CSVReader(new InputStreamReader(propertyFileUrl.openStream(), "UTF-8"), '\t');
				fileLines = csvReader.readAll();
			}
			finally {
				IOUtils.closeQuietly(csvReader);
			}
		}

		if(fileLines.size() < 2) {
			throw new Exception("Multi level tab delimited file must have at least 2 " + "lines (one for the header, and one or more for the data)");
		}

		ArrayList<Integer> columnIndicesForDropDowns = columnIndicesForDropDowns(fileLines.get(0));

		List<String[]> dataLines = fileLines.subList(1, fileLines.size());

		Map<String, Set<String>> choicesByDropdownId = new LinkedHashMap<>();

		String prefix = getName() + " dropdown MultiLevelMultiSelect 0";
		choicesByDropdownId.put(prefix, new LinkedHashSet<String>());

		for(int i = 0; i < columnIndicesForDropDowns.size(); ++i) {
			String prettyCurrentColumnName = value.split(",")[i];
			prettyCurrentColumnName = prettyCurrentColumnName.toLowerCase();
			prettyCurrentColumnName = prettyCurrentColumnName.replace("_", " ");

			for(String[] dataLine: dataLines) {
				StringBuilder priorLevelDropdownIdBuilder = new StringBuilder(prefix);
				StringBuilder currentLevelDropdownIdBuilder = new StringBuilder(prefix);

				int column = 0;
				for(int j = 0; j <= i; ++j) {
					column = columnIndicesForDropDowns.get(j);

					if(j < i) {
						priorLevelDropdownIdBuilder.append(" ");
						priorLevelDropdownIdBuilder.append(dataLine[column]);
					}
					currentLevelDropdownIdBuilder.append(" ");
					currentLevelDropdownIdBuilder.append(dataLine[column]);
				}
				if(i != columnIndicesForDropDowns.size() - 1) {
					choicesByDropdownId.put(currentLevelDropdownIdBuilder.toString(), new LinkedHashSet<String>());
				}
				Set<String> choicesForPriorDropdown = choicesByDropdownId.get(priorLevelDropdownIdBuilder.toString());
				choicesForPriorDropdown.add("Select a " + prettyCurrentColumnName + "...");
				choicesForPriorDropdown.add(dataLine[column]);
			}
		}

		return choicesByDropdownId;
	}

	public String getMultiLevelDropdownIds() throws Exception {
		StringBuilder dropdownIdsBuilder = new StringBuilder();

		Map<String, Set<String>> choicesByDropdownId = calculateChoicesByDropdownId();

		for(String id: choicesByDropdownId.keySet()) {
			if(dropdownIdsBuilder.length() > 0) {
				dropdownIdsBuilder.append(",");
			}
			dropdownIdsBuilder.append(id);
		}

		return dropdownIdsBuilder.toString();

		/* dropdownIds is of a form like this:
		return name + " dropdown MultiLevelMultiSelect 0,"
				   // next select the source of the genome -- each genome gets a separate dropdown id:"
				 + name + " dropdown MultiLevelMultiSelect 0 HG18,dropdown MultiLevelMultiSelect 0 ZZ23,"
				 // next select the cell type of the source -- each source gets a separate dropdown id
				 + name + " dropdown MultiLevelMultiSelect 0 HG18 Diffuse large B-cell lymphoma, dropdown MultiLevelMultiSelect 0 HG18 Multiple Myeloma,"
				 + name + " dropdown MultiLevelMultiSelect 0 ZZ23 Neuroblastoma,"
				 // next select the name from the cell type -- each cell type gets a separate dropdown id
				 + name + " dropdown MultiLevelMultiSelect 0 HG18 Diffuse large B-cell lymphoma LY1,"
				 + name + " dropdown MultiLevelMultiSelect 0 HG18 Multiple Myeloma MM1S,"
				 + name + " dropdown MultiLevelMultiSelect 0 ZZ23 Neuroblastoma BE2C,"
				 + name + " dropdown MultiLevelMultiSelect 0 ZZ23 Neuroblastoma SKNAS";*/
	}

	public Map<String, String> getChoicesByDropdownId() throws Exception {
		Map<String, Set<String>> choicesByDropdownId = calculateChoicesByDropdownId();

		Map<String, String> collapsedMap = new LinkedHashMap<>();

		for(Map.Entry<String, Set<String>> dropdownIdEntry: choicesByDropdownId.entrySet()) {
			StringBuilder choicesBuilder = new StringBuilder();
			for(String choice: dropdownIdEntry.getValue()) {
				if(choicesBuilder.length() > 0) {
					choicesBuilder.append(",");
				}
				choicesBuilder.append(choice);
			}

			collapsedMap.put(dropdownIdEntry.getKey(), choicesBuilder.toString());
		}

		/* collapsedMap is of a form like this:
		collapsedMap.put(name + " dropdown MultiLevelMultiSelect 0", "Select a genome...,HG18,ZZ23");
		collapsedMap.put(name + " dropdown MultiLevelMultiSelect 0 HG18", "Select a source...,Diffuse large B-cell lymphoma,Multiple Myeloma");
		collapsedMap.put(name + " dropdown MultiLevelMultiSelect 0 ZZ23", "Select a source...,Neuroblastoma");
		collapsedMap.put(name + " dropdown MultiLevelMultiSelect 0 HG18 Diffuse large B-cell lymphoma","Select a cell type...,LY1");
		collapsedMap.put(name + " dropdown MultiLevelMultiSelect 0 HG18 Multiple Myeloma","Select a cell type...,MM1S");
		collapsedMap.put(name + " dropdown MultiLevelMultiSelect 0 ZZ23 Neuroblastoma","Select a cell type...,BE2C,SKNAS");
		collapsedMap.put(name + " dropdown MultiLevelMultiSelect 0 HG18 Diffuse large B-cell lymphoma LY1","Select a name...,LY1_BCL6_DMSO,LY1_BCL6_JQ1");
		collapsedMap.put(name + " dropdown MultiLevelMultiSelect 0 HG18 Multiple Myeloma MM1S", "Select a name...,MM1S_BRD4_150nM_JQ1,MM1S_BRD4_500nM_JQ1");
		collapsedMap.put(name + " dropdown MultiLevelMultiSelect 0 ZZ23 Neuroblastoma BE2C", "Select a name...,BE2C_BRD4");
		collapsedMap.put(name + " dropdown MultiLevelMultiSelect 0 ZZ23 Neuroblastoma SKNAS", "Select a name...,SKNAS_H3K4ME3");
		*/

		return collapsedMap;
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

	public String getDefaultGroovyScript() {
		return defaultGroovyScript;
	}

	public void setDefaultGroovyScript(String defaultGroovyScript) {
		this.defaultGroovyScript = defaultGroovyScript;
	}

	public String getDefaultGroovyScriptFile() {
		return defaultGroovyScriptFile;
	}

	public void setDefaultGroovyScriptFile(String defaultGroovyScriptFile) {
		this.defaultGroovyScriptFile = defaultGroovyScriptFile;
	}

	public String getDefaultBindings() {
		return defaultBindings;
	}

	public void setDefaultBindings(String defaultBindings) {
		this.defaultBindings = defaultBindings;
	}

	@Deprecated
	public String getGroovyClasspath() {
		return null;
	}

	@Deprecated
	public void setGroovyClasspath(String groovyClasspath) { }

	@Deprecated
	public String getDefaultGroovyClasspath() {
		return null;
	}

	@Deprecated
	public void setDefaultGroovyClasspath(String defaultGroovyClasspath) { }

	public String getDescriptionPropertyValue() {
		return descriptionPropertyValue;
	}

	public void setDescriptionPropertyValue(String descriptionPropertyValue) {
		this.descriptionPropertyValue = descriptionPropertyValue;
	}

	public String getDescriptionPropertyFile() {
		return descriptionPropertyFile;
	}

	public void setDescriptionPropertyFile(String descriptionPropertyFile) {
		this.descriptionPropertyFile = descriptionPropertyFile;
	}

	public String getDescriptionGroovyScript() {
		return descriptionGroovyScript;
	}

	public void setDescriptionGroovyScript(String descriptionGroovyScript) {
		this.descriptionGroovyScript = descriptionGroovyScript;
	}

	public String getDescriptionGroovyScriptFile() {
		return descriptionGroovyScriptFile;
	}

	public void setDescriptionGroovyScriptFile(String descriptionGroovyScriptFile) {
		this.descriptionGroovyScriptFile = descriptionGroovyScriptFile;
	}

	public String getDescriptionBindings() {
		return descriptionBindings;
	}

	public void setDescriptionBindings(String descriptionBindings) {
		this.descriptionBindings = descriptionBindings;
	}

	@Deprecated
	public String getDescriptionGroovyClasspath() {
		return null;
	}

	@Deprecated
	public void setDescriptionGroovyClasspath(String descriptionGroovyClasspath) { }

	public String getDescriptionPropertyKey() {
		return descriptionPropertyKey;
	}

	public void setDescriptionPropertyKey(String descriptionPropertyKey) {
		this.descriptionPropertyKey = descriptionPropertyKey;
	}

	public String getJavascriptFile() {
		return javascriptFile;
	}

	public void setJavascriptFile(String javascriptFile) {
		this.javascriptFile = javascriptFile;
	}

	public String getJavascript() {
		return javascript;
	}

	public void setJavascript(String javascript) {
		this.javascript = javascript;
	}

	public boolean isSaveJSONParameterToFile() {
		return saveJSONParameterToFile;
	}

	public void setSaveJSONParameterToFile(boolean saveJSONParameterToFile) {
		this.saveJSONParameterToFile = saveJSONParameterToFile;
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

	public String getProjectName() {
		return projectName;
	}

	public void setProjectName(String projectName) {
		this.projectName = projectName;
	}

	public boolean hasUnapprovedScripts() {
		if (groovyScriptResultStatus != ScriptResult.OK) {
			try {
				if (!StringUtils.isBlank(jsonScript)) {
					return false;
				} else if (!StringUtils.isBlank(groovyScript)) {
					executeGroovyScript(groovyScript, bindings);
				} else if (!StringUtils.isBlank(groovyScriptFile)) {
					String script = Util.loadFile(new File(expandVariables(groovyScriptFile)));
					executeGroovyScript(script, bindings);
				}
			} catch(IOException | URISyntaxException e) {
				LOGGER.log(Level.SEVERE, e.getMessage(), e);
			}
		}

		return groovyScriptResultStatus == ScriptResult.Unapproved;
	}

	public ParameterDefinitionInfo getParameterDefinitionInfo() {
		ParameterDefinitionInfo result = new ParameterDefinitionInfo();

		String effectiveValue = computeEffectiveValue();
		Map<String, Boolean> defaultValueMap = computeDefaultValueMap();
		Map<String, String> descriptionPropertyValueMap = computeDescriptionPropertyValueMap(effectiveValue);

		result.setEffectiveValue(effectiveValue);
		result.setDefaultValueMap(defaultValueMap);
		result.setDescriptionPropertyValueMap(descriptionPropertyValueMap);

		return result;
	}

	public String getEffectiveDefaultValue() {
		return computeEffectiveDefaultValue();
	}

	public String getJSONEditorScript() {
		String result = null;
		try {
			if(!StringUtils.isBlank(javascript)) {
				result = javascript;
			}
			else if(!StringUtils.isBlank(javascriptFile)) {
				result = Util.loadFile(new File(expandVariables(javascriptFile)));
			}
		}
		catch(IOException e) {
			LOGGER.log(Level.SEVERE, e.getMessage(), e);
		}
		return result;
	}

	public Object getJSONEditorOptions() {
		Object result = null;
		if (!StringUtils.isBlank(jsonScript)) {
			return Boon.fromJson(jsonScript);
		}
		try {
			String script = null;
			if(!StringUtils.isBlank(groovyScript)) {
				script = groovyScript;
			}
			else {
				script = Util.loadFile(new File(expandVariables(groovyScriptFile)));
			}

			result = executeGroovyScript(script, bindings);
		}
		catch(IOException | URISyntaxException e) {
			LOGGER.log(Level.SEVERE, e.getMessage(), e);
		}
		return result;
	}

	private String expandVariables(String input) {
		String result = input;
		if(input != null) {
			Jenkins instance = Jenkins.getInstance();
			AbstractProject<?, ?> project = (AbstractProject<?, ?>)(projectName != null && instance != null ? instance.getItem(projectName) : null);
			if(project != null) {
				EnvVars envVars;
				try {
					envVars = project.getEnvironment(null, new LogTaskListener(LOGGER, Level.SEVERE));
					User user = User.current();
					if(user != null) {
						String userId = user.getId();
						envVars.put("USER_ID", userId);
					}
					result = Util.replaceMacro(input, envVars);
				}
				catch(IOException | InterruptedException e) {
					LOGGER.log(Level.SEVERE, e.getMessage(), e);
				}
			}
		}
		return result;
	}

}
