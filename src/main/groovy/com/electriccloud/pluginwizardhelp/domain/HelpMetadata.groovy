package com.electriccloud.pluginwizardhelp.domain

import com.electriccloud.pluginwizardhelp.exceptions.MetadataNotFound
import org.yaml.snakeyaml.Yaml

class HelpMetadata {
//    The text block before all procedures
    String proceduresPreface

//    Overview (first block after the toc)
    String overview

//    List of the supported third-party versions
    List supportedVersions


    String supportedVersionsText

//    List of the excluded from documentation procedures
    List excludeProcedures

//    Description of the known issues
    String knownIssues

//    Ordered list of the procedures (otherwise will be ordered alphabetically)
    List<String> proceduresOrder

//    List of the deprecated procedures (names)
    List<String> deprecatedProcedures

//    Block for prereqs
    String prerequisites

//    Procedures grouping (see Application Servers plugins)
    Map proceduresGrouping

//    User-defined chapters (.md files in help)
//     List<String> chapters = new ArrayList<String>()
    List<Map> chapters = []

//    Plugin help will have two tables of contents: one main in the beginning of the file and one with the procedures list under Procedures header
    boolean separateProceduresToc

//    description of the procedures will not be included into help
    boolean omitDescription

    static HelpMetadata fromYaml(File yaml) {
        def meta = new Yaml().load(new FileReader(yaml))
        HelpMetadata help = new HelpMetadata(meta)
        return help
    }

}
