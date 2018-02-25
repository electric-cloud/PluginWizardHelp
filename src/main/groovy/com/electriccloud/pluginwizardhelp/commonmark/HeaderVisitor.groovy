package com.electriccloud.pluginwizardhelp.commonmark

import org.commonmark.node.AbstractVisitor
import org.commonmark.node.Heading
import org.commonmark.node.Text

class HeaderVisitor extends AbstractVisitor {
    String firstHeader

    @Override
    public void visit(Heading heading) {
        if (!firstHeader) {
            if (heading.firstChild instanceof Text) {
                Text headingText = (Text) heading.firstChild
                firstHeader = headingText.literal
            }
        }
    }
}
