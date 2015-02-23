/*
 *Copyright (c) 2013 Costco, Vimil Saju
 *See the file license.txt for copying permission.
 */


package com.cwctravel.hudson.plugins.extended_choice_parameter;

import org.kohsuke.stapler.DataBoundConstructor;

import java.util.Map;
import java.util.List;

import hudson.model.StringParameterValue;

public class ExtendedChoiceParameterValue extends StringParameterValue {
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
		this.setAllCols(allCols);
		this.setMultiLevelColumns(multiLevelColumns);
	}

	public Map<Integer, String> getAllCols() {
		return allCols;
	}

	public void setAllCols(Map<Integer, String> allCols) {
		this.allCols = allCols;
	}

	public Map<Integer, List<String>> getAllColsList() {
		return allColsList;
	}

	public void setAllColsList(Map<Integer, List<String>> allColsList) {
		this.allColsList = allColsList;
	}

	public int getMultiLevelColumns() {
		return multiLevelColumns;
	}

	public void setMultiLevelColumns(int multiLevelColumns) {
		this.multiLevelColumns = multiLevelColumns;
	}
}
