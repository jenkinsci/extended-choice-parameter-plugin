# Extended Choice Parameter Plugin

[![Build](https://ci.jenkins.io/job/Plugins/job/extended-choice-parameter-plugin/job/main/badge/icon)](https://ci.jenkins.io/job/Plugins/job/extended-choice-parameter-plugin/job/main)<br/>
[![Contributors](https://img.shields.io/github/contributors/jenkinsci/extended-choice-parameter-plugin.svg?color=blue)](https://github.com/jenkinsci/extended-choice-parameter-plugin/graphs/contributors)<br/>
[![Jenkins Plugin Installs](https://img.shields.io/jenkins/plugin/i/extended-choice-parameter.svg?color=blue&label=installations)](https://plugins.jenkins.io/extended-choice-parameter)<br/>
[![Plugin](https://img.shields.io/jenkins/plugin/v/extended-choice-parameter.svg)](https://plugins.jenkins.io/extended-choice-parameter)<br/>

# END OF LIFE
Given the age of this plugin and the number of security issues with the code base, no further development is expected. There are
many excellent alternatives that may suit your purpose.

# ALTERNATIVES
There are other parameter plugins to use for user inputs.
- [Json Editor Parameter](https://plugins.jenkins.io/json-editor-parameter/)
- [Active Choices](https://plugins.jenkins.io/uno-choice/)
- [Extensible Choice](https://plugins.jenkins.io/extensible-choice-parameter/)
- [Editable Choice](https://plugins.jenkins.io/editable-choice/)

## File Inputs and Outputs
Any file I/O will be removed in a future version.  Use another step to read or write files:
- [Pipeline Utility Steps](https://github.com/jenkinsci/pipeline-utility-steps-plugin/blob/master/docs/STEPS.md)

## Groovy Scripting
Any Groovy Scripting will be removed in a future version.  Use
[pipeline](https://plugins.jenkins.io/ui/search?sort=relevance&categories=&labels=&view=Tiles&page=1&query=pipeline) 
or another plugin to execute groovy:
- [Groovy](https://plugins.jenkins.io/groovy/)

## Introduction

The `extended-choice-parameter-plugin` creates various types of choice fields for use with the
[Input Step](https://www.jenkins.io/doc/pipeline/steps/pipeline-input-step) plugin.

This is accomplished using
[json-editor](https://github.com/jdorn/json-editorhttps://github.com/jdorn/json-editor), which
generates an HTML form from a JSON Schema. The json editor requires two inputs: the html
`id` of the section holding the form and `options` which drive the form creation.

## Help Wanted

Additional documentation is desired. Please create pull requests with better documentation of the
params.  *Thanks!*

## Params

### name

The name of the parameter.

### type

The type of parameter

- Single Select: user chooses a single selection from a drop-down menu, populated by either explicit
  values (see Value field below) or a property file (see Property File and Property Key fields
  below)
- Multi Select: a user can choose multiple selections from a multi-line box, populated by either
  explicit values (see Value field below) or a property file (see Property File and Property Key
  fields below)
- Check Boxes: user can check off zero or more check boxes, labeled by either explicit values (see
  Value field below) or a property file (see Property File and Property Key fields below)
- Multi-Level Single Select: user chooses a selection from a drop-down, and then a another drop down
  appears with selections that depend on the first value, and upon second selection a third drop
  down may appear depending on the first two selections, and so on.
- Multi-Level Multi Select: same as single select, but after all levels are chosen, a button appears
  to "Select another..." and an additional multi-level selection is presented.

### value

Comma separated list of values for the single select or multi-select box.
This field can be left blank if the comma separated values need to be picked up from a properties
file (set via 'Property File' and 'Property Key').

### propertyFile

The properties file is a collection of key,value pairs of the form key=value1,value2,...

### propertyKey

The property of the property file to use.

For example, if the property file was the following:
<pre>
prop1=a,b,c,d,e
prop2=1,2,3,4
</pre>
Then you could specify the property as either `prop1` or `prop2`.

### defaultValue

Initial selection of the single-select or mult-select box.

In case of the multi-select box, default value can be a comma separated string.

### defaultPropertyFile

Absolute path (specified without using environment variables).

### multiSelectDelimiter

Inserts this value between selections when the parameter is a multi-select.

The default when empty is ','

### projectName

If specified, this adds a currentProject entry to the groovy script context. The entry's value is
set to the specified Jenkins project.

### quoteValue

If true, the value or selected values will be formatted with quotes.

### visibleItemCount

If specified, this will limit the amount of options displayed by creating a scrolldown list 
with the only the specified amount visible at once.

### groovyScript

A groovy script used to generate the list of values.

### groovyScriptFile

A file containing a groovy script used to generate the list of values.

### defaultGroovyScript

A groovy script used to generate the list of values used in the
initial selection of the single-select or mult-select box.

### defaultGroovyScriptFile

A file containing a groovy script used to generate the list of values used in the
initial selection of the single-select or mult-select box.

### bindings

### groovyClasspath

### defaultBindings

### defaultGroovyClasspath

### defaultPropertyKey

### descriptionPropertyValue

### descriptionPropertyFile

### descriptionGroovyScript

### descriptionGroovyScriptFile

### descriptionBindings

### descriptionGroovyClasspath

### descriptionPropertyKey

### javascriptFile

### javascript

### saveJSONParameterToFile

## More

For info on how to use groovy script feature see
this [link](http://stackoverflow.com/questions/24730186/jenkins-extended-parameter-plugin-groovy-script)

## Contributing

Refer to [contribution guidelines](https://github.com/jenkinsci/.github/blob/master/CONTRIBUTING.md)

## LICENSE

Licensed under MIT, see [LICENSE](LICENSE.md)
