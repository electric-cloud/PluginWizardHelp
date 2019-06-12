import com.electriccloud.pluginwizardhelp.DataSlurper
import spock.lang.*

@Unroll
class DataSlurperTest extends Specification {

    DataSlurper slurper = new DataSlurper("some_folder")

    def "html documentation #formXml"() {
        when:
            def fields = slurper.readFields(formXml)
        then:
            assert fields.size() == 2
            assert fields[0].required
            assert fields[0].name == 'My Field'
            assert fields[0].documentation == 'My doc'

            assert !fields[1].required
            assert fields[1].documentation == 'Here goes some <a href="link" target="_blank">link</a>'
        where:
            formXml << [getFormXmlWithDoc()]
    }

    def "html documentation #doc"() {
        given:
            def xml = """
<editor>
    <formElement>
        <label>test</label>
        <htmlDocumentation>${doc}</htmlDocumentation>
    </formElement>
</editor>
"""
        when:
            def fields = slurper.readFields(xml)
        then:
            assert fields[0].documentation == doc
        where:
            doc << [
            'some test<p>test<img src="image"/></p><br/>another line',
            'some <b>bold</b> text'
            ]
    }

    def "link target"() {
        given:
            def xml = '''
<editor>
    <formElement>
        <label>test</label>
        <htmlDocumentation><a href="link">link</a></htmlDocumentation>
    </formElement>
</editor>
'''
        when:
            def fields = slurper.readFields(xml)
        then:
            assert fields[0].documentation == '<a href="link" target="_blank">link</a>'
    }

    def "procedure metadata from dsl"() {
        when:
        def metadata = slurper.readProcedureMetadataFromDsl(procedureDsl, 'testProcedure')

        then:
        assert metadata.name
        assert metadata.description

        where:
        procedureDsl << [getProcedureDslWithOutputParameters()]
    }

    def "procedure metadata from dsl with output parameters"() {
        when:
        def metadata = slurper.readProcedureMetadataFromDsl(procedureDsl, 'testProcedure')

        then:
        assert metadata.name
        assert metadata.description
        def outputParameters = metadata.outputParameters
        assert outputParameters.size()

        def parameter1 = outputParameters[0]
        assert parameter1.name == 'outputParameter1'
        assert parameter1.description == 'Description of the first outputParameter'

        def parameter2 = outputParameters[1]
        assert parameter2.name == 'outputParameter2'
        assert parameter2.description == 'Description of the second outputParameter'

        where:
        procedureDsl << [getProcedureDslWithOutputParameters()]
    }

    def getFormXmlWithDoc() {
        return '''
<editor>

    <formElement>
        <type>entry</type>
        <label>My Field:</label>
        <documentation>My doc</documentation>
        <required>1</required>
    </formElement>
    <formElement>
        <label>Another Label:</label>
        <htmlDocumentation>Here goes some <a href="link">link</a></htmlDocumentation>
    </formElement>
</editor>
'''
    }

    def getProcedureDslWithOutputParameters(){
        return '''
procedure 'GetAllPlans', description: 'Returns all plans that are available for current user.', {

    step 'GetAllPlans', {
        description = ''
        command = new File(pluginDir, "dsl/procedures/GetAllPlans/steps/GetAllPlans.pl").text
        shell = 'ec-perl'
    }

    formalOutputParameter 'outputParameter1',
        description: 'Description of the first outputParameter'
        
    formalOutputParameter 'outputParameter2',
        description: 'Description of the second outputParameter'
}
'''
    }

}
