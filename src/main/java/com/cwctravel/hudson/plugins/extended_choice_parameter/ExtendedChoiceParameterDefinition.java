/*
 *Copyright (c) 2013 Costco, Vimil Saju
 *Copyright (c) 2013 John DiMatteo
 *See the file license.txt for copying permission.
 */

package com.cwctravel.hudson.plugins.extended_choice_parameter;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringReader;
import java.lang.reflect.Field;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.ServletException;

import org.acegisecurity.Authentication;
import org.acegisecurity.context.SecurityContextHolder;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.Property;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.jenkinsci.plugins.scriptsecurity.scripts.ApprovalContext;
import org.jenkinsci.plugins.scriptsecurity.scripts.ClasspathEntry;
import org.jenkinsci.plugins.scriptsecurity.scripts.ScriptApproval;
import org.jenkinsci.plugins.scriptsecurity.scripts.UnapprovedClasspathException;
import org.jenkinsci.plugins.scriptsecurity.scripts.UnapprovedUsageException;
import org.jenkinsci.plugins.scriptsecurity.scripts.languages.GroovyLanguage;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;

import au.com.bytecode.opencsv.CSVReader;
import groovy.lang.Binding;
import groovy.lang.GroovyCodeSource;
import groovy.lang.GroovyShell;
import hudson.EnvVars;
import hudson.Extension;
import hudson.Util;
import hudson.cli.CLICommand;
import hudson.model.AbstractProject;
import hudson.model.ParameterDefinition;
import hudson.model.ParameterValue;
import hudson.model.User;
import hudson.util.DirScanner;
import hudson.util.FileVisitor;
import hudson.util.FormValidation;
import hudson.util.LogTaskListener;
import jenkins.model.Jenkins;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

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

	private transient GroovyShell groovyShell;

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
			String bindings = null;
			String groovyClasspath = null;
			String javascriptFile = null;
			String javascript = null;

			String defaultPropertyValue = null;
			String defaultPropertyKey = null;
			String defaultPropertyFile = null;
			String defaultGroovyScript = null;
			String defaultGroovyScriptFile = null;
			String defaultBindings = null;
			String defaultGroovyClasspath = null;

			String descriptionPropertyValue = null;
			String descriptionPropertyKey = null;
			String descriptionPropertyFile = null;
			String descriptionGroovyScript = null;
			String descriptionGroovyScriptFile = null;
			String descriptionBindings = null;
			String descriptionGroovyClasspath = null;
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
							groovyClasspath = propertySourceJSON.getString("groovyClasspath");
						}
						else if(propertySourceJSON.getInt("value") == 3) {
							groovyScriptFile = propertySourceJSON.getString("groovyScriptFile");
							bindings = propertySourceJSON.getString("bindings");
							groovyClasspath = propertySourceJSON.getString("groovyClasspath");
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
							defaultGroovyClasspath = defaultPropertySourceJSON.getString("defaultGroovyClasspath");
						}
						else if(defaultPropertySourceJSON.getInt("value") == 3) {
							defaultGroovyScriptFile = defaultPropertySourceJSON.getString("defaultGroovyScriptFile");
							defaultBindings = defaultPropertySourceJSON.getString("defaultBindings");
							defaultGroovyClasspath = defaultPropertySourceJSON.getString("defaultGroovyClasspath");
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
							descriptionGroovyClasspath = descriptionPropertySourceJSON.getString("descriptionGroovyClasspath");
						}
						else if(descriptionPropertySourceJSON.getInt("value") == 3) {
							descriptionGroovyScriptFile = descriptionPropertySourceJSON.getString("descriptionGroovyScriptFile");
							descriptionBindings = descriptionPropertySourceJSON.getString("descriptionBindings");
							descriptionGroovyClasspath = descriptionPropertySourceJSON.getString("descriptionGroovyClasspath");
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
							groovyScript = jsonParameterConfigSourceJSON.getString("groovyScript");
							groovyScriptFile = null;
							bindings = jsonParameterConfigSourceJSON.getString("bindings");
							groovyClasspath = jsonParameterConfigSourceJSON.getString("groovyClasspath");
						}
						else if(jsonParameterConfigSourceJSON.getInt("value") == 1) {
							groovyScript = null;
							groovyScriptFile = jsonParameterConfigSourceJSON.getString("groovyScriptFile");
							bindings = jsonParameterConfigSourceJSON.getString("bindings");
							groovyClasspath = jsonParameterConfigSourceJSON.getString("groovyClasspath");
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
														bindings, 
														groovyClasspath, 
														propertyKey, 
														defaultPropertyValue, 
														defaultPropertyFile, 
														defaultGroovyScript, 
														defaultGroovyScriptFile, 
														defaultBindings, 
														defaultGroovyClasspath, 
														defaultPropertyKey, 
														descriptionPropertyValue, 
														descriptionPropertyFile, 
														descriptionGroovyScript, 
														descriptionGroovyScriptFile, 
														descriptionBindings, 
														descriptionGroovyClasspath, 
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

	private String bindings;

	private String groovyClasspath;

	private String propertyKey;

	private String defaultValue;

	private String defaultPropertyFile;

	private String defaultGroovyScript;

	private String defaultGroovyScriptFile;

	private String defaultBindings;

	private String defaultGroovyClasspath;

	private String defaultPropertyKey;

	private String multiSelectDelimiter;

	private String descriptionPropertyValue;

	private String descriptionPropertyFile;

	private String descriptionGroovyScript;

	private String descriptionGroovyScriptFile;

	private String descriptionBindings;

	private String descriptionGroovyClasspath;

	private String descriptionPropertyKey;

	private String javascriptFile;

	private String javascript;

	private String projectName;

	//@formatter:off
	@DataBoundConstructor
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
	//@formatter:on

		super(name, description);

		this.type = type;
		this.value = value;
		this.projectName = projectName;
		this.propertyFile = propertyFile;
		this.propertyKey = propertyKey;
		this.groovyScript = groovyScript;
		this.groovyScriptFile = groovyScriptFile;
		this.bindings = bindings;
		this.groovyClasspath = groovyClasspath;

		this.defaultValue = defaultValue;
		this.defaultPropertyFile = defaultPropertyFile;
		this.defaultPropertyKey = defaultPropertyKey;
		this.defaultGroovyScript = defaultGroovyScript;
		this.defaultGroovyScriptFile = defaultGroovyScriptFile;
		this.defaultBindings = defaultBindings;
		this.defaultGroovyClasspath = defaultGroovyClasspath;

		this.descriptionPropertyValue = descriptionPropertyValue;
		this.descriptionPropertyFile = descriptionPropertyFile;
		this.descriptionPropertyKey = descriptionPropertyKey;
		this.descriptionGroovyScript = descriptionGroovyScript;
		this.descriptionGroovyScriptFile = descriptionGroovyScriptFile;
		this.descriptionBindings = descriptionBindings;
		this.descriptionGroovyClasspath = descriptionGroovyClasspath;
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
		return super.getDefaultParameterValue();
	}

	// note that computeValue is not called by multiLevel.jelly
	private String computeValue(String value, String propertyFilePath, String propertyKey, String groovyScript, String groovyScriptFile,
			String bindings, String groovyClasspath, boolean isSingleValued) {

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
		else if(!StringUtils.isBlank(groovyScript)) {
			return executeGroovyScriptAndProcessGroovyValue(groovyScript, bindings, groovyClasspath, isSingleValued);
		}
		else if(!StringUtils.isBlank(groovyScriptFile)) {
			return executeGroovyScriptFile(groovyScriptFile, bindings, groovyClasspath, isSingleValued);
		}
		else if(!StringUtils.isBlank(value)) {
			return value;
		}
		return null;
	}

	private String executeGroovyScriptFile(String groovyScriptFile, String bindings, String groovyClasspath, boolean isSingleValued) {
		String result = null;
		try {
			String groovyScript = loadGroovyScriptFile(groovyScriptFile);
			result = executeGroovyScriptAndProcessGroovyValue(groovyScript, bindings, groovyClasspath, isSingleValued);
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

	private String executeGroovyScriptAndProcessGroovyValue(String groovyScript, String bindings, String groovyClasspath, boolean isSingleValued) {
		String result = null;
		try {
			Object groovyValue = executeGroovyScript(groovyScript, bindings, groovyClasspath);
			result = processGroovyValue(isSingleValued, groovyValue);

		}
		catch(Exception e) {
			LOGGER.log(Level.SEVERE, e.getMessage(), e);
		}
		return result;
	}

	private Object executeGroovyScript(String groovyScript, String bindings, String groovyClasspath) throws URISyntaxException, IOException {
		Object groovyValue = null;

		if(checkScriptApproval(groovyScript, groovyClasspath, false)) {
			GroovyShell groovyShell = getGroovyShell(groovyClasspath);
			GroovyCodeSource codeSource = new GroovyCodeSource(groovyScript, computeMD5Hash(groovyScript), "/groovy/shell");
			groovyShell.getClassLoader().parseClass(codeSource, true);
			setBindings(groovyShell, bindings);
			groovyValue = groovyShell.evaluate(codeSource);
		}

		return groovyValue;
	}

	private String computeMD5Hash(String str) {
		String result = str;
		if(str != null) {
			result = DigestUtils.md5Hex(str);
		}
		result = "_" + result;
		return result;
	}

	private Binding getGroovyBinding() {
		Binding groovyBinding = null;

		StaplerRequest currentRequest = Stapler.getCurrentRequest();
		if(currentRequest != null) {
			groovyBinding = (Binding)currentRequest.getAttribute(ATTR_REQUEST_GROOVY_BINDING);
			if(groovyBinding == null) {
				groovyBinding = new Binding();
				currentRequest.setAttribute(ATTR_REQUEST_GROOVY_BINDING, groovyBinding);
			}
		}
		else {
			groovyBinding = new Binding();
		}
		return groovyBinding;
	}

	private synchronized GroovyShell getGroovyShell(String groovyClasspath) {
		if(groovyShell == null) {
			Jenkins jenkins = Jenkins.getInstance();
			if(jenkins != null) {
				ClassLoader cl = jenkins.getPluginManager().uberClassLoader;

				if(cl == null) {
					cl = Thread.currentThread().getContextClassLoader();
				}

				CompilerConfiguration compilerConfiguration = new CompilerConfiguration();
				if(!StringUtils.isBlank(groovyClasspath)) {
					compilerConfiguration.setClasspath(groovyClasspath);
				}

				Binding groovyBinding = getGroovyBinding();
				groovyShell = new GroovyShell(cl, groovyBinding, compilerConfiguration);
			}
		}
		else {
			if(!StringUtils.isBlank(groovyClasspath)) {
				String[] groovyClasspathElements = groovyClasspath.split(";");
				for(String groovyClasspathElement: groovyClasspathElements) {
					groovyShell.getClassLoader().addClasspath(groovyClasspathElement);
				}
			}

			try {
				Binding groovyBinding = getGroovyBinding();
				Field contextField = groovyShell.getClass().getDeclaredField("context");
				contextField.setAccessible(true);
				contextField.set(groovyShell, groovyBinding);
			}
			catch(NoSuchFieldException e) {
				LOGGER.log(Level.SEVERE, e.getMessage(), e);
			}
			catch(SecurityException e) {
				LOGGER.log(Level.SEVERE, e.getMessage(), e);
			}
			catch(IllegalArgumentException e) {
				LOGGER.log(Level.SEVERE, e.getMessage(), e);
			}
			catch(IllegalAccessException e) {
				LOGGER.log(Level.SEVERE, e.getMessage(), e);
			}

		}

		return groovyShell;
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

	private void setBindings(GroovyShell shell, String bindings) throws IOException {
		if(bindings != null) {
			Properties p = new Properties();
			p.load(new StringReader(bindings));
			for(Map.Entry<Object, Object> entry: p.entrySet()) {
				shell.setVariable((String)entry.getKey(), entry.getValue());
			}
		}

		Jenkins instance = Jenkins.getInstance();
		shell.setProperty("jenkins", instance);

		if(projectName != null && instance != null) {
			AbstractProject<?, ?> project = (AbstractProject<?, ?>)instance.getItem(projectName);
			shell.setProperty("currentProject", project);
		}
	}

	private String computeEffectiveValue() {
		return computeValue(value, propertyFile, propertyKey, groovyScript, groovyScriptFile, bindings, groovyClasspath, false);
	}

	private String computeEffectiveDefaultValue() {
		return computeValue(defaultValue, defaultPropertyFile, defaultPropertyKey, defaultGroovyScript, defaultGroovyScriptFile, defaultBindings, defaultGroovyClasspath, isSingleValuedParameterType(type));
	}

	private boolean isSingleValuedParameterType(String type) {
		return PARAMETER_TYPE_RADIO.equals(type) || PARAMETER_TYPE_SINGLE_SELECT.equals(type);
	}

	private String computeEffectiveDescriptionPropertyValue() {
		return computeValue(descriptionPropertyValue, descriptionPropertyFile, descriptionPropertyKey, descriptionGroovyScript, descriptionGroovyScriptFile, descriptionBindings, descriptionGroovyClasspath, false);
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

	public String getBindings() {
		return bindings;
	}

	public void setBindings(String bindings) {
		this.bindings = bindings;
	}

	public String getDefaultPropertyKey() {
		return defaultPropertyKey;
	}

	public void setDefaultPropertyKey(String defaultPropertyKey) {
		this.defaultPropertyKey = defaultPropertyKey;
	}

	private ArrayList<Integer> columnIndicesForDropDowns(String[] headerColumns) {
		ArrayList<Integer> columnIndicesForDropDowns = new ArrayList<Integer>();

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

		Map<String, Set<String>> choicesByDropdownId = new LinkedHashMap<String, Set<String>>();

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
				   // next select the source of the genome -- each genome gets a seperate dropdown id:"
				 + name + " dropdown MultiLevelMultiSelect 0 HG18,dropdown MultiLevelMultiSelect 0 ZZ23,"
				 // next select the cell type of the source -- each source gets a seperate dropdown id
				 + name + " dropdown MultiLevelMultiSelect 0 HG18 Diffuse large B-cell lymphoma, dropdown MultiLevelMultiSelect 0 HG18 Multiple Myeloma,"
				 + name + " dropdown MultiLevelMultiSelect 0 ZZ23 Neuroblastoma,"
				 // next select the name from the cell type -- each cell type gets a seperate dropdown id
				 + name + " dropdown MultiLevelMultiSelect 0 HG18 Diffuse large B-cell lymphoma LY1,"
				 + name + " dropdown MultiLevelMultiSelect 0 HG18 Multiple Myeloma MM1S,"
				 + name + " dropdown MultiLevelMultiSelect 0 ZZ23 Neuroblastoma BE2C,"
				 + name + " dropdown MultiLevelMultiSelect 0 ZZ23 Neuroblastoma SKNAS";*/
	}

	public Map<String, String> getChoicesByDropdownId() throws Exception {
		Map<String, Set<String>> choicesByDropdownId = calculateChoicesByDropdownId();

		Map<String, String> collapsedMap = new LinkedHashMap<String, String>();

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

	public String getGroovyClasspath() {
		return groovyClasspath;
	}

	public void setGroovyClasspath(String groovyClasspath) {
		this.groovyClasspath = groovyClasspath;
	}

	public String getDefaultGroovyClasspath() {
		return defaultGroovyClasspath;
	}

	public void setDefaultGroovyClasspath(String defaultGroovyClasspath) {
		this.defaultGroovyClasspath = defaultGroovyClasspath;
	}

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

	public String getDescriptionGroovyClasspath() {
		return descriptionGroovyClasspath;
	}

	public void setDescriptionGroovyClasspath(String descriptionGroovyClasspath) {
		this.descriptionGroovyClasspath = descriptionGroovyClasspath;
	}

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
		boolean result = false;
		try {
			if(!StringUtils.isBlank(groovyScript)) {
				result = !checkScriptApproval(groovyScript, groovyClasspath, true);
			}
			else if(!StringUtils.isBlank(groovyScriptFile)) {
				String script = Util.loadFile(new File(expandVariables(groovyScriptFile)));
				result = !checkScriptApproval(script, groovyClasspath, true);
			}

			if(!StringUtils.isBlank(defaultGroovyScript)) {
				result = !checkScriptApproval(defaultGroovyScript, defaultGroovyClasspath, true);
			}
			else if(!StringUtils.isBlank(defaultGroovyScriptFile)) {
				String script = Util.loadFile(new File(expandVariables(defaultGroovyScriptFile)));
				result = !checkScriptApproval(script, defaultGroovyClasspath, true);
			}

			if(!StringUtils.isBlank(descriptionGroovyScript)) {
				result = !checkScriptApproval(descriptionGroovyScript, descriptionGroovyClasspath, true);
			}
			else if(!StringUtils.isBlank(descriptionGroovyScriptFile)) {
				String script = Util.loadFile(new File(expandVariables(descriptionGroovyScriptFile)));
				result = !checkScriptApproval(script, descriptionGroovyClasspath, true);
			}
		}
		catch(IOException e) {
			LOGGER.log(Level.SEVERE, e.getMessage(), e);
		}
		catch(URISyntaxException e) {
			LOGGER.log(Level.SEVERE, e.getMessage(), e);
		}
		return result;
	}

	private boolean checkScriptApproval(String groovyScript, String groovyClasspath,
			boolean impersonateAnonymousUser) throws IOException, URISyntaxException, MalformedURLException {
		boolean result = true;
		Authentication authentication = Jenkins.getAuthentication();
		try {
			ScriptApproval scriptApproval = ScriptApproval.get();

			Jenkins instance = Jenkins.getInstance();
			AbstractProject<?, ?> project = (AbstractProject<?, ?>)(projectName != null && instance != null ? instance.getItem(projectName) : null);

			if(impersonateAnonymousUser) {
				SecurityContextHolder.getContext().setAuthentication(Jenkins.ANONYMOUS);
			}

			try {
				scriptApproval.configuring(groovyScript, GroovyLanguage.get(), ApprovalContext.create());
				scriptApproval.using(groovyScript, GroovyLanguage.get());
			}
			catch(UnapprovedUsageException uUE) {
				result = false;
			}
			catch(UnapprovedClasspathException uCE) {
				result = false;
			}

			List<ClasspathEntry> classpathEntries = parseClasspath(groovyClasspath);
			for(ClasspathEntry classpathEntry: classpathEntries) {
				if(classpathEntry.isClassDirectory()) {
					ClasspathEntry classpathDirDigestEntry = createClasspathDirDigest(project, classpathEntry);
					if(classpathDirDigestEntry != null) {
						try {
							scriptApproval.using(classpathDirDigestEntry);
						}
						catch(UnapprovedUsageException uUE) {
							result = false;
						}
						catch(UnapprovedClasspathException uCE) {
							result = false;
						}
					}
					else {
						result = false;
					}
				}
				else {
					try {
						scriptApproval.using(classpathEntry);
					}
					catch(UnapprovedUsageException uUE) {
						result = false;
					}
					catch(UnapprovedClasspathException uCE) {
						result = false;
					}
				}
			}
		}
		finally {
			if(impersonateAnonymousUser) {
				SecurityContextHolder.getContext().setAuthentication(authentication);
			}
		}
		return result;
	}

	private ClasspathEntry createClasspathDirDigest(AbstractProject<?, ?> project,
			ClasspathEntry classpathEntry) throws URISyntaxException, IOException {
		ClasspathEntry result = null;

		if(project != null) {
			URI classpathEntryURI = classpathEntry.getURL().toURI();
			File dirFile = new File(classpathEntryURI);
			final int[] fileCountHolder = new int[1];
			final List<Object[]> files = new ArrayList<Object[]>();
			new DirScanner.Full().scan(dirFile, new FileVisitor() {
				@Override
				public void visit(File file, String relativePath) throws IOException {
					if(file.isFile()) {
						fileCountHolder[0]++;
						if(fileCountHolder[0] <= 500) {
							files.add(new Object[] {file, relativePath});
						}
						else {
							throw new IOException("too many files in directory");
						}
					}
				}
			});

			Collections.sort(files, new Comparator<Object[]>() {
				@Override
				public int compare(Object[] o1, Object[] o2) {
					String relativePath1 = (String)o1[1];
					String relativePath2 = (String)o2[1];
					return relativePath1.compareTo(relativePath2);
				}
			});

			File digestFile = createDigest(project, classpathEntryURI, files);
			result = new ClasspathEntry(digestFile.toURI().toString());
		}
		return result;
	}

	private File createDigest(AbstractProject<?, ?> project, URI classpathEntryURI, List<Object[]> fileInfos) throws IOException {
		String classpathEntryStr = classpathEntryURI.toString();
		String digestFileName = getName() + computeMD5Hash(classpathEntryStr) + ".dig";
		File digestFile = new File(project.getRootDir(), digestFileName);
		PrintWriter pW = new PrintWriter(digestFile, "UTF-8");
		try {
			pW.println(classpathEntryStr);
			for(Object[] fileInfo: fileInfos) {
				File file = (File)fileInfo[0];
				String relativePath = (String)fileInfo[1];
				String fileHash = hashFile(file);
				pW.println(relativePath + "::" + fileHash);
			}
		}
		finally {
			pW.close();
		}
		return digestFile;
	}

	private String hashFile(File file) throws IOException {
		InputStream is = new FileInputStream(file);
		try {
			DigestInputStream input = null;
			try {
				MessageDigest digest = MessageDigest.getInstance("SHA-1");
				input = new DigestInputStream(new BufferedInputStream(is), digest);
				byte[] buffer = new byte[1024];
				while(input.read(buffer) != -1);
				return Util.toHexString(digest.digest());
			}
			catch(NoSuchAlgorithmException x) {
				throw new AssertionError(x);
			}
			finally {
				if(input != null) {
					input.close();
				}
			}
		}
		finally {
			is.close();
		}
	}

	private List<ClasspathEntry> parseClasspath(String groovyClasspath) throws MalformedURLException {
		List<ClasspathEntry> result = new ArrayList<ClasspathEntry>();
		if(!StringUtils.isEmpty(groovyClasspath)) {
			String[] classpathUrls = groovyClasspath.split(";");
			for(String classpathUrl: classpathUrls) {
				ClasspathEntry classpathEntry = new ClasspathEntry(classpathUrl);
				result.add(classpathEntry);
			}
		}
		return result;
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
		try {
			String script = null;
			if(!StringUtils.isBlank(groovyScript)) {
				script = groovyScript;
			}
			else {
				script = Util.loadFile(new File(expandVariables(groovyScriptFile)));
			}

			result = executeGroovyScript(script, bindings, groovyClasspath);
		}
		catch(IOException e) {
			LOGGER.log(Level.SEVERE, e.getMessage(), e);
		}
		catch(URISyntaxException e) {
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
				catch(IOException e) {
					LOGGER.log(Level.SEVERE, e.getMessage(), e);
				}
				catch(InterruptedException e) {
					LOGGER.log(Level.SEVERE, e.getMessage(), e);
				}
			}
		}
		return result;
	}

}
