package com.cwctravel.hudson.plugins.extended_choice_parameter;

import org.kohsuke.stapler.DataBoundConstructor;

import hudson.model.StringParameterValue;

public class ExtendedChoiceParameterValue extends StringParameterValue{
	private static final long serialVersionUID = 7993744779892775177L;
	
	@DataBoundConstructor
	public ExtendedChoiceParameterValue(String name, String value) {
		super(name, value);
	}

}
