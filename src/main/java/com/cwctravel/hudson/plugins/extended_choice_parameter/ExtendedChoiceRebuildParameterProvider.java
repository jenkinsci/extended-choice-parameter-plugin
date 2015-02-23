/*
 *Copyright (c) 2015 Len Isac
 *See the file license.txt for copying permission.
 */
package com.cwctravel.hudson.plugins.extended_choice_parameter;

import hudson.Extension;
import hudson.model.ParameterValue;

import com.sonyericsson.rebuild.RebuildParameterPage;
import com.sonyericsson.rebuild.RebuildParameterProvider;

/**
 * An extension class to inject {@link ExtendedChoiceParameterValue} to rebuild-plugin.
 */
@Extension(optional = true)
public class ExtendedChoiceRebuildParameterProvider extends RebuildParameterProvider {
    /**
     * @param value
     * @return
     * @see com.sonyericsson.rebuild.RebuildParameterProvider#getRebuildPage(hudson.model.ParameterValue)
     */
    @Override
    public RebuildParameterPage getRebuildPage(ParameterValue value) {
        if (value instanceof ExtendedChoiceParameterValue) {
            return new RebuildParameterPage(ExtendedChoiceParameterValue.class, "rebuild.groovy");
        }
        
        return null;
    }
}
