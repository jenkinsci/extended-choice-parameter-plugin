/*
 *Copyright (c) 2013 Costco, Vimil Saju
 *Copyright (c) 2013 John DiMatteo
 *See the file license.txt for copying permission.
 */

package com.cwctravel.hudson.plugins.extended_choice_parameter;

import hudson.Extension;
import hudson.model.ParameterValue;
import hudson.model.ParameterDefinition;
import hudson.util.FormValidation;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;

import javax.servlet.ServletException;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import org.apache.commons.lang.StringUtils;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.Property;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import au.com.bytecode.opencsv.CSVReader;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.Reader;

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

		public FormValidation doCheckPropertyFile(@QueryParameter final String propertyFile, @QueryParameter final String propertyKey, @QueryParameter final String type) throws IOException, ServletException {
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

			if(   type.equals(PARAMETER_TYPE_MULTI_LEVEL_SINGLE_SELECT)
				 || type.equals(PARAMETER_TYPE_MULTI_LEVEL_MULTI_SELECT))
			{
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
						@QueryParameter final String defaultPropertyKey, @QueryParameter final String type) throws IOException, ServletException
		{
			return doCheckPropertyFile(defaultPropertyFile, defaultPropertyKey, type);
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
			if (   type.equals(PARAMETER_TYPE_MULTI_LEVEL_SINGLE_SELECT)
				  || type.equals(PARAMETER_TYPE_MULTI_LEVEL_MULTI_SELECT))
			{
				final int valuesBetweenLevels = this.value.split(",").length;
				
				Iterator it = jsonValues.iterator();
				for (int i = 1; it.hasNext(); i++)
				{
					String nextValue = it.next().toString();
					if (i % valuesBetweenLevels == 0)
					{
						if (strValue.length() > 0)
						{
							strValue += getMultiSelectDelimiter();
						}
						strValue += nextValue;
					}
				}
			}
			else
			{
				strValue = StringUtils.join(jsonValues.iterator(), getMultiSelectDelimiter());
			}
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
	
	private ArrayList<Integer> columnIndicesForDropDowns(String[] headerColumns)
	{
		ArrayList<Integer> columnIndicesForDropDowns = new ArrayList<Integer>();
		
		String[] dropDownNames = value.split(",");

		for (String dropDownName : dropDownNames)
		{
			for (int i = 0; i < headerColumns.length; ++i)
			{
				if (headerColumns[i].equals(dropDownName))
				{
					columnIndicesForDropDowns.add(new Integer(i));
				}
			}
		}
		
		return columnIndicesForDropDowns;
	}
	
	LinkedHashMap<String, LinkedHashSet<String>> calculateChoicesByDropdownId() throws Exception
	{
            Reader rdr;
            File src = new File(propertyFile);
            if( src.exists()) {
                    rdr =new FileReader(src);
            } else {             
                URL propertyFileUrl = new URL(propertyFile);
                rdr = new BufferedReader(
                    new InputStreamReader(propertyFileUrl.openStream()));
             }
                                 
            List<String[]> fileLines = new CSVReader(rdr, '\t').readAll();
  

		if (fileLines.size() < 2)
		{
			throw new Exception("Multi level tab delimited file must have at least 2 "
							+ "lines (one for the header, and one or more for the data)");
		}

		ArrayList<Integer> columnIndicesForDropDowns =
						columnIndicesForDropDowns(fileLines.get(0));
		
		List<String[]> dataLines = fileLines.subList(1, fileLines.size());

		LinkedHashMap<String, LinkedHashSet<String>> choicesByDropdownId =
						new LinkedHashMap<String, LinkedHashSet<String>>();

		String prefix = getName() + " dropdown MultiLevelMultiSelect 0";
		choicesByDropdownId.put(prefix, new LinkedHashSet<String>());

		for (int i=0; i < columnIndicesForDropDowns.size(); ++i)
		{
			String prettyCurrentColumnName = value.split(",")[i];
			prettyCurrentColumnName = prettyCurrentColumnName.toLowerCase();
			prettyCurrentColumnName = prettyCurrentColumnName.replace("_", " ");

			for (String[] dataLine : dataLines)
			{
				String priorLevelDropdownId = prefix;
				String currentLevelDropdownId = prefix;

				int column = 0;
				for (int j=0; j <= i; ++j)
				{
					column = columnIndicesForDropDowns.get(j);

					if (j < i)
					{
						priorLevelDropdownId += " " + dataLine[column];
					}
					currentLevelDropdownId += " " + dataLine[column];
				}					
				if (i != columnIndicesForDropDowns.size() - 1)
				{
					choicesByDropdownId.put(currentLevelDropdownId, new LinkedHashSet<String>());
				}
				LinkedHashSet<String> choicesForPriorDropdown
								= choicesByDropdownId.get(priorLevelDropdownId);
				choicesForPriorDropdown.add("Select a " + prettyCurrentColumnName
																		+ "...");
				choicesForPriorDropdown.add(dataLine[column]);
			}				
		}

		return choicesByDropdownId;
	}
	
	public String getMultiLevelDropdownIds() throws Exception
	{
		String dropdownIds = new String();
		
		LinkedHashMap<String, LinkedHashSet<String>> choicesByDropdownId = 
						calculateChoicesByDropdownId();
		
		for (String id : choicesByDropdownId.keySet())
		{
			if (dropdownIds.length() > 0)
			{
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
	
	public Map<String, String> getChoicesByDropdownId() throws Exception
	{
		LinkedHashMap<String, LinkedHashSet<String>> choicesByDropdownId = 
			calculateChoicesByDropdownId();
		
		Map<String, String> collapsedMap = new LinkedHashMap<String, String>();
		
		for (String dropdownId : choicesByDropdownId.keySet())
		{
			String choices = new String();
			for (String choice : choicesByDropdownId.get(dropdownId))
			{
				if (choices.length() > 0)
				{
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
