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

Each procedure can have help files in the procedure directory: preface.md and postface.md. The content will be placed before and after the procedure's documentation.

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
