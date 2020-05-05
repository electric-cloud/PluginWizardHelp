package com.electriccloud.pluginwizardhelp

import com.electriccloud.pluginwizardhelp.exceptions.InvalidPlugin

enum PluginType {
    GRADLE, PLUGIN_WIZARD, FLOWPDF

    static PluginType guessPluginType(File pluginFolder) {
        if (new File(pluginFolder, "config/pluginspec.yaml").exists()) {
            return FLOWPDF
        }
        File pluginXml = new File(pluginFolder, "META-INF/plugin.xml")
        if (pluginXml.exists()) {
            return PLUGIN_WIZARD
        }

        File gradle = new File(pluginFolder, "build.gradle")
        if (gradle.exists()) {
            return GRADLE
        }

        throw new InvalidPlugin("Plugin ${pluginFolder.absolutePath} is invalid")
    }
}
