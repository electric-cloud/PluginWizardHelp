package com.electriccloud.pluginwizardhelp

import com.electriccloud.pluginwizardhelp.domain.Procedure
import com.electriccloud.pluginwizardhelp.exceptions.MissingFormXML

class GradleDataSlurper extends DataSlurper implements Constants {

    GradleDataSlurper(String pluginFolder) {
        super(pluginFolder)
    }

    Map forms = [:]

    @Override
    List<Procedure> readProcedures() {
        List procedures = []
        def projectXml = readProjectXML()
        boolean foundConfig = false
        def excluded = this.metadata.excludeProcedures
        projectXml.project.procedure.each { def procedure ->
            String name = procedure.procedureName
            String description = procedure.description

            def xmlRaw
            if (isConfigurationProcedure(name)) {
                foundConfig = true
            } else {
                if (!(name in excluded)) {
                    xmlRaw = forms[name]
                    if (!xmlRaw) {
                        throw new MissingFormXML("No form.xml found for procedure $name")
                    }
                    def proc = buildProcedure(xmlRaw.toString(), name, description)
                    procedures << proc
                }
            }
        }
        if (foundConfig) {
            def configXml = forms[CREATE_CONFIGURATION]
            procedures << buildProcedure(configXml.toString(), CREATE_CONFIGURATION, "")
        }
        return procedures
    }

    def buildProcedure(String rawForm, String name, String description) {
        def fields
        try {
            fields = readFields(rawForm)
        } catch (Throwable e) {
            throw new MissingFormXML("Invalid form.xml '$rawForm' for procedure $name: ${e.message}")
        }
        Procedure proc = new Procedure(name: name, description: description, fields: fields)
        File metadataFolder = new File(this.pluginFolder, "help/procedures/$name")
        if (metadataFolder.exists())
            proc = withMetadata(proc, metadataFolder)
        return proc
    }

    def isConfigurationProcedure(name) {
        return name == CREATE_CONFIGURATION || name == EDIT_CONFIGURATION || name == DELETE_CONFIGURATION
    }

    def readProjectXML() {
        def projectPath = new File(this.pluginFolder, "src/main/resources/project").absolutePath
        def projectXml = new XmlSlurper().parse(new File(projectPath, "project.xml"))
        def manifestXml = new XmlSlurper().parse(new File(projectPath, "manifest.xml"))
        manifestXml.file.each { file ->
            //procedure[procedureName="CreateOrUpdateJMSServer"]/propertySheet/property[propertyName="ec_parameterForm"]/value
            if (file.xpath =~ /ec_parameterForm/) {
                def group = (file.xpath =~ /procedure\[procedureName="([\w\s]+)"\]/)
                String procedureName = group?.getAt(0)?.getAt(1)
                if (!procedureName) {
                    throw new RuntimeException("Cannot get procedure name: ${file.xpath}")
                }
                def formXml = new File(projectPath, file.path.toString())
                if (formXml.exists()) {
                    forms[procedureName] = formXml.text
                } else {
                    logger.warning("File ${file.path} does not exist (mentioned in manifets.xml)")
                }
            } else if (file.xpath =~ /ui_forms/ && file.xpath =~ /CreateConfig/) {
                def formXml = new File(projectPath, file.path.toString())
                if (formXml.exists()) {
                    forms[CREATE_CONFIGURATION] = formXml.text
                } else {
                    logger.warning("Configuration form.xml is not found")
                }
            }
        }
        return projectXml
    }

}
