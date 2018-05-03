package com.electriccloud.pluginwizardhelp

import groovy.util.CliBuilder

class Main {
    public static void main(String[] args) {
        def cli = new CliBuilder(usage: 'java -jar <plugin-help-builder.jar> [options]')
        cli.help('Print utility help')
        cli.pluginFolder(args: 1, argName: 'pluginFolder', 'Folder with PluginWizard plugin', required: true)
        cli.out(args: 1, 'Output file', required: true)
        cli.revisionDate(args: 1, argName: 'revision-date', 'Custom revision date or -1 for no revision date', required: false)
        def options = cli.parse(args)

        if (!options) {
            cli.usage()
            System.exit(-1)
        }
        if (options.help) {
            cli.usage()
            printHelp()
            System.exit(0)
        }
        def path = options.pluginFolder
        def outPath = options.out
        String revisionDate = options.revisionDate

        if (!path || !outPath) {
            cli.usage()
            System.exit(-1)
        }

        assert path
        def generator = new HelpGenerator(pluginFolder: path, revisionDate: revisionDate)
        String help = generator.generate()
        new Validator().validate(help)
        File output = new File(outPath)
        output.write help
        println "Saved content into ${outPath}"
    }


    static void printHelp() {
        println '''

Required plugin structure:

META-INF/
dsl/
  procedures/
    MyProcedure/
      form.xml
      procedure.dsl
      help.yaml
help/
  metadata.yaml
  chngelog.yaml
  UseCases/
    MyUseCase.md
    
help.yaml:
preface: some preface text
postface: blah blah procedure postface


metadata.yaml:

description: Some plugin description
proceduresPreface: |
  Description for all procedures in markdown
overview: |
    ![alt text]("images/Screenshot.png" "Logo Title Text 1")
supportedVersions:
  - one
  - two
excludeProcedures:
  - Check Cluster
  - Cleanup Cluster - Experimental
knownIssues: |
  ## Describe known issues


changelog.yaml:

1.0.1:
  - First change
  - Second change
1.0.0:
  - First release


'''
    }

}
