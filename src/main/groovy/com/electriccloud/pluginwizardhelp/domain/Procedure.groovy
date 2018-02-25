package com.electriccloud.pluginwizardhelp.domain

class Procedure {

    String name
    String description
    @Lazy String id = {name.replaceAll(/\s/, '')}()
    List<Field> fields
    String preface
    String postface
    Map additionalFieldsDoc
    boolean deprecated
}
