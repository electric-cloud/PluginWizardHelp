package com.electriccloud.pluginwizardhelp

import com.electriccloud.pluginwizardhelp.domain.Changelog
import com.electriccloud.pluginwizardhelp.domain.Field
import com.electriccloud.pluginwizardhelp.domain.HelpMetadata
import com.electriccloud.pluginwizardhelp.domain.Procedure
import com.electriccloud.pluginwizardhelp.exceptions.InvalidPlugin
import com.electriccloud.pluginwizardhelp.exceptions.SlurperException
import groovy.util.slurpersupport.NodeChild
import org.yaml.snakeyaml.Yaml
import static org.apache.commons.lang.StringEscapeUtils.escapeHtml

class FlowpdfSlurper extends DataSlurper {
    private static String HELP_FILE_PATH = "help/metadata.yaml"
    private static String HELP_FOLDER = 'help'
    private static String METADATA_GLOB = 'metadata.y*ml'
    private static String CHANGELOG_GLOB = 'changelog.y*ml'
    private static String SPEC_PATH = 'config/pluginspec.yaml'

    String pluginFolder
    Logger logger = Logger.getInstance()
    @Lazy(soft = true)
    List procedures = readProcedures()
    @Lazy(soft = true)
    HelpMetadata metadata = readHelpMetadata()
    @Lazy
    Changelog changelog = readChangelog()
    @Lazy
    List<String> useCases = readUseCases()

    FlowpdfSlurper(String pluginFolder) {
        super(pluginFolder)
        this.pluginFolder = pluginFolder
    }

    HelpMetadata readHelpMetadata() {
        def helpFile = fileByGlob(new File(pluginFolder, HELP_FOLDER), METADATA_GLOB)
        if (!helpFile || !helpFile.exists()) {
            logger.info("No help file exists at $HELP_FILE_PATH")
            throw new InvalidPlugin("No help metadata file exists at $HELP_FILE_PATH")
        } else {
            logger.info("Found metadata")
            def metadata = HelpMetadata.fromYaml(helpFile)
            metadata = addMetaChapter(metadata, "overview")
            metadata = addMetaChapter(metadata, "prerequisites")

            File spec = new File(pluginFolder, SPEC_PATH)
            def pluginspec = new Yaml().load(spec.text)
            metadata.pluginKey = pluginspec.pluginInfo.pluginName
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
        } else {
            logger.warning("$name file does not exist. Consider placing it under ${chapter.absolutePath}.")
        }
        return metadata
    }

    Changelog readChangelog() {
        def changelogFile = fileByGlob(new File(pluginFolder, HELP_FOLDER), CHANGELOG_GLOB)
        if (changelogFile && !changelogFile.exists()) {
            throw new InvalidPlugin("Changelog does not exist at ${changelogFile.absolutePath}")
        } else {
            logger.info("Found changelog")
            Changelog changelog = Changelog.fromYaml(changelogFile)
            return changelog
        }
    }

    List<Procedure> readProcedures() {
        File spec = new File(pluginFolder, SPEC_PATH)
        def pluginspec = new Yaml().load(spec.text)

        List<Procedure> procedures = []
        pluginspec?.procedures?.each { proc ->

            if (proc.hideFromStepPicker) {
                return
            }

            List<Field> fields = proc?.parameters?.collect {
                def documentation = it.htmlDocumentation ?: it.documentation
                if (!documentation) {
                    logger.warning("Documentation not found for $it.name: $proc.name")
                }
                boolean r = it.required instanceof Boolean ? it.required : it.required == "true" || it.required == '1'
                new Field(
                    name: it.label ?: it.name,
                    required: r,
                    documentation: documentation,
                    type: it.type,
                )
            }
            if (proc.hasConfig) {
                fields.add(0, new Field(
                    name: 'Configuration Name',
                    documentation: 'Previously defined configuration for the plugin',
                    required: true
                ))
            }
            def outputParameters = proc.outputParameters?.collect {
                [name: it.key, description: it.value]
            }
            def procedure = new Procedure(name: proc.name, description: proc.description, fields: fields, outputParameters: outputParameters)
            def folderName = procedure.name.replaceAll(/\W/, '')
            def folder = new File(pluginFolder, "dsl/procedures/$folderName")
            procedure = withMetadata(procedure, folder)
            procedures << procedure
        }


        if (pluginspec.configuration) {
            List<Field> configParams = pluginspec.configuration?.parameters?.collect {
                def documentation = it.htmlDocumentation ?: it.documentation
                boolean r = it.required instanceof Boolean ? it.required : it.required == "true" || it.required == '1'
                new Field(
                    name: it.label ?: it.name,
                    required: r,
                    documentation: documentation,
                    type: it.type,
                )
            } ?: []


            /*
            configuration:
  checkConnection: true
  restConfigInfo:
    defaultEndpointValue: 'https://api.github.com'
    checkConnectionUri: '/user'
    headers:
      Accept: '*'
    endpointDescription: 'Endpoint to connect to. By default Github API endpoint.'
    authSchemes:
      basic:
        userNameLabel: Username to connect to Github
        passwordLabel: Password to connect to Github
      bearer:
        passwordLabel: Bearer token to connect to Github API.
  hasDebugLevel: true
             */

            if (pluginspec.configuration.restConfigInfo) {
                def rest = pluginspec.configuration.restConfigInfo
                def endpointLabel = rest.endpointLabel ?: 'REST API Endpoint'
                configParams << new Field(
                    name: 'Configuration Name',
                    required: true,
                    documentation: 'Unique name for the configuration',
                    type: 'entry'
                )
                configParams << new Field(
                    name: 'Description',
                    required: false,
                    documentation: 'Configuration description',
                    type: 'entry'
                )
                configParams <<  new Field(
                    name: endpointLabel,
                    required: true,
                    documentation: rest.endpointDescription,
                    type: 'entry',
                )
                configParams << new Field(
                    name: 'Auth Scheme',
                    documentation: 'Authorization scheme for the third-party connection.',
                    type: 'entry',
                    required: true
                )

                //todo auth schemes
                if (rest.hasDebugLevel == 'true') {
                    configParams << new Field(
                        name: "Debug Level",
                        documentation: 'This option sets debug level for logs. If info is selected, only summary information will be shown, for debug, there will be some debug information and for trace the whole requests and responses will be shown.',
                        type: 'entry',
                        required: false
                    )
                }
            }

            if (pluginspec.configuration.checkConnection) {
                configParams << new Field(
                    name: 'Check Connection?',
                    required: false,
                    documentation: 'If checked, the connection endpoint and credentials entered as part of the configuration will be tested. If this option is checked, configuration will not be saved if the test fails.'
                )
            }
            if (pluginspec.configuration.hasDebugLevel) {
                configParams << new Field(
                    name: 'Debug Level',
                    required: false,
                    documentation: 'This option sets debug level for logs. If info is selected, only summary information will be shown, for debug, there will be some debug information and for trace the whole requests and responses will be shown.'
                )
            }

            def configProcedure = new Procedure(name: 'CreateConfiguration', description: '', fields: configParams)

            File configFolder = new File(pluginFolder, "dsl/procedures/CreateConfiguration")
            if (configFolder.exists()) {
                configProcedure = withMetadata(configProcedure, configFolder)
            }

            procedures << configProcedure
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


    Procedure withMetadata(Procedure procedure, File metadataFolder) {
        def procedureHelp = fileByGlob(metadataFolder, "help.y*ml")
        if (procedureHelp && procedureHelp.exists()) {
            def help = new Yaml().load(new FileReader(procedureHelp))
            procedure.preface = help.preface
            procedure.postface = help.postface
            procedure.token = help.token
            procedure.omitDescription = help.omitDescription

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
        } else {
            return null
        }
    }

    String getProcedurePostface(procedureFolder) {
        File file = new File(procedureFolder, "postface.md")
        if (file.exists()) {
            return file.text
        } else {
            return null
        }
    }


    String getProcedureToken(procedureFolder) {
        File file = new File(procedureFolder, "token.txt")
        if (file.exists()) {
            return file.text
        } else {
            return null
        }
    }


    File fileByGlob(File folder, String pattern) {
        def found = new FileNameFinder().getFileNames(folder.absolutePath, pattern)
        if (found.size() > 1) {
            throw new RuntimeException("More than one metadata file found for $pattern in $folder")
        }
        if (!found) {
            return null
        }
        return new File(found.first())
    }


}
