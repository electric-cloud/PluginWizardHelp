package com.electriccloud.pluginwizardhelp

import com.electriccloud.pluginwizardhelp.domain.Procedure
import com.electriccloud.pluginwizardhelp.exceptions.MissingFormXML

import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import javax.xml.xpath.XPathConstants
import javax.xml.xpath.XPathFactory

class GradleDataSlurper extends DataSlurper implements Constants {

    GradleDataSlurper(String pluginFolder) {
        super(pluginFolder)
    }

    @Override
    List<Procedure> readProcedures() {
        List procedures = []
        def projectXml = readProjectXML()
        boolean foundConfig = false
        projectXml.project.procedure.each { def procedure ->
            String name = procedure.procedureName
            String description = procedure.description
            def parameterFormProperty = procedure.propertySheet.property.find {
                it.propertyName == 'ec_parameterForm'
            }
            def xmlRaw
            if (isConfigurationProcedure(name)) {
                foundConfig = true
            } else {
                if (!parameterFormProperty) {
                    throw new MissingFormXML("No form.xml found for procedure $name")
                }
                xmlRaw = parameterFormProperty.value
                def proc = buildProcedure(xmlRaw.toString(), name, description)
                procedures << proc
            }
        }
        if (foundConfig) {
            def configXml = projectXml.'**'.find {
                it.propertyName =~ /CreateConfig/
            }.value
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
        def xml = DocumentBuilderFactory.newInstance().newDocumentBuilder()
        def xmlp = XPathFactory.newInstance().newXPath()

        def projectXml = xml.parse("$projectPath/project.xml")
        def replacements = xml.parse("$projectPath/manifest.xml")

        xmlp.evaluate("//file", replacements.documentElement, XPathConstants.NODESET).each {
            def file = "$projectPath/${xmlp.evaluate('path', it)}"
            def xpath = xmlp.evaluate('xpath', it)
            def nodes = xmlp.evaluate(xpath, projectXml.documentElement, XPathConstants.NODESET)
            nodes.each {
                it.setTextContent(new File(file).text)
            }
        }

        def source = new DOMSource(projectXml)
        StringWriter sw = new StringWriter()
        def result = new StreamResult(sw)
        def transformer = TransformerFactory.newInstance().newTransformer()
        transformer.transform(source, result)
        def retval = new XmlSlurper().parseText(sw.toString())
        return retval
    }

}
