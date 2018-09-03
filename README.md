# PluginWizardHelp

Generates help.xml for PluginWizard based plugins.

# Required Infrastructure

Plugin should have `help` folder with the following content:

metadata.yaml:

```
overview: Plugin overview
excludeProcedures:
  - Procedure Name
deprecatedProcedures:
  - Procedure Name
proceduresOrder:
  - First Procedure
  - Second Procedure
knownIssues: |
 issues description
proceduresPreface: |
  some text to be places before all procedures
supportedVersions:
 - version 1
 - version 2
```

If overview is too big, it can be moved into overview.md in the same folder. overview.md will replace overview key in metadata.yaml.

changelog.yaml:
```
1.0.0:
  - First release
1.1.0:
  - Something was added
  - And something else
```

Folder help may have nested folder named UseCases. It should contain plugin use-cases in .md files, one per file. Use case name is taken from the file header, e.g.:

```
## Simple Use-Case

do this
do that
```

## Procedures

Each procedure can have help files in the procedure directory: preface.md, token.md and postface.md.
The content in preface and postface will be placed before and after the procedure's documentation.
The content in token.md will be placed into TOC after the procedure name and before the procedure documentation.

## Images

Full URL is added automatically to the images, to use image reference in Markdown, one can place the following:

```
![Image](images/MyScreenshot.png)
```
Assuming that MyScreenshot.png is stored under htdocs/images folder.


# Build

```
./gradlew
```

The tool is built into fat jar and can be used right away.
Sample usage:

    java -jar build/libs/plugin-wizard-help-1.0-SNAPSHOT-all.jar --out /Users/imago/Documents/ecloud/plugins/EC-Docker/pages/help.xml --pluginFolder /Users/imago/Documents/ecloud/plugins/EC-Docker


# Gradle Plugins

There are some alterations for Gradle based plugins. Folder help/ still stands, holding metadata and changelog,
project.xml and manifest.xml will be scanned for procedures and their respective forms.

Folder help should be located in root folder **path\EC-Docker\help**

Procedures metadata can be stored in help/procedures/<ProcedureName> folder, e.g. help/procedures/CreateOrUpdateConnectionFactory/preface.md.
