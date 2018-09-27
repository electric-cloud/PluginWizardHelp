package com.electriccloud.pluginwizardhelp.commonmark

import org.commonmark.node.Link;
import groovyx.net.http.HTTPBuilder
import static groovyx.net.http.Method.GET
import static groovyx.net.http.ContentType.TEXT
import org.commonmark.node.Node;
import org.commonmark.renderer.NodeRenderer;
import org.commonmark.renderer.html.HtmlNodeRendererContext;
import org.commonmark.renderer.html.HtmlWriter

import java.util.logging.Logger;


public class LinkRenderer implements NodeRenderer {
    private final HtmlWriter html
    Logger logger = Logger.getLogger("")


    LinkRenderer(HtmlNodeRendererContext context) {
        this.html = context.getWriter()
    }

    @Override
    public Set<Class<? extends Node>> getNodeTypes() {
        return Collections.<Class<? extends Node>> singleton(Link.class)
    }

    @Override
    public void render(Node node) {
        Link link = (Link) node
        validateLink(link.destination)

        html.line()
        html.tag("a", [href: link.destination, target: "_blank"])
        if (link.title) {
            html.text(link.title)
        } else {
            html.text(link.destination)
        }
        html.tag("/a")
        html.line()
    }

    private void validateLink(String destination) {
        def http = new HTTPBuilder(destination)
        http.ignoreSSLIssues()
        try {
            http.request(GET, TEXT) { req ->
                response.success = { resp, reader ->
                    logger.info("Link ${destination} is alive")
                }
                response.failure = { resp ->
                    logger.warning("Link ${destination} returned ${resp.status}")
                }
            }
        } catch (Throwable e) {
            logger.warning("Cannot validate link $destination: ${e.message}")
        }
    }

}
