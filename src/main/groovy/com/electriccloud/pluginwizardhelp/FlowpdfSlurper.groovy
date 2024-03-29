package com.electriccloud.pluginwizardhelp

import com.electriccloud.pluginwizardhelp.domain.Changelog
import com.electriccloud.pluginwizardhelp.domain.Dependency
import com.electriccloud.pluginwizardhelp.domain.Field
import com.electriccloud.pluginwizardhelp.domain.HelpMetadata
import com.electriccloud.pluginwizardhelp.domain.Procedure
import com.electriccloud.pluginwizardhelp.domain.ReportObject
import com.electriccloud.pluginwizardhelp.exceptions.InvalidPlugin
import com.electriccloud.pluginwizardhelp.exceptions.SlurperException
import groovy.json.JsonSlurper
import groovy.util.slurpersupport.NodeChild
import org.yaml.snakeyaml.Yaml
import static org.apache.commons.lang.StringEscapeUtils.escapeHtml

class FlowpdfSlurper extends DataSlurper {
    private static String HELP_FILE_PATH = "help/metadata.yaml"
    private static String HELP_FOLDER = 'help'
    private static String METADATA_GLOB = 'metadata.y*ml'
    private static String CHANGELOG_GLOB = 'changelog.y*ml'
    private static String SPEC_PATH = 'config/pluginspec.yaml'

    ResourceBundle bundle = ResourceBundle.getBundle("messages", Locale.forLanguageTag("en-US"))

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
            metadata.pluginVersion = refineVersion(pluginspec.pluginInfo.version.toString())
            return metadata
        }
    }

    def addMetaChapter(HelpMetadata metadata, String name) {
        def chapter = new File(pluginFolder, "help/${name}.adoc")
        if (chapter.exists()) {
            if (metadata.getProperty(name)) {
                logger.warning("$name in metadata.yaml will be overriden by ${name}.adoc")
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


    List<Dependency> readDependencies() {
        File deps = new File(pluginFolder, 'help/dependencies.json')
        if (!deps.exists()) {
            return []
        }
        def slurper = new JsonSlurper()
        def dependencies = slurper.parse(deps)
        /**
         *

         # {
         #   'moduleName' => 'org.slf4j:jcl-over-slf4j',
         #   'moduleLicense' => 'MIT License',
         #   'moduleUrl' => 'http://www.slf4j.org',
         #   'moduleVersion' => '1.7.25',
         #   'moduleLicenseUrl' => 'http://www.opensource.org/licenses/mit-license.php'
         # },

         */
        return dependencies.collect {
            new Dependency(name: it.moduleName, license: it.moduleLicense ?: '', url: it.moduleUrl ?: '', version: it.moduleVersion, licenseUrl: it.moduleLicenseUrl)
        }
    }


    List<ReportObject> collectReportingData() {
        File spec = new File(pluginFolder, SPEC_PATH)
        def pluginspec = new Yaml().load(spec.text)
        return pluginspec?.devOpsInsight?.supportedReports?.collect {
            def reportObjectType = it.reportObjectType
            List<String> words = reportObjectType.split(/_/)
            words[0] = words[0].capitalize()
            def title = words.join(' ')


            List<Field> fields = it?.parameters?.collect {
                def documentation = it.adoc ?: it.htmlDocumentation ?: it.documentation
                if (!documentation) {
                    logger.warning("Documentation not found for $it.name: $proc.name")
                }
                boolean r = it.required instanceof Boolean ? it.required : it.required == "true" || it.required == '1'
                new Field(
                    name: it.label ?: it.name,
                    required: r,
                    documentation: documentation,
                    type: it.type,
                    adoc: it.adoc,
                )
            }

            return new ReportObject(type: reportObjectType, fields: fields, title: title)
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
                def documentation = it.adoc ?: it.htmlDocumentation ?: it.documentation
                if (!documentation) {
                    logger.warning("Documentation not found for $it.name: $proc.name")
                }
                boolean r = it.required instanceof Boolean ? it.required : it.required == "true" || it.required == '1'
                new Field(
                    name: it.label ?: it.name,
                    required: r,
                    documentation: documentation,
                    type: it.type,
                    adoc: it.adoc,
                )
            }
            if (proc.hasConfig) {
                fields = fields ?: []
                fields.add(0, new Field(
                    name: bundle.getString('configLabel'),
                    documentation: bundle.getString('configDescr'),
                    required: true
                ))
            }
            def outputParameters = proc.outputParameters?.collect {
                [
                    name       : it instanceof LinkedHashMap ? it.name : it.key,
                    description: it instanceof LinkedHashMap ? it.description : it.value,
                    adoc       : it instanceof LinkedHashMap ? it.adoc : '',
                ]
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
                    adoc: it.adoc,
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
                    name: bundle.getString('configLabel'),
                    required: true,
                    documentation: bundle.getString('configDescr'),
                    type: 'entry'
                )
                configParams << new Field(
                    name: bundle.getString('configDescrLabel'),
                    required: false,
                    documentation: bundle.getString('configDescrDescr'),
                    type: 'entry'
                )
                configParams << new Field(
                    name: endpointLabel,
                    required: true,
                    documentation: rest.endpointDescription,
                    type: 'entry',
                )
                configParams << new Field(
                    name: bundle.getString('authSchemeLabel'),
                    documentation:  bundle.getString('authSchemeDescr'),
                    type: 'entry',
                    required: true
                )

                //todo auth schemes
                if (rest.hasDebugLevel == 'true') {
                    configParams << new Field(
                        name: bundle.getString('debugLevelLabel'),
                        documentation: bundle.getString('debugLevelDescr'),
                        type: 'entry',
                        required: false
                    )
                }
            }

            if (pluginspec.configuration.checkConnection) {
                configParams << new Field(
                    name: bundle.getString('checkConnLabel'),
                    required: false,
                    documentation: bundle.getString('checkConnDescr')
                )
            }
            if (pluginspec.configuration.hasDebugLevel) {
                configParams << new Field(
                    name: bundle.getString('debugLevelLabel'),
                    required: false,
                    documentation: bundle.getString('debugLevelDescr')
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
        File file = new File(procedureFolder, "preface.adoc")
        if (file.exists()) {
            return file.text
        } else {
            return null
        }
    }

    String getProcedurePostface(procedureFolder) {
        File file = new File(procedureFolder, "postface.adoc")
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
