package com.electriccloud.pluginwizardhelp.domain

import org.yaml.snakeyaml.Yaml

class HelpMetadata {
    String proceduresPreface
    String overview
    List supportedVersions
    List excludeProcedures
    String knownIssues
    List<String> proceduresOrder
    List<String> deprecatedProcedures

    static HelpMetadata fromYaml(File yaml) {
        def meta = new Yaml().load(new FileReader(yaml))
        HelpMetadata help = new HelpMetadata(meta)
        return help
    }
}
