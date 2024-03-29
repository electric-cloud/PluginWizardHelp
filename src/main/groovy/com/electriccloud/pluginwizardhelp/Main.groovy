package com.electriccloud.pluginwizardhelp

import groovy.cli.picocli.CliBuilder

class Main {
    public static void main(String[] args) {
        def cli = new CliBuilder()
        cli.p(longOpt: 'pluginFolder', type: String, required: false, 'plugin folder')
        cli.o(longOpt: 'out', type: String, required: false, 'output file path')
        cli.rd(longOpt: 'revision-date', type: String, 'custom revision date or -1 for no revision date')
        cli.v(longOpt: 'verbose', type: boolean, 'verbose level')
        cli.c(longOpt: 'community', type: boolean, 'is a communtiy plugin?')
        cli.pt(longOpt: 'partials', type: boolean, 'produce partials?')
        def options = cli.parse(args)

        if (!options || options.help) {
            cli.usage()
            printHelp()
            System.exit(0)
        }
        def path = options.pluginFolder ?: System.getenv('PWD')
        if (!path) {
            println "Cannot deduce the name of the output file"
            cli.usage()
            printHelp()
            System.exit(-1)
        }
        def outPath = options.out ?: deduceOut(path)
        if (!outPath) {
            println "Cannot deduce the name of the output file"
            cli.usage()
            printHelp()
            System.exit(-1)
        }
        String revisionDate = options.rd

        if (!path || !outPath) {
            cli.usage()
            System.exit(-1)
        }

        assert path
        Logger logger = Logger.buildInstance(options.v)
        def generator = new HelpGenerator(pluginFolder: path, revisionDate: revisionDate)
        if (options.community) {
            generator.community = true
        }
        String adoc = generator.generateAdoc()
        File out
        if (options.community) {
            out = new File(path, "README.adoc")
        } else {
            out = new File(path, "help/help.adoc")
        }

        if (options.pt) {
            def partials = generator.generatePartials()
            partials.each { k, v ->
                def file = new File(path, "help/${k}.adoc")
                file.write(v)
                logger.info "Saved adoc into $file.path"
            }
        } else {

            out.write(adoc)
            logger.info "Saved adoc into $out.path"
        }


    }


    static def deduceOut(String path) {
        File pages = new File(path, 'pages')
        if (pages.exists()) {
            def out = pages.listFiles().find { it.name.endsWith('help.xml') }
            if (out)
                return out.absolutePath
        }
        return null
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
