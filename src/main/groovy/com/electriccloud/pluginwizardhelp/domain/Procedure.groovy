package com.electriccloud.pluginwizardhelp.domain

class Procedure {

    String name
    String description
    @Lazy String id = {name.replaceAll(/\s/, '')}()
    List<Field> fields
    String preface
    String postface
//    Token to show after the procedure name in toc (e.g. "Cluster Only")
    String token
    Map additionalFieldsDoc
    String procedure
    boolean deprecated
}
