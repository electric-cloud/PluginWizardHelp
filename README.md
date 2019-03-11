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

## Custom Chapters (Changed in 1.15)

### Old
Every .md document added into the help/ folder and having a name ending with "Chapter.md" is considered a custom chapter.
The chapters are ordered by their file names and added after the "Overview" and "Prerequisites" chapters.
Each chapter should have a header separated by ======, e.g.

    My Chapter
    ================
    Content

### Current

Additional chapters are described in metadata.yaml in the following format:

    chapters:
      - name: 'Chapter Name'
        file: myChapter.md
        place: before prerequisites

Where file is the name of the file in the help/ folder. Place can be:


* after|before supported_versions
* after|before prerequisites
* after|before overview
* after|before procedures
* after|before use_cases
* after|before known_issues

## Configuration

### excludedProcedures

List of the excluded procedures. Will not be mentioned in the documentation.

### deprecatedProcedures

List of the deprecated procedures. Will have DEPRECATED token after the procedure name.

### knownIssues

Text, describing known issues for the plugin.

### proceduresPreface

Text will be placed before the procedures list.

### supportedVersions

List of the supported versions of the third-party tool.

### proceduresGrouping

Non-default procedures grouping. E.g.

    proceduresGrouping:
      groups:
        - name: WLS Domain Management
          description: These procedures help in managing a WLS Domain.
          procedures:
            - CreateCluster
        - name: WLS Domain Server Administration
          description: These procedures help in administering (i.e., Starting or Stopping Servers) of a WLS Domain.
          procedures:
            - StartAdminServer
            - ResumeServer

Will be used for TOC generation.

### proceduresOrder

Ordered list of the procedure names. Will be used for default TOC. If the ordering is not provided, alphabetical order is used.

### separateProceduresToc

Flag. If set to true, procedures will have a separate TOC under the Procedures header.

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
