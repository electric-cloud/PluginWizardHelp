package com.electriccloud.pluginwizardhelp.domain

import com.electriccloud.pluginwizardhelp.HelpGenerator

class ChaptersPlacement {
    List<Chapter> chapters = []
    HelpGenerator generator

    String place(String where, String what) {
        String ch = chapters.findAll {
            where == it.place.where && what == it.place.entity
        }?.collect {
            it.render()
        }?.join("\n")
        if (ch) {
            println "Found a custom chapter $where $what"
        }
        return ch
    }

    String placeTocHeader(String where, String what) {
        return chapters.findAll {
            where == it.place.where && what == it.place.entity
        }?.collect {
            it.renderToc()
        }?.join("\n")
    }

}
