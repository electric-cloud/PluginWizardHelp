package com.electriccloud.pluginwizardhelp

import java.util.logging.Logger

class Validator {
    Logger logger = Logger.getLogger("")

    void validate(String help) {
        validateNonAscii(help)
    }

    void validateNonAscii(String help) {
        boolean valid = true
        help.split(/\n/).eachWithIndex{ String entry, int i ->
            if (!(entry =~ /[\x00-\x7F]*/)) {
                valid = false
                logger.info("Non-ascii symbol found at line ${i}: ${entry}")
            }
        }
        if (!valid) {
            throw new RuntimeException("Non-ascii symbols found")
        }
    }
}
