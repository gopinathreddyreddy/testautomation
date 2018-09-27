package com.service.test;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.http.HttpComponent;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.DefaultExchange;
import org.rsimulator.http.config.HttpSimulatorConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmlunit.builder.DiffBuilder;
import org.xmlunit.builder.Input;
import org.xmlunit.diff.DefaultComparisonFormatter;
import org.xmlunit.diff.Diff;
import org.xmlunit.diff.Difference;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

/**
 * Created by gopinathreddy.p on 6/11/2017.
 */
public class TestAll {
    private Logger log = LoggerFactory.getLogger(TestAll.class);
    private FileUtils fileUtils;
    private CamelContext context;
    private ProducerTemplate template;

    public TestAll() {
        fileUtils = new FileUtils();
        context = new DefaultCamelContext();
        context.addComponent("http", new HttpComponent());
        template = context.createProducerTemplate();
    }

    public void execute(String basePath, String simulatorServerURL, String esServerURL) {
        log.debug("basePath: {}, simulatorServerURL: {}, esServerURL: {}", basePath, simulatorServerURL, esServerURL);
        try {
            List<Path> requests = fileUtils.find(Paths.get(basePath), "Request.xml");
            for (Path requestPath : requests) {
                String request;
                String expectedResponse;
                String rootPath;
                String endPointUri;
                String response;
                try {
                    request = fileUtils.read(requestPath);
                    expectedResponse = getResponse(requestPath);
                    rootPath = getRootPath(requestPath);
                    endPointUri = getEndPointUri(esServerURL, basePath, requestPath);
                    HttpSimulatorConfig.config(rootPath, false, simulatorServerURL);

                    Exchange requestExchange = createExchangeWithBody(request);
                    Exchange responseExchange = template.send(endPointUri, requestExchange);

                    response = responseExchange.getOut().getBody(String.class);

                    Diff diff = DiffBuilder.compare(Input.fromString(expectedResponse)).withTest(Input.fromString(response))
                            .checkForSimilar()
                            .ignoreWhitespace()
                            .ignoreComments()
                            .build();

                    if (diff.hasDifferences()) {
                        log.error("Failure {} {}: {}", requestPath, diff.toString(), response);
                        DefaultComparisonFormatter defaultComparisonFormatter = new DefaultComparisonFormatter();
                        for (Difference difference : diff.getDifferences()) {
                            String description = defaultComparisonFormatter.getDescription(difference.getComparison());
                            String controlDetails = defaultComparisonFormatter.getDetails(difference.getComparison().getControlDetails(), null, true);
                            String testDetails = defaultComparisonFormatter.getDetails(difference.getComparison().getTestDetails(), null, true);

                            log.error("difference\n    requestPath: {}\n    description: {}\n    controlDetails: {}\n    testDetails: {}", new Object[]{requestPath, description, controlDetails, testDetails});
                        }
                    } else {
                        log.info("Success {}", requestPath);
                    }
                } catch (Throwable t) {
                    log.error("Failure {}: {}", requestPath, t.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("Can not execute", e);
        }
    }

    private Exchange createExchangeWithBody(String request) {
        Exchange exchange = new DefaultExchange(context, ExchangePattern.InOut);
        exchange.getIn().setHeader(Exchange.CONTENT_TYPE, "application/xml");
        exchange.getIn().setHeader("user-session-id", UUID.randomUUID().toString());
        exchange.getIn().setHeader("SESSION_ID", UUID.randomUUID().toString());
        exchange.getIn().setHeader("userId", UUID.randomUUID().toString());
        exchange.getIn().setBody(request);
        return exchange;
    }

    private String getResponse(Path requestPath) throws IOException {
        String name = requestPath.getFileName().toString().replaceFirst("Request", "Response");
        Path responsePath = requestPath.resolveSibling(name);
        return fileUtils.read(responsePath);
    }

    private String getRootPath(Path requestPath) {
        String name = requestPath.getFileName().toString().replaceFirst("Request.*", "Simulator");
        Path responsePath = requestPath.resolveSibling(name);
        return responsePath.toFile().getAbsolutePath();
    }

    private String getEndPointUri(String esServerURL, String basePath, Path path) {
        return esServerURL + path.toFile().getParentFile().getAbsolutePath().replaceFirst(basePath, "");
    }

    public static void main(String[] args) {
        String basePath = args[0];
        String simulatorServerURL = args[1];
        String esServerURL = args[2];

        TestAll testAll = new TestAll();
        testAll.execute(basePath, simulatorServerURL, esServerURL);
    }
}
