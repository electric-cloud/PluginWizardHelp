package com.electriccloud.pluginwizardhelp.domain

import org.yaml.snakeyaml.Yaml

class Changelog {

    List<Version> versions = []

    static Changelog fromYaml(file) {
        Map changes = new Yaml().load(new FileReader(file))
        Changelog changelog = new Changelog()
        changes.each { k, v ->
            Version version = new Version(version: k, changes: v)
            changelog.versions << version
        }
        changelog
    }



    List<Version> getOrderedVersions() {
        versions.sort { a, b ->
            b.version <=> a.version
        }
    }

}
