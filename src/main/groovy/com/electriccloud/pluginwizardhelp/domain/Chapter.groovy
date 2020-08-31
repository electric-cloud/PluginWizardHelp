package com.electriccloud.pluginwizardhelp.domain

import com.electriccloud.pluginwizardhelp.HelpGenerator
import com.electriccloud.pluginwizardhelp.exceptions.HelpGeneratorException
import groovy.text.SimpleTemplateEngine
import groovy.transform.Canonical
import groovy.transform.builder.Builder

@Builder
@Canonical
class Chapter {
    String name
    String content
    ChapterPlace place
    HelpGenerator generator
    boolean adoc

    int headerLevel = 1

    String anchor() {
        return name.replaceAll(/\W+/, '')
    }

    String render() {
        String content = generator.markdownToHtml(this.content)
        def extension = adoc ? 'adoc' : 'html'
        return renderTemplate("chapter.$extension", [chapter: this, html: content])
    }

    String renderToc() {
        return renderTemplate("chapterToc.html", [chapter: this])
    }

    static Chapter loadChapter(HelpGenerator generator, File helpFolder, Map declaration) {
        String name = declaration.name
        if (!name) {
            throw new HelpGeneratorException("Chapter does not have a name")
        }
        String file = declaration.file
        if (!file) {
            throw new HelpGeneratorException("Chapter $name does not have a file")
        }
        File chFile = new File(helpFolder, file)
        if (!chFile.exists()) {
            throw new HelpGeneratorException("File $file does not exist for the chapter $name")
        }

        ChapterPlace place = new ChapterPlace()
        String where = declaration.place ?: 'after prerequisites'

        def group = where =~ /(after|before)\s+(\w+)/
        if (group.size()) {
            def matches = group[0]
            if (matches.size() >= 3) {
                place.where = matches[1]
                place.entity = matches[2]
            } else {
                throw new HelpGeneratorException("Wrong chapter placement declaration: ${where}")
            }
        } else {
            throw new HelpGeneratorException("Wrong chapter placement declaration: ${where}")
        }
        int header = declaration.header ?: '1' as int
        Chapter c = builder()
            .name(name)
            .content(chFile.text)
            .headerLevel(header)
            .place(place)
            .generator(generator)
            .build()

        return c
    }

    private String renderTemplate(String templateName, Map bindings) {
        def pageTemplateText = getClass()
            .getClassLoader()
            .getResourceAsStream(templateName)
            .getText("UTF-8")

        def template = new SimpleTemplateEngine().createTemplate(pageTemplateText)
        return template.make(bindings).toString()
    }

}
