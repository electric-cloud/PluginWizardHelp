package com.electriccloud.pluginwizardhelp.commonmark

import org.commonmark.node.Image
import org.commonmark.node.Node
import org.commonmark.renderer.NodeRenderer
import org.commonmark.renderer.html.HtmlNodeRendererContext
import org.commonmark.renderer.html.HtmlWriter

public class ImageRenderer implements NodeRenderer {
    private final HtmlWriter html


    ImageRenderer(HtmlNodeRendererContext context) {
        this.html = context.getWriter()
    }

    @Override
    public Set<Class<? extends Node>> getNodeTypes() {
        return Collections.<Class<? extends Node>> singleton(Image.class)
    }

    @Override
    public void render(Node node) {
        Image image = (Image) node

        String destination = image.getDestination().replaceAll(/'"&quot;/, '')
        if (destination.startsWith("images")) {
            destination = "../../plugins/@PLUGIN_KEY@/" + destination
        }

        def imageAttrs = [src: destination]

        if (image.title) {
            imageAttrs['title'] = image.title
        }

        html.line()
        html.tag("img", imageAttrs, true)
        html.line()
    }


}
