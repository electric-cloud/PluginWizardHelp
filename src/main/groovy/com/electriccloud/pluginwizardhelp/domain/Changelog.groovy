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
            List verA = a.version.tokenize('.')
            List verB = b.version.tokenize('.')

            def commonIndices = Math.min(verA.size(), verB.size())

            for (int i = 0; i < commonIndices; ++i) {
                def numA = verA[i].toInteger()
                def numB = verB[i].toInteger()

                if (numA != numB) {
                    return numB <=> numA
                }
            }

            verB.size() <=> verA.size()
        }
    }

}
