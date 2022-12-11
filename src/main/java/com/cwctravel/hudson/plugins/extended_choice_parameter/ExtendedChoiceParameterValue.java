/*
 *Copyright (c) 2013 Costco, Vimil Saju
 *See the file license.txt for copying permission.
 */

package com.cwctravel.hudson.plugins.extended_choice_parameter;

import hudson.FilePath;
import hudson.model.AbstractBuild;
import hudson.model.ParameterDefinition;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.StringParameterValue;
import hudson.util.VariableResolver;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.io.FileUtils;
import org.kohsuke.stapler.DataBoundConstructor;

public class ExtendedChoiceParameterValue extends StringParameterValue {
	private static final long serialVersionUID = 7993744779892775177L;

	private static final Logger LOGGER = Logger.getLogger(ExtendedChoiceParameterValue.class.getName());

	@DataBoundConstructor
	public ExtendedChoiceParameterValue(String name, String value) {
		super(name, value);
	}

	@Override
	public VariableResolver<String> createVariableResolver(final AbstractBuild<?, ?> build) {
		return new VariableResolver<String>() {
			public String resolve(String name) {
				String result = null;
				if(ExtendedChoiceParameterValue.this.getName().equals(name)) {
					result = value;
					if(build != null) {
						ParametersDefinitionProperty parametersDefinitionProperty = build.getProject().getProperty(ParametersDefinitionProperty.class);
						if(parametersDefinitionProperty != null) {
							ParameterDefinition parameterDefinition = parametersDefinitionProperty.getParameterDefinition(name);
							if(parameterDefinition instanceof ExtendedChoiceParameterDefinition) {
								ExtendedChoiceParameterDefinition extendedChoiceParameterDefinition = (ExtendedChoiceParameterDefinition)parameterDefinition;
								if(ExtendedChoiceParameterDefinition.PARAMETER_TYPE_JSON.equals(extendedChoiceParameterDefinition.getType()) && extendedChoiceParameterDefinition.isSaveJSONParameterToFile()) {
									File jsonParametersDir = new File(build.getRootDir(), "parameters");
									if(!jsonParametersDir.mkdirs()) {
										LOGGER.log(Level.INFO, "mkdirs failed");
									}
									try {
										String jsonFileName = getName() + ".json";
										File jsonParameterFile = new File(jsonParametersDir, jsonFileName);
										FileUtils.writeStringToFile(jsonParameterFile, value);
										
										FilePath workspace = build.getWorkspace();
										if(workspace != null) {
											FilePath parametersWorkspaceDir = workspace.child("parameters");
											FilePath parameterWorkspaceFile = parametersWorkspaceDir.child(jsonFileName);
											parameterWorkspaceFile.write(value, "UTF-8");
											
											result = parameterWorkspaceFile.getRemote();
										}else {
											result = jsonParameterFile.getAbsolutePath();
										}
									}
									catch(IOException | InterruptedException e) {
										LOGGER.log(Level.SEVERE, e.getMessage(), e);
									}
								}
							}
						}
					}
				}
				return result;
			}
		};
	}

}
