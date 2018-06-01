package com.electriccloud.pluginwizardhelp

import groovy.json.JsonOutput

class Logger {
    enum Level {
        INFO, DEBUG
    }
    private static Logger instance
    private Level level

    static getInstance() {
        if (!this.instance) {
            this.instance = new Logger(level: Level.INFO)
        }
        return this.instance
    }

    static buildInstance(boolean v) {
        Level level
        if (v) {
            level = Level.DEBUG
        } else {
            level = Level.INFO
        }
        this.instance = new Logger(level: level)
        return this.instance
    }

    def log(Level level, Object ... messages) {
        if (this.level >= level) {
            for (message in  messages) {
                if (message instanceof String || message instanceof GString) {
                    println message
                }
                else {
                    println JsonOutput.toJson(message)
                }
            }
        }
    }

    def warning(Object ... messages) {
        print '[WARNING] '
        log(Level.INFO, messages)
    }

    def info(Object ... messages) {
        log(Level.INFO, messages)
    }

    def debug() {
        print "[DEBUG] "
        log(Level.DEBUG, messages)
    }

    def error() {
        print '[ERROR] '
        log(Level.INFO, messages)
    }
}
