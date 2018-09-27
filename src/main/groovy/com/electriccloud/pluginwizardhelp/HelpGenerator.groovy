package com.electriccloud.pluginwizardhelp

import com.electriccloud.pluginwizardhelp.commonmark.HeaderVisitor
import com.electriccloud.pluginwizardhelp.commonmark.ImageRenderer
import com.electriccloud.pluginwizardhelp.commonmark.LinkRenderer
import com.electriccloud.pluginwizardhelp.domain.Procedure
import groovy.text.SimpleTemplateEngine
import groovy.text.Template
import org.commonmark.Extension
import org.commonmark.ext.autolink.AutolinkExtension
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.parser.Parser
import org.commonmark.renderer.NodeRenderer
import org.commonmark.renderer.html.HtmlNodeRendererContext
import org.commonmark.renderer.html.HtmlNodeRendererFactory
import org.commonmark.renderer.html.HtmlRenderer
import org.commonmark.node.*

import java.text.SimpleDateFormat;

class HelpGenerator implements Constants {

    String pluginFolder
    String revisionDate

    @Lazy(soft = true)
    DataSlurper slurper = {
        PluginType type = PluginType.guessPluginType(new File(this.pluginFolder))
        if (type == PluginType.GRADLE) {
            def slurper = new GradleDataSlurper(this.pluginFolder)
            return slurper
        }
        else {
            return new DataSlurper(this.pluginFolder)
        }
    }()
    @Lazy
    List<Extension> extensions = {
        Arrays.asList(TablesExtension.create(), AutolinkExtension.create())
    }()
    @Lazy
    Parser markdownParser = {
        Parser.builder().extensions(extensions).build()
    }()
    @Lazy
    HtmlRenderer htmlRenderer = {
        HtmlRenderer
            .builder()
            .extensions(extensions)
            .nodeRendererFactory(new HtmlNodeRendererFactory() {
            public NodeRenderer create(HtmlNodeRendererContext context) {
                return new ImageRenderer(context)
            }
        })
            .nodeRendererFactory(new HtmlNodeRendererFactory() {
            public NodeRenderer create(HtmlNodeRendererContext context) {
                return new LinkRenderer(context)
            }
        })
            .build()
    }()

    @Lazy
    String revisionDateFormat = {
        if (revisionDate == 'false') {
            def date = new Date()
            SimpleDateFormat format = new SimpleDateFormat("MMMMMMMMM dd, yyyy")
            return format.format(date)

        }
        else if (revisionDate == "-1") {
            return ""
        }
        else {
            return revisionDate
        }
    }()

    Logger logger = Logger.getInstance()

    String generate() {
        def parameters = [:]
        parameters.with {
            procedures = this.commonProcedures().collect { Procedure proc ->
                grabProcedureParameters(proc)
            }
            toc = generateToc()
            releaseNotes = generateReleaseNotes()
            prerequisites = markdownToHtml(this.slurper.metadata.prerequisites)
            chapters = processCustomChapters(this.slurper.metadata.chapters)
            metadata = this.slurper.metadata
            def configProcedure = getConfigurationProcedure()
            configurationProcedure = configProcedure ? grabProcedureParameters(configProcedure) : null
            proceduresPreface = markdownToHtml(this.slurper.metadata.proceduresPreface)
            overview = markdownToHtml(this.slurper.metadata.overview)
            supportedVersions = this.slurper.metadata.supportedVersions
            changelog = this.slurper.changelog
            useCases = this.generateUseCases()
            knownIssues = markdownToHtml(this.slurper.metadata.knownIssues)
            revisionDate = this.revisionDateFormat
        }

        def template = getTemplate("page.html")
        String help = template.make(parameters)
        help = cleanup(help)
        help
    }

    def processCustomChapters(List<String> chapters) {
        def retval = []
        for(String content: chapters) {
            def (header, text) = content.split(/\=+/)
            assert header : "Chapter ${content.substring(0, 100)} does not have a header"
            text = markdownToHtml(text)
            header = header.replaceAll(/\n/, '')
            retval << [header: header, text: text]
        }
        return retval
    }

    private Procedure grabProcedureParameters(Procedure proc) {
        if (proc.preface) {
            proc.preface = markdownToHtml(proc.preface)
        }
        if (proc.postface) {
            proc.postface = markdownToHtml(proc.postface)
        }
        proc.fields.each { field ->
            field.additionalDocumentation = markdownToHtml(field.additionalDocumentation)
        }
        if (proc.description) {
            String description = proc.description
            if (description =~ /html/) {
                description = description.replaceAll(/<\/?html>/, '')
            }
            if (isPlainText(description)) {
                description = "<p>$description</p>".toString()
            }
            proc.description = description
        }
        proc
    }


    boolean isPlainText(String text) {
        if (text =~ /<|\/>/) {
            return false
        }
        return true
    }

    private List generateUseCases() {
        def useCases = []
        this.slurper.useCases?.each { markdown ->
            Node node = markdownParser.parse(markdown)
            HeaderVisitor visitor = new HeaderVisitor()
            node.accept(visitor)
            String useCaseName = visitor.firstHeader
            if (useCaseName) {
                String id = useCaseName.replaceAll(/\W/, '')
                useCases << [name: useCaseName, body: markdownToHtml(markdown), id: id]
            } else {
                logger.warning("Use Case does not have a name: " + markdown)
            }
        }
        useCases
    }

    private String generateToc() {
        def params = [:]
        params.with {
            hasConfig = getConfigurationProcedure()
            procedures = commonProcedures()
            proceduresGrouping = this.slurper.metadata.proceduresGrouping
            useCases = generateUseCases()
            knownIssues = this.slurper.metadata.knownIssues
            prerequisites = this.slurper.metadata.prerequisites
            chapters = processCustomChapters(this.slurper.metadata.chapters)
        }

        def template = getTemplate("toc.html")
        String toc = template.make(params)
        return toc
    }

    private String generateReleaseNotes() {

    }

    private Procedure getConfigurationProcedure() {
        this.slurper.procedures.find {
            it.name == 'CreateConfiguration'
        }
    }

    private Template getTemplate(String name) {
        def pageTemplateText = getClass()
            .getClassLoader()
            .getResourceAsStream(name)
            .getText("UTF-8")

        def template = new SimpleTemplateEngine().createTemplate(pageTemplateText)
        return template
    }

    private String markdownToHtml(String markdown) {
        if (markdown == null) {
            return null
        }
        Node document = markdownParser.parse(markdown)
        return htmlRenderer.render(document)
    }

    private String cleanup(String help) {
//        Empty lines of spaces
        help = help.replaceAll(/^\s+$/, '')
        help = help.replaceAll(/[“”]/, '"') // fucking quotes!!!!!
        return help
    }

    boolean isDeprecatedProcedure(procedureName) {
        this.slurper.metadata.deprecatedProcedures.find {
            it.name == procedureName
        }
    }

    List<Procedure> commonProcedures() {
        def exclude = []
        if (this.slurper.metadata.excludeProcedures) {
            exclude = this.slurper.metadata.excludeProcedures
        }
        exclude << CREATE_CONFIGURATION
        exclude << 'EditConfiguration'
        exclude << 'DeleteConfiguration'
        def proceduresOrder = this.slurper.metadata.proceduresOrder

        if (this.slurper.metadata.proceduresGrouping) {
            proceduresOrder = generateProceduresOrder()
            logger.warning("Procedures order will be taken from procedures grouping")
        }
        def orderedProcedures = this.slurper.procedures.findAll {
            !(it.name in exclude)
        }.sort { a, b ->
            if (proceduresOrder) {
                def aIndex = proceduresOrder.indexOf(a.name)
                def bIndex = proceduresOrder.indexOf(b.name)
                if (aIndex != -1 && bIndex != -1) {
                    aIndex <=> bIndex
                } else if (aIndex != -1) {
                    -1
                } else if (bIndex != -1) {
                    1
                } else {
                    a.name <=> b.name
                }
            } else {
                a.name <=> b.name
            }
        }
        return orderedProcedures
    }

    def generateProceduresOrder() {
        Map grouping = this.slurper.metadata.proceduresGrouping
        def order = []
        grouping?.groups?.each { group ->
//            def name = group.name
//            def description = group.description
            def procedures = group.procedures ?: []
            order += procedures
        }
        return order
    }


}
