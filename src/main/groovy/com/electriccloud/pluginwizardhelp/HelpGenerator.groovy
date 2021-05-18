package com.electriccloud.pluginwizardhelp

import com.electriccloud.pluginwizardhelp.commonmark.HeaderVisitor
import com.electriccloud.pluginwizardhelp.commonmark.ImageRenderer
import com.electriccloud.pluginwizardhelp.commonmark.LinkRenderer
import com.electriccloud.pluginwizardhelp.domain.Changelog
import com.electriccloud.pluginwizardhelp.domain.Chapter
import com.electriccloud.pluginwizardhelp.domain.ChapterPlace
import com.electriccloud.pluginwizardhelp.domain.ChaptersPlacement
import com.electriccloud.pluginwizardhelp.domain.Procedure
import groovy.text.SimpleTemplateEngine
import groovy.text.Template
import groovy.util.logging.Slf4j
import groovy.util.slurpersupport.NodeChild
import nl.jworks.markdown_to_asciidoc.Converter
import org.asciidoctor.Asciidoctor
import org.asciidoctor.OptionsBuilder
import org.commonmark.Extension
import org.commonmark.ext.autolink.AutolinkExtension
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.parser.Parser
import org.commonmark.renderer.NodeRenderer
import org.commonmark.renderer.html.HtmlNodeRendererContext
import org.commonmark.renderer.html.HtmlNodeRendererFactory
import org.commonmark.renderer.html.HtmlRenderer
import org.commonmark.node.*
import org.cyberneko.html.parsers.SAXParser

import java.text.SimpleDateFormat

class HelpGenerator implements Constants {

    String pluginFolder
    String revisionDate
    boolean adoc
    boolean community

    @Lazy(soft = true)
    DataSlurper slurper = {
        PluginType type = PluginType.guessPluginType(new File(this.pluginFolder))
        if (type == PluginType.GRADLE) {
            def slurper = new GradleDataSlurper(this.pluginFolder)
            return slurper
        } else if (type == PluginType.FLOWPDF) {
            return new FlowpdfSlurper(this.pluginFolder)
        } else {
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

        } else if (revisionDate == "-1") {
            return ""
        } else {
            return revisionDate
        }
    }()

    Logger logger = Logger.getInstance()


    Changelog changelogToAdoc(Changelog changelog) {
        for (def v in changelog.versions) {
            v.changes = v.changes.collect { it }
        }
        return changelog
    }

    String generateAdoc() {
        adoc = true
        def parameters = [:]
        parameters.with {
            procedures = this.commonProcedures().collect { Procedure proc ->
                grabProcedureParameters(proc)
            }
            releaseNotes = generateReleaseNotes()
            prerequisites = markdownToAdoc(this.slurper.metadata.prerequisites)
            // chapters = processCustomChapters(this.slurper.metadata.chapters)
            metadata = this.slurper.metadata
            def configProcedure = getConfigurationProcedure()
            configurationProcedure = configProcedure ? grabProcedureParameters(configProcedure) : null
            proceduresPreface = markdownToAdoc(this.slurper.metadata.proceduresPreface)
            overview = markdownToAdoc(this.slurper.metadata.overview)
            supportedVersions = this.slurper.metadata.supportedVersions
            changelog = changelogToAdoc(this.slurper.changelog)
            useCases = this.generateUseCases()
            knownIssues = markdownToAdoc(this.slurper.metadata.knownIssues)
            revisionDate = this.revisionDateFormat
            separateProceduresToc = this.slurper.metadata.separateProceduresToc
            supportedVersionsText = this.slurper.metadata.supportedVersionsText
            chaptersPlacement = generateChaptersPlacement(this.slurper.metadata.chapters)
            pluginKey = this.slurper.metadata.pluginKey
        }

        def template = getTemplate("page.adoc")
        String help = template.make(parameters)
        if (community) {
            help = help.replaceAll(/\Q{CD}/, "CloudBees CD")
            help = help
                .replaceAll(/@PLUGIN_VERSION@/, slurper.metadata.pluginVersion)
                .replaceAll(/@PLUGIN_KEY@/, slurper.metadata.pluginKey)
                .replaceAll(/@PLUGIN_NAME@/, slurper.metadata.pluginKey + '-' + slurper.metadata.pluginVersion)
                .replaceAll(/\Qmenu:Admistration[Plugins]/, 'Adminstration -> Plugins')
        } else {
            help = help.replaceAll(/(?i)CloudBees CD/, '{CD}')
            help = help.replaceAll(/(?i)CloudBees Core/, '{CI}')
        }
        help = cleanup(help)

        adoc = false
        help
    }

    String markdownToAdoc(String md) {
        if (!md) {
            md = ''
        }
        return md
        String retval = Converter.convertMarkdownToAsciiDoc(md)
        def pluginKeyLowercase = this.slurper.metadata.pluginKey.toLowerCase()
        if (community) {
            retval = retval.replaceAll(/image:images\/([\w\/\-.]+?)\[(.+?)\]/) {
                String imagePath = it[1]
                "image::htdocs/images/$imagePath[image]"
            }
        } else {

            retval = retval.replaceAll(/image:images\/([\w\/\-.]+?)\[(.+?)\]/) {
                String imagePath = it[1]
                if (imagePath =~ /[A-Z]/) {
                    logger.warning("The path $imagePath contains invalid symbols")
                }
                imagePath = imagePath.toLowerCase()
                "image::cloudbees-common-sda::cd-plugins/$pluginKeyLowercase/$imagePath[image]"
            }
        }
        return retval
    }

    String generate() {
        def parameters = [:]
        parameters.with {
            procedures = this.commonProcedures().collect { Procedure proc ->
                grabProcedureParameters(proc)
            }
            toc = generateToc()
            releaseNotes = generateReleaseNotes()
            prerequisites = markdownToHtml(this.slurper.metadata.prerequisites)
            // chapters = processCustomChapters(this.slurper.metadata.chapters)
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
            separateProceduresToc = this.slurper.metadata.separateProceduresToc
            supportedVersionsText = this.slurper.metadata.supportedVersionsText
            chaptersPlacement = generateChaptersPlacement(this.slurper.metadata.chapters)
        }

        def template = getTemplate("page.html")
        String help = template.make(parameters)
        help = cleanup(help)

        //cloudbees-common::cd-plugins/ec-jira/createissues/form.png
        help = help.replaceAll(/cloudbees-common::cd-plugins\/[\w-]+\/([\w\/\.-]+)/) {
            String path = it[1]
            return "../../plugins/@PLUGIN_NAME@/images/$path"
            //<img src="../../plugins/@PLUGIN_NAME@/images/getissues/form.png" /><br/>
        }

        help = help.replaceAll(/<img(.+?)>/, '<img$1 />')

        help
    }

    def generateChaptersPlacement(List<Map> chapters) {
        List<Chapter> chapterList = chapters.collect {
            Chapter ch = Chapter.loadChapter(this, new File(pluginFolder, "help"), it)
            ch.adoc = this.adoc
            ch
        }
        ChaptersPlacement placement = new ChaptersPlacement(chapters: chapterList, generator: this)
        return placement
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
            if (adoc) {
                field.documentation = htmlToAdoc field.documentation
            }
        }
        if (proc.description) {
            String description = proc.description
            if (description =~ /html/) {
                if (adoc) {
                    description = htmlToAdoc(description)
                } else {
                    description = description.replaceAll(/<\/?html>/, '')
                }
            }
            if (isPlainText(description)) {
                if (!adoc)
                    description = "<p>$description</p>".toString()
            }
            proc.description = description
        }
        proc
    }


    static htmlToAdoc(String s) {
        if (!s) {
            s = ''
        }
        SAXParser parser = new SAXParser()
        StringWriter sw = new StringWriter()
        def wrapped = "<root>$s</root>"


        Closure walkNode
        int listLevel = 0
        String listBullet = ""
        walkNode = { n ->
            for (def child in n.children()) {
                if (child instanceof groovy.util.slurpersupport.Node) {
                    if (child.name() == "BR") {
                        sw.println("\n\n")
                    } else if (child.name() == "A") {
                        //todo escape
                        def href = child.attributes().getOrDefault("href", "")
                        sw.print(" $href[${child.text()}] ")
                    } else if (child.name() == "B") {
                        sw.print(" *${child.text()}* ")
                    } else if (child.name() == "I") {
                        sw.print "_${child.text()}_"
                    } else if (child.name() == "OL" || child.name() == "UL") {
                        listLevel++
                        //sw.print("\n${'*' * listLevel}")
                        if (child.name() == "OL") {
                            listBullet = "."
                        } else {
                            listBullet = "*"
                        }
                        walkNode(child)

                        listLevel--
                    } else if (child.name() == "LI") {
                        sw.print "\n ${listBullet * listLevel} "
                        walkNode(child)
                    } else {
                        walkNode(child)
                    }
                } else {
                    sw.println(child)
                }
            }
        }

        new XmlSlurper(parser).parseText(s).childNodes().each { target ->
            walkNode(target)
            //for (def child in target.children()) {
            //    if (child instanceof groovy.util.slurpersupport.Node) {
            //        walkNode(child)
            //    }
            //    else {
            //        sw.println(child)
            //    }
            //}
        }

        return sw.toString()
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
            proceduresGrouping = refineProceduresGrouping(this.slurper.metadata.proceduresGrouping)
            useCases = generateUseCases()
            knownIssues = this.slurper.metadata.knownIssues
            prerequisites = this.slurper.metadata.prerequisites
            chaptersPlacement = generateChaptersPlacement(this.slurper.metadata.chapters)
            separateProceduresToc = this.slurper.metadata.separateProceduresToc
            supportedVersions = this.slurper.metadata.supportedVersions
        }

        def template = getTemplate("toc.html")
        String toc = template.make(params)
        return toc
    }


    Map refineProceduresGrouping(Map proceduresGrouping) {
        if (!proceduresGrouping) {
            return null
        }
        def groupedProcedures = []
        def logger = Logger.getInstance()
        List<Procedure> commonProcedures = commonProcedures()
        def groups = proceduresGrouping?.groups?.collect {
            def description = it.description ?: ''
            def name = it.name ?: 'Other'
            groupedProcedures.addAll(it.procedures)
            def procedures = []
            it.procedures.each { procedureName ->
                Procedure proc = commonProcedures.find { it.name == procedureName }
                if (proc) {
                    procedures << proc
                } else {
                    throw new RuntimeException("Procedure $procedureName is not found in the list of procedures(in groups)")
                }
            }

            [name: name, description: description, procedures: procedures]
        }
        def ungroupedProcedures = []
        for (Procedure procedure : commonProcedures) {
            if (!(procedure.name in groupedProcedures)) {
                ungroupedProcedures << procedure.name
            }
        }
        if (ungroupedProcedures.size()) {
            def procedures = []
            ungroupedProcedures.sort().each { procedureName ->
                def proc = commonProcedures.find { it.name == procedureName }
                if (proc) {
                    procedures << proc
                } else {
                    throw new RuntimeException("Procedure $procedureName is not found in the list of procedures")
                }
            }
            def other = [name: 'Other', description: '', procedures: procedures]
            groups << other
        }
        return [groups: groups]
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

    String markdownToHtml(String markdown) {
        if (markdown == null) {
            return null
        }
        //markdown is now adoc
        if (adoc) {
            return markdownToAdoc(markdown)
        }
        Node document = markdownParser.parse(markdown)
        return htmlRenderer.render(document)
    }

    private String cleanup(String help) {
        //        Empty lines of spaces
        help = help.replaceAll(/^\s+$/, '')
        help = help.replaceAll(/[“”]/, '"') // fucking quotes!!!!!

        help.split(/\n/).each {
            if (it =~ /[^\x00-\x7F]/) {
                logger.warning("Found non-ascii chars in line $it")
            }
        }

        help = help.replaceAll(/[^\x00-\x7F]/, ' ')

        return help
    }

    boolean isDeprecatedProcedure(procedureName) {
        this.slurper.metadata.deprecatedProcedures.find {
            it.name == procedureName
        }
    }


    boolean isExcluded(String procedureName) {
        def excluded = this.slurper.metadata.excludeProcedures ?: []
        return excluded.find { it == procedureName }
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
