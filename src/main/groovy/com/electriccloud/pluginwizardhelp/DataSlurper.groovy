package com.electriccloud.pluginwizardhelp

import com.electriccloud.pluginwizardhelp.domain.Changelog
import com.electriccloud.pluginwizardhelp.domain.Field
import com.electriccloud.pluginwizardhelp.domain.HelpMetadata
import com.electriccloud.pluginwizardhelp.domain.Procedure
import groovy.util.slurpersupport.NodeChild
import org.yaml.snakeyaml.Yaml

import java.util.logging.Logger

class DataSlurper {
    String pluginFolder
    Logger logger = Logger.getLogger("")
    @Lazy(soft = true)
    List procedures = readProcedures()
    private static String HELP_FILE_PATH = "help/metadata.yaml"
    @Lazy(soft = true)
    HelpMetadata metadata = readHelpMetadata()
    @Lazy
    Changelog changelog = readChangelog()
    @Lazy
    List<String> useCases = readUseCases()

    DataSlurper(String pluginFolder) {
        this.pluginFolder = pluginFolder
    }

    private HelpMetadata readHelpMetadata() {
        def helpFile = new File(pluginFolder, HELP_FILE_PATH)
        if (!helpFile.exists()) {
            logger.info("No help file exists at $HELP_FILE_PATH")
        } else {
            logger.info("Found metadata")
            return HelpMetadata.fromYaml(helpFile)
        }
    }

    private Changelog readChangelog() {
        def changelogFile = new File(pluginFolder, "help/changelog.yaml")
        if (!changelogFile.exists()) {
            logger.info("Changelog is not found, please place changelog.yaml into help folder.")
        } else {
            logger.info("Found changelog")
            Changelog changelog = Changelog.fromYaml(changelogFile)
            return changelog
        }
    }

    private List<Procedure> readProcedures() {
        List procedures = []
        def proceduresFolder = new File(pluginFolder, "dsl/procedures")

        proceduresFolder.eachFile { File folder ->
            logger.info("Reading procedure ${folder}")
            Procedure procedure = readProcedure(folder)
            if (procedure.name != 'EditConfiguration' && procedure.name != 'DeleteConfiguration')
                procedures << procedure
            logger.info("Found procedure: ${procedure.name}")
        }
        return procedures
    }

    private List<String> readUseCases() {
        List cases = []
        def useCasesFolder = new File(pluginFolder, "help/UseCases")
        if (!useCasesFolder.exists()) {
            logger.info("UseCases folder does not exist in the help folder")
        } else {
            def files = []
            useCasesFolder.eachFile { file ->
                files << file

            }
            files = files.sort { a, b ->
                a.name <=> b.name
            }
            files.each { file ->
                String markdown = file.text
                cases << markdown
            }
            logger.info("Found use-cases")
        }
        cases
    }

    private Procedure readProcedure(File procedureFolder) {
        def procedureMetadata = readProcedureMetadata(procedureFolder)
        def fields = readFields(getFormXml(procedureFolder))
        def procedure = new Procedure(
            name: procedureMetadata.name,
            description: procedureMetadata.description,
            fields: fields
        )


        def procedureHelp = new File(procedureFolder, "help.yaml")
        if (procedureHelp.exists()) {
            def help = new Yaml().load(new FileReader(procedureHelp))
            procedure.preface = help.preface
            procedure.postface = help.postface

            if (help.fields) {
                procedure.fields.each { field ->
                    if (help.fields[field.name]) {
                        field.additionalDocumentation = help.fields[field.name].additionalDocumentation
                    }
                }
            }
        } else {
            logger.info("Procedure help file does not exist for procedure ${procedure.name}")
        }

        if (!procedure.preface) {
            procedure.preface = getProcedurePreface(procedureFolder)
        }
        if (!procedure.postface) {
            procedure.postface = getProcedurePostface(procedureFolder)
        }
        if (procedure.preface) {
            logger.info("Found preface for ${procedure.name}")
        }
        if (procedure.postface) {
            logger.info("Found postface for ${procedure.name}")
        }
        procedure.postface ?: getProcedurePostface(procedureFolder)

        if (this.metadata.deprecatedProcedures && this.metadata.deprecatedProcedures.find {
            it == procedure.name
        }) {
            procedure.deprecated = true
        }

        procedure

    }

    private String getProcedurePreface(procedureFolder) {
        File file = new File(procedureFolder, "preface.md")
        if (file.exists()) {
            return file.text
        }
        else {
            return null
        }
    }

    private String getProcedurePostface(procedureFolder) {
        File file = new File(procedureFolder, "postface.md")
        if (file.exists()) {
            return file.text
        }
        else {
            return null
        }
    }

    private Map readProcedureMetadata(File procedureFolder) {
        def dslCode = '''
def procedure(params, name, closure) {
    def retval = [:]
    retval.name = name
    retval.description = params.description
    return retval
}
'''
        def procedureMetadata = Eval.me(dslCode + "\n" + getProcedureDsl(procedureFolder))
        if (!procedureMetadata.name) {
            logger.warning("Procedure in ${procedureFolder} does not have a name")
            throw new RuntimeException("Procedure does not have a name")
        }
        if (!procedureMetadata.description) {
            logger.warning("Procedure in ${procedureFolder} does not have a description")
            throw new RuntimeException("Procedure does not have a description")
        }
        procedureMetadata
    }

    private String getProcedureDsl(File folder) {
        new File(folder, "procedure.dsl").text
    }

    private String getFormXml(File folder) {
        new File(folder, "form.xml").text
    }

    private List<Field> readFields(String formXml) {
        def form = new XmlSlurper().parseText(formXml)
        def fields = form.formElement.collect { element ->
            def name = element.label.toString().replaceAll(':', '')
            def required = element.required.toBoolean()
            def documentation

            if (element.htmlDocumentation.toString() != "") {
                documentation = extractHtmlDocumentation(element)
            } else {
                documentation = element.documentation.toString()
            }
            new Field(
                name: name,
                required: required,
                documentation: documentation
            )
        }
        fields
    }

    private String extractHtmlDocumentation(NodeChild element) {
        assert element.htmlDocumentation.toString()
        Iterator it = element.childNodes()
        def htmlDocumentation
        while (it.hasNext()) {
            def node = it.next()
            if (node.name == 'htmlDocumentation') {
                htmlDocumentation = node
            }
        }
        assert htmlDocumentation
        def writer = new StringWriter()
        htmlDocumentation.children().each { def child ->
            processNestedNode(writer, child)
        }
        writer.toString()

    }

    private void processNestedNode(StringWriter writer, def child) {
        if (child.hasProperty('name')) {
            def tag = child.name
            writer.write("<" + tag)
            if (child.hasProperty('attributes') && child.attributes.size() > 0) {
                writer.write(' ')
                def attributes = child.attributes.collect {
                    "${it.key}=\"${it.value}\""
                }
                if (tag == 'a') {
                    attributes << 'target="_blank"'
                }
                writer.write(attributes.join(' '))
            }
            def hasChildren = child.hasProperty('children') && child.children.size() > 0
            if (hasChildren) {
                writer.write('>')
                child.children.each {
                    processNestedNode(writer, it)
                }
                writer.write('</' + tag + '>')
            } else {
                writer.write('/>')
            }

        }
        if (child.hasProperty('value') && child.value.size() > 0) {
            byte[] value = child.value as byte[]
            String childValue = new String(value, 'UTF-8')
            writer.write(childValue)
        }
    }

}
