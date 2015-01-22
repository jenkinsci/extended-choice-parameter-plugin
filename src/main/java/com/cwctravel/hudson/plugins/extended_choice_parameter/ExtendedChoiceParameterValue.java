/*
 *Copyright (c) 2015 Len Isac  
 *Copyright (c) 2013 Costco, Vimil Saju
 *See the file license.txt for copying permission.
 */


package com.cwctravel.hudson.plugins.extended_choice_parameter;

import org.kohsuke.stapler.DataBoundConstructor;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;

import hudson.model.StringParameterValue;

public class ExtendedChoiceParameterValue extends StringParameterValue{
	private static final long serialVersionUID = 7993744779892775177L;
	private Map<Integer, String> allCols;
	private Map<Integer, List<String>> allColsList;
	private int multiLevelColumns;	
	
	@DataBoundConstructor
	public ExtendedChoiceParameterValue(String name, String value) {
		super(name, value);
	}
	
	public ExtendedChoiceParameterValue(String name, String value,
			int multiLevelColumns, Map<Integer, String> allCols) {
		super(name, value);
		this.allCols = allCols;
		this.multiLevelColumns = multiLevelColumns;
	}
}
