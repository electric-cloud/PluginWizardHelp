package com.electriccloud.pluginwizardhelp

import com.electriccloud.pluginwizardhelp.exceptions.InvalidPlugin

enum PluginType {
    GRADLE, PLUGIN_WIZARD

    static PluginType guessPluginType(File pluginFolder) {
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
