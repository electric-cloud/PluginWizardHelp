package com.electriccloud.pluginwizardhelp

import com.electriccloud.pluginwizardhelp.domain.Changelog
import com.electriccloud.pluginwizardhelp.domain.Field
import com.electriccloud.pluginwizardhelp.domain.HelpMetadata
import com.electriccloud.pluginwizardhelp.domain.Procedure
import com.electriccloud.pluginwizardhelp.exceptions.InvalidPlugin
import com.electriccloud.pluginwizardhelp.exceptions.SlurperException
import groovy.util.slurpersupport.NodeChild
import org.yaml.snakeyaml.Yaml

class DataSlurper {
    String pluginFolder
    Logger logger = Logger.getInstance()
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

    HelpMetadata readHelpMetadata() {
        def helpFile = new File(pluginFolder, HELP_FILE_PATH)
        if (!helpFile.exists()) {
            logger.info("No help file exists at $HELP_FILE_PATH")
            throw new InvalidPlugin("No help metadata file exists at $HELP_FILE_PATH")
        } else {
            logger.info("Found metadata")
            def metadata = HelpMetadata.fromYaml(helpFile)
            metadata = addMetaChapter(metadata, "overview")
            metadata = addMetaChapter(metadata, "prerequisites")
            return metadata
        }
    }


    def addMetaChapter(HelpMetadata metadata, String name) {
        def chapter = new File(pluginFolder, "help/${name}.md")
        if (chapter.exists()) {
            if (metadata.getProperty(name)) {
                logger.warning("$name in metadata.yaml will be overriden by ${name}.md")
            }
            metadata.setProperty(name, chapter.text)
        }
        else {
            logger.warning("$name file does not exist. Consider placing it under ${chapter.absolutePath}.")
        }
        return metadata
    }

    Changelog readChangelog() {
        def changelogFile = new File(pluginFolder, "help/changelog.yaml")
        if (!changelogFile.exists()) {
            throw new InvalidPlugin("Changelog does not exist at ${changelogFile.absolutePath}")
        } else {
            logger.info("Found changelog")
            Changelog changelog = Changelog.fromYaml(changelogFile)
            return changelog
        }
    }

    List<Procedure> readProcedures() {
        List procedures = []
        def proceduresFolder = new File(pluginFolder, "dsl/procedures")

        proceduresFolder.eachFile { File folder ->
            logger.info("Reading procedure ${folder}")
            if (!folder.name.endsWith("_ignore")) {
                Procedure procedure = readProcedure(folder)
                if (procedure.name != 'EditConfiguration' && procedure.name != 'DeleteConfiguration')
                    procedures << procedure
                logger.info("Found procedure: ${procedure.name}")
            }
        }
        return procedures
    }

    List<String> readUseCases() {
        List cases = []
        def useCasesFolder = new File(pluginFolder, "help/UseCases")
        if (!useCasesFolder.exists()) {
            logger.info("UseCases folder does not exist in the help folder, consider placing use cases under ${useCasesFolder.absolutePath}")
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

    Procedure readProcedure(File procedureFolder) {
        def procedureMetadata = readProcedureMetadata(procedureFolder)
        def fields = readFields(getFormXml(procedureFolder))
        def procedure = new Procedure(
            name: procedureMetadata.name,
            description: procedureMetadata.description,
            fields: fields
        )
        procedure = withMetadata(procedure, procedureFolder)
        return procedure
    }


    Procedure withMetadata(Procedure procedure, File metadataFolder) {
        def procedureHelp = new File(metadataFolder, "help.yaml")
        if (procedureHelp.exists()) {
            def help = new Yaml().load(new FileReader(procedureHelp))
            procedure.preface = help.preface
            procedure.postface = help.postface
            procedure.token = help.token

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

        procedure.preface = procedure.preface ?: getProcedurePreface(metadataFolder)
        procedure.postface = procedure.postface ?: getProcedurePostface(metadataFolder)
        procedure.token = procedure.token ?: getProcedureToken(metadataFolder)
        if (procedure.preface) {
            logger.info("Found preface for ${procedure.name}")
        }
        if (procedure.postface) {
            logger.info("Found postface for ${procedure.name}")
        }
        procedure.deprecated = isDeprecated(procedure)
        return procedure
    }

    boolean isDeprecated(Procedure proc) {
        if (this.metadata.deprecatedProcedures && this.metadata.deprecatedProcedures.find {
            it == proc.name
        }) {
            return true
        }
        return false
    }

    String getProcedurePreface(procedureFolder) {
        File file = new File(procedureFolder, "preface.md")
        if (file.exists()) {
            return file.text
        }
        else {
            return null
        }
    }

    String getProcedurePostface(procedureFolder) {
        File file = new File(procedureFolder, "postface.md")
        if (file.exists()) {
            return file.text
        }
        else {
            return null
        }
    }


    String getProcedureToken(procedureFolder) {
        File file = new File(procedureFolder, "token.txt")
        if (file.exists()) {
            return file.text
        }
        else {
            return null
        }
    }

    Map readProcedureMetadata(File procedureFolder) {
        def dslCode = '''
def procedure(params, name, closure) {
    def retval = [:]
    retval.name = name
    retval.description = params.description
    return retval
}

def procedure(name, closure) {
    def retval = [:]
    retval.name = name
    retval.description = ''
    return retval
}
'''
        def procedureMetadata = [:]
        try {
            procedureMetadata = Eval.me(dslCode + "\n" + getProcedureDsl(procedureFolder))
        } catch (Throwable e) {
            throw new SlurperException("Cannot eval procedure.dsl for ${procedureFolder.name}: ${e.getMessage()}")
        }
        if (!procedureMetadata.name) {
            logger.warning("Procedure in folder ${procedureFolder.name} does not have a name")
            throw new SlurperException("Procedure in folder ${procedureFolder.name} does not have a name")
        }
        if (!procedureMetadata.description) {
            logger.warning("Procedure in ${procedureFolder.name} does not have a description")
            throw new SlurperException("Procedure in folder ${procedureFolder.name} does not have a description")
        }
        procedureMetadata
    }

    private String getProcedureDsl(File folder) {
        new File(folder, "procedure.dsl").text
    }

    private String getFormXml(File folder) {
        new File(folder, "form.xml").text
    }

    List<Field> readFields(String formXml) {
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

    String extractHtmlDocumentation(NodeChild element) {
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
        String result = writer.toString()
        return result
    }

    void processNestedNode(StringWriter writer, def child) {
        if (child.hasProperty('name')) {
            def tag = child.name
            writer.write("<" + tag)
            if (child.hasProperty('attributes') && child.attributes.size() > 0) {
                writer.write(' ')
                boolean isLink
                def attributes = child.attributes.collect {
                    "${it.key}=\"${it.value}\""
                }
                if (tag == 'a') {
                    isLink = true
                }
                if (isLink && !attributes.find { it =~ /target/ }) {
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
            childValue = childValue.replaceAll(/>/, '&gt;').replaceAll(/</, '&lt;')
            writer.write(childValue)
        }
    }

}
