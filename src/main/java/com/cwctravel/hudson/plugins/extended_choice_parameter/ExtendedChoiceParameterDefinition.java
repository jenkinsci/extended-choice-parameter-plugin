/*
 *Copyright (c) 2013 Costco, Vimil Saju
 *Copyright (c) 2013 John DiMatteo
 *See the file license.txt for copying permission.
 */

package com.cwctravel.hudson.plugins.extended_choice_parameter;

import groovy.lang.GroovyShell;
import hudson.Extension;
import hudson.Util;
import hudson.cli.CLICommand;
import hudson.model.ParameterValue;
import hudson.model.ParameterDefinition;
import hudson.util.FormValidation;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import javax.servlet.ServletException;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import org.apache.commons.lang.StringUtils;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.Property;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import au.com.bytecode.opencsv.CSVReader;

public class ExtendedChoiceParameterDefinition extends ParameterDefinition {
	private static final long serialVersionUID = -2946187268529865645L;

	public static final String PARAMETER_TYPE_SINGLE_SELECT = "PT_SINGLE_SELECT";

	public static final String PARAMETER_TYPE_MULTI_SELECT = "PT_MULTI_SELECT";

	public static final String PARAMETER_TYPE_CHECK_BOX = "PT_CHECKBOX";

	public static final String PARAMETER_TYPE_RADIO = "PT_RADIO";

	public static final String PARAMETER_TYPE_TEXT_BOX = "PT_TEXTBOX";

	public static final String PARAMETER_TYPE_MULTI_LEVEL_SINGLE_SELECT = "PT_MULTI_LEVEL_SINGLE_SELECT";

	public static final String PARAMETER_TYPE_MULTI_LEVEL_MULTI_SELECT = "PT_MULTI_LEVEL_MULTI_SELECT";

	@Extension
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
			catch(Exception e) {
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
			int visibleItemCount = 5;

			String propertyValue = null;
			String propertyKey = null;
			String propertyFile = null;
			String groovyScript = null;
			String groovyScriptFile = null;
			String bindings = null;

			String defaultPropertyValue = null;
			String defaultPropertyKey = null;
			String defaultPropertyFile = null;
			String defaultGroovyScript = null;
			String defaultGroovyScriptFile = null;
			String defaultBindings = null;

			name = formData.getString("name");
			description = formData.getString("description");

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
						}
						else if(propertySourceJSON.getInt("value") == 3) {
							groovyScriptFile = propertySourceJSON.getString("groovyScriptFile");
							bindings = propertySourceJSON.getString("bindings");
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
				}
				else if(value == 1) {
					type = parameterGroup.getString("type");
					propertyFile = parameterGroup.getString("propertyFile");
					propertyValue = parameterGroup.optString("propertyValue");
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

			return new ExtendedChoiceParameterDefinition(name, type, propertyValue, propertyFile, groovyScript, groovyScriptFile, bindings, propertyKey, defaultPropertyValue, defaultPropertyFile, defaultGroovyScript, defaultGroovyScriptFile, defaultBindings, defaultPropertyKey, quoteValue, visibleItemCount, description, multiSelectDelimiter);
		}
	}

	private boolean quoteValue;

	private int visibleItemCount;

	private String type;

	private String value;
	
	private transient int multiSelectTotal = 0;

	private transient Map<Integer, String> allCols = new HashMap<Integer, String>();
	
	private String propertyFile;

	private String groovyScript;

	private String groovyScriptFile;

	private String bindings;

	private String propertyKey;

	private String defaultValue;

	private String defaultPropertyFile;

	private String defaultGroovyScript;

	private String defaultGroovyScriptFile;

	private String defaultBindings;

	private String defaultPropertyKey;

	private String multiSelectDelimiter;

	public ExtendedChoiceParameterDefinition(String name, String type, String value, String propertyFile, String groovyScript,
			String groovyScriptFile, String bindings, String propertyKey, String defaultValue, String defaultPropertyFile,
			String defaultGroovyScript, String defaultGroovyScriptFile, String defaultBindings, String defaultPropertyKey, boolean quoteValue,
			int visibleItemCount, String description, String multiSelectDelimiter) {
		super(name, description);
		this.type = type;

		this.value = value;
		this.propertyFile = propertyFile;
		this.propertyKey = propertyKey;
		this.groovyScript = groovyScript;
		this.groovyScriptFile = groovyScriptFile;
		this.bindings = bindings;

		this.defaultValue = defaultValue;
		this.defaultPropertyFile = defaultPropertyFile;
		this.defaultPropertyKey = defaultPropertyKey;
		this.defaultGroovyScript = defaultGroovyScript;
		this.defaultGroovyScriptFile = defaultGroovyScriptFile;
		this.defaultBindings = defaultBindings;

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
		Map<Integer, String> allCols = new HashMap<Integer, String>();		
		
		if(value instanceof String) {
			strValue = (String)value;
		}
		else if(value instanceof JSONArray) {
			int multiLevelColumns = 0;
			JSONArray jsonValues = (JSONArray)value;
			if(type.equals(PARAMETER_TYPE_MULTI_LEVEL_SINGLE_SELECT) || type.equals(PARAMETER_TYPE_MULTI_LEVEL_MULTI_SELECT)) {
				final int valuesBetweenLevels = this.value.split(",").length;
				multiLevelColumns = valuesBetweenLevels;
				
				Iterator<?> it = jsonValues.iterator();
				for(int i = 1; it.hasNext(); i++) {
					String nextValue = it.next().toString();
					this.multiSelectTotal = i;
					
					allCols.put(i, nextValue);
					
					if(i % valuesBetweenLevels == 0) {
						if(strValue.length() > 0) {
							strValue += getMultiSelectDelimiter();
						}
						strValue += nextValue;
					}
					this.allCols = allCols;
				}
			}
			else {
				strValue = StringUtils.join(jsonValues.iterator(), getMultiSelectDelimiter());
			}
			
			// Concatenate all multi-select values into one string
			strValue = "";
			StringBuilder strCols = new StringBuilder();
			for (int eachSelect = 1, col = 1, row = 1; eachSelect <= this.multiSelectTotal; eachSelect++, col++) {
				if (col == 1) { // 1st column
					strCols.append(row).append(":").append(this.allCols.get(eachSelect)).append(",");
				}
				else if ((eachSelect % multiLevelColumns) == 0) { // last column
					strCols.append(this.allCols.get(eachSelect)).append(":");
					col = 0;
					row++;
				} 
				else { // columns in between
					strCols.append(this.allCols.get(eachSelect)).append(",");
				}
			}
			strValue = strCols.toString();
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

	// note that computeValue is not called by multiLevel.jelly
	private String computeValue(String value, String propertyFilePath, String propertyKey, String groovyScript, String groovyScriptFile,
			String bindings, boolean isDefault) {
		if(!StringUtils.isBlank(propertyFilePath) && !StringUtils.isBlank(propertyKey)) {
			try {
				File propertyFile = new File(propertyFilePath);
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
					URL propertyFileUrl = new URL(propertyFilePath);
					property.setUrl(propertyFileUrl);
					property.execute();
					return project.getProperty(propertyKey);
				}

			}
			catch(Exception e) {

			}
		}
		else if(!StringUtils.isBlank(groovyScript)) {
			try {
				GroovyShell groovyShell = new GroovyShell();
				setBindings(groovyShell, bindings);
				Object groovyValue = groovyShell.evaluate(groovyScript);
				String processedGroovyValue = processGroovyValue(isDefault, groovyValue);
				return processedGroovyValue;
			}
			catch(Exception e) {

			}
		}
		else if(!StringUtils.isBlank(groovyScriptFile)) {
			try {
				GroovyShell groovyShell = new GroovyShell();
				setBindings(groovyShell, bindings);
				groovyScript = Util.loadFile(new File(groovyScriptFile));
				Object groovyValue = groovyShell.evaluate(groovyScript);
				String processedGroovyValue = processGroovyValue(isDefault, groovyValue);
				return processedGroovyValue;
			}
			catch(Exception e) {

			}
		}
		else if(!StringUtils.isBlank(value)) {
			return value;
		}
		return null;
	}

	private String processGroovyValue(boolean isDefault, Object groovyValue) {
		String value = null;
		if(groovyValue instanceof String[]) {
			String[] groovyValues = (String[])groovyValue;
			if(!isDefault) {
				value = StringUtils.join((String[])groovyValue, multiSelectDelimiter);
			}
			else if(groovyValues.length > 0) {
				value = groovyValues[0];
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
	}

	@Override
	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public String getEffectiveDefaultValue() {
		return computeValue(defaultValue, defaultPropertyFile, defaultPropertyKey, defaultGroovyScript, defaultGroovyScriptFile, defaultBindings, true);
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

	public String getEffectiveValue() {
		return computeValue(value, propertyFile, propertyKey, groovyScript, groovyScriptFile, bindings, false);
	}

	private ArrayList<Integer> columnIndicesForDropDowns(String[] headerColumns) {
		ArrayList<Integer> columnIndicesForDropDowns = new ArrayList<Integer>();

		String[] dropDownNames = value.split(",");

		for(String dropDownName: dropDownNames) {
			for(int i = 0; i < headerColumns.length; ++i) {
				if(headerColumns[i].equals(dropDownName)) {
					columnIndicesForDropDowns.add(new Integer(i));
				}
			}
		}

		return columnIndicesForDropDowns;
	}

	LinkedHashMap<String, LinkedHashSet<String>> calculateChoicesByDropdownId() throws Exception {
		File file = new File(propertyFile);
		List<String[]> fileLines = Collections.emptyList();
		if(file.isFile()) {
			CSVReader csvReader = null;
			try {
				csvReader = new CSVReader(new FileReader(file), '\t');
				fileLines = csvReader.readAll();
			}
			finally {
				csvReader.close();
			}
		}
		else {
			URL propertyFileUrl = new URL(propertyFile);
			CSVReader csvReader = null;
			try {
				csvReader = new CSVReader(new InputStreamReader(propertyFileUrl.openStream()), '\t');
				fileLines = csvReader.readAll();
			}
			finally {
				csvReader.close();
			}
		}

		if(fileLines.size() < 2) {
			throw new Exception("Multi level tab delimited file must have at least 2 " + "lines (one for the header, and one or more for the data)");
		}

		ArrayList<Integer> columnIndicesForDropDowns = columnIndicesForDropDowns(fileLines.get(0));

		List<String[]> dataLines = fileLines.subList(1, fileLines.size());

		LinkedHashMap<String, LinkedHashSet<String>> choicesByDropdownId = new LinkedHashMap<String, LinkedHashSet<String>>();

		String prefix = getName() + " dropdown MultiLevelMultiSelect 0";
		choicesByDropdownId.put(prefix, new LinkedHashSet<String>());

		for(int i = 0; i < columnIndicesForDropDowns.size(); ++i) {
			String prettyCurrentColumnName = value.split(",")[i];
			prettyCurrentColumnName = prettyCurrentColumnName.toLowerCase();
			prettyCurrentColumnName = prettyCurrentColumnName.replace("_", " ");

			for(String[] dataLine: dataLines) {
				String priorLevelDropdownId = prefix;
				String currentLevelDropdownId = prefix;

				int column = 0;
				for(int j = 0; j <= i; ++j) {
					column = columnIndicesForDropDowns.get(j);

					if(j < i) {
						priorLevelDropdownId += " " + dataLine[column];
					}
					currentLevelDropdownId += " " + dataLine[column];
				}
				if(i != columnIndicesForDropDowns.size() - 1) {
					choicesByDropdownId.put(currentLevelDropdownId, new LinkedHashSet<String>());
				}
				LinkedHashSet<String> choicesForPriorDropdown = choicesByDropdownId.get(priorLevelDropdownId);
				choicesForPriorDropdown.add("Select a " + prettyCurrentColumnName + "...");
				choicesForPriorDropdown.add(dataLine[column]);
			}
		}

		return choicesByDropdownId;
	}

	public String getMultiLevelDropdownIds() throws Exception {
		String dropdownIds = new String();

		LinkedHashMap<String, LinkedHashSet<String>> choicesByDropdownId = calculateChoicesByDropdownId();

		for(String id: choicesByDropdownId.keySet()) {
			if(dropdownIds.length() > 0) {
				dropdownIds += ",";
			}
			dropdownIds += id;
		}

		return dropdownIds;

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
		LinkedHashMap<String, LinkedHashSet<String>> choicesByDropdownId = calculateChoicesByDropdownId();

		Map<String, String> collapsedMap = new LinkedHashMap<String, String>();

		for(String dropdownId: choicesByDropdownId.keySet()) {
			String choices = new String();
			for(String choice: choicesByDropdownId.get(dropdownId)) {
				if(choices.length() > 0) {
					choices += ",";
				}
				choices += choice;
			}

			collapsedMap.put(dropdownId, choices);
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
