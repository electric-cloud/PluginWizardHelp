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
import org.commonmark.node.*;


import java.util.logging.Logger

class HelpGenerator {

    String pluginFolder
    @Lazy(soft = true)
    DataSlurper slurper = { new DataSlurper(this.pluginFolder) }()
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

    Logger logger = Logger.getLogger("")

    String generate() {
        def parameters = [:]
        parameters.with {
            procedures = this.commonProcedures().collect { Procedure proc ->
                grabProcedureParameters(proc)
            }
            toc = generateToc()
            releaseNotes = generateReleaseNotes()
            metadata = this.slurper.metadata
            configurationProcedure = grabProcedureParameters(getConfigurationProcedure())
            proceduresPreface = markdownToHtml(this.slurper.metadata.proceduresPreface)
            overview = markdownToHtml(this.slurper.metadata.overview)
            supportedVersions = this.slurper.metadata.supportedVersions
            changelog = this.slurper.changelog
            useCases = this.generateUseCases()
            knownIssues = markdownToHtml(this.slurper.metadata.knownIssues)
        }

        def template = getTemplate("page.html")
        String help = template.make(parameters)
        help = cleanup(help)
        help
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
        proc
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
            useCases = generateUseCases()
            knownIssues = this.slurper.metadata.knownIssues
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
        exclude << 'CreateConfiguration'
        exclude << 'EditConfiguration'
        exclude << 'DeleteConfiguration'
        def proceduresOrder = this.slurper.metadata.proceduresOrder
        this.slurper.procedures.findAll {
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
    }


}
