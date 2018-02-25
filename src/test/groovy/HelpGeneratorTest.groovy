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

    def "markdown generation"() {
        given:
            HelpGenerator generator = new HelpGenerator()
        when:
            def html = generator.markdownToHtml(markdown)
        then:
            assert html =~ /$expected/
        where:
            markdown << [markdownWithLink()]
            expected << ['<a href=']

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
}

