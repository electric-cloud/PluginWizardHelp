import com.electriccloud.pluginwizardhelp.DataSlurper
import com.electriccloud.pluginwizardhelp.HelpGenerator
import com.electriccloud.pluginwizardhelp.domain.HelpMetadata
import com.electriccloud.pluginwizardhelp.domain.Procedure
import spock.lang.*

@Unroll
class HelpGeneratorTest extends Specification {
    HelpGenerator generator =  new HelpGenerator()

    def "sorted procedures #order, #expectedOrder, #totalProcedures"() {
        given:
            DataSlurper slurper = new DataSlurper()
            def proceduresOrder = order.collect {
                "procedure ${it}".toString()
            }
            slurper.metadata = new HelpMetadata(
                proceduresOrder: proceduresOrder
            )
            slurper.procedures = totalProcedures.collect {
                new Procedure(name: "procedure ${it}".toString())
            }
            HelpGenerator generator = new HelpGenerator(slurper: slurper)
        when:
            def procedures = generator.commonProcedures()
        then:
            def expectedProcedures = expectedOrder.collect {
                "procedure ${it}".toString()
            }
            assert procedures.collect { it.name } == expectedProcedures
        where:
            order     | expectedOrder   | totalProcedures
            [2, 1, 3] | [2, 1, 3]       | [1, 2, 3]
            [4, 3]    | [4, 3, 1, 2]    | [1, 2, 3, 4]
    }

    def "link generation"() {
        given:
        HelpGenerator generator = new HelpGenerator()
        when:
        def html = generator.markdownToHtml(markdown)
        then:
        assert html =~ /$expected/
        where:
        markdown                      | expected
        markdownWithLink()            | '<a href='
        '[](http://i.ua "title")'     | '<a href="http://i.ua" target="_blank">title</a>'
        '[text](http://i.ua "title")' | '<a href="http://i.ua" target="_blank" title="title">text</a>'
        '[text](http://i.ua)'         | '<a href="http://i.ua" target="_blank">text</a>'
    }


    def "code block"() {
        given:
            def markdown = '''
some text

```
code block
```
'''
        when:
            def html = generator.markdownToHtml(markdown)
        then:
            assert html =~ /<pre>/
    }

    def markdownWithLink() {
        return '''
Some text
http://google.com
'''
    }

    def 'html to adoc'() {
        when:
        def html = '''
Specify comma-separated list of fields (as per example below) that need to be retrieved per work item. <br/>
    If this parameter is left empty all fields will be returned. <br/>
    Example: <b>'System.State, System.IterationPath, Microsoft.VSTS.Common.StateChangeDate, Microsoft.VSTS.Common.Priority'</b>. Refer to <a href="#">https://dev.azure.com/{yourOrganizationName}/_apis/wit/fields</a> to see all the available fields. <br/>
    This parameter cannot be specified with the "Expand Relationships" parameter.'''
        then:
        def adoc = HelpGenerator.htmlToAdoc(html)
        println adoc
    }
    def 'html to adoc long'() {
        when:
        def html = '''
<html>Create microservices in CloudBees CD by importing a Docker Compose file.
<div>
    <ol>
        <li><b>Copy and enter the content of your Docker Compose File (version 3 or greater).</b></li>
        <li><b>Determine how the new microservices will be created in CloudBees CD</b>
            <ul>
                <li><b>Create the microservices individually at the top-level within the project.</b> All microservices will be created at the top-level. Enter the following parameters:
                    <ul>
                        <li>Project Name: Enter the name of the project where the microservices will be created</li>
                    </ul></li>
                <li><b>Create the Microservices within an application in CloudBees CD.</b> All microservices will be created as services within a new application. Enter the following parameters:
                    <ul>
                        <li>Project Name: Enter the name of the project where the new application will be created</li>
                        <li>Create Microservices within and Application: Select the checkbox</li>
                        <li>Application Name:  The name of a new application which will be created in CloudBees CD containing the new services.</li>
                    </ul></li>
            </ul></li>
        <li><b>Optionally map the services to an existing Environment Cluster</b> Select an existing Environment that contains a cluster with EC-Docker configuration details where the new microservices can be deployed. Enter the following parameters:
            <ul>
                <li>Environment Project Name: The project containing the CloudBees CD environment where the services will be deployed.</li>
                <li>Environment Name: The name of the existing environment that contains a cluster where the newly created microservice(s) will be deployed.</li>
                <li>Cluster Name: The name of an existing EC-Docker backed cluster in the environment above where the newly created microservice(s) will be deployed.</li>
            </ul></li>
    </ol>
</div></html>'''
        then:
        def adoc = HelpGenerator.htmlToAdoc(html)
        println adoc
    }
}

