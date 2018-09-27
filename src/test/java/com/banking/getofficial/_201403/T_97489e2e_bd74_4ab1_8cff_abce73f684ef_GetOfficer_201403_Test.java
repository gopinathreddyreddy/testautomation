package com.banking.getofficial._201403;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.http.HttpComponent;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.DefaultExchange;
import org.junit.Assert;
import org.junit.Test;
import org.rsimulator.http.config.HttpSimulatorConfig;
import org.xmlunit.builder.DiffBuilder;
import org.xmlunit.builder.Input;
import org.xmlunit.diff.DefaultComparisonFormatter;
import org.xmlunit.diff.Diff;
import org.xmlunit.diff.Difference;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ResourceBundle;
import java.util.UUID;

public class T_97489e2e_bd74_4ab1_8cff_abce73f684ef_GetOfficer_201403_Test {
    private static final int BUFFER_SIZE = 1000;
    private static final String ENCODING = "UTF-8";
    private CamelContext context;
    private ProducerTemplate template;
    private String requestResource = "/com/banking/getofficial/201403/97489e2e_bd74_4ab1_8cff_abce73f684ef/Request.xml";
    private String responseResource = "/com/banking/getofficial/201403/97489e2e_bd74_4ab1_8cff_abce73f684ef/Response.xml";
    private String simulatorResource = "/com/banking/getofficial/201403/97489e2e_bd74_4ab1_8cff_abce73f684ef/Simulator";
    private ResourceBundle bundle = ResourceBundle.getBundle("testConfigurations");
    private String endPointUri = bundle.getString("serverURL") + "/com/banking/getofficial/201403";
    private String simulatorServerURL = bundle.getString("simulatorServerURL");

    public T_97489e2e_bd74_4ab1_8cff_abce73f684ef_GetOfficer_201403_Test() {
        context = new DefaultCamelContext();
        context.addComponent("http", new HttpComponent());
        template = context.createProducerTemplate();
    }

   // @Test
    public void test() throws Exception {
        Path requestPath = getPath(requestResource);
        Path responsePath = getPath(responseResource);
        String request = read(requestPath);
        String expectedResponse = read(responsePath);
        String rootPath = getPath(simulatorResource).toFile().getAbsolutePath();

        HttpSimulatorConfig.config(rootPath, false, simulatorServerURL);

        Exchange requestExchange = createExchangeWithBody(request);
        Exchange responseExchange = template.send(endPointUri, requestExchange);

        String response = responseExchange.getOut().getBody(String.class);

        Diff diff = DiffBuilder.compare(Input.fromString(expectedResponse)).withTest(Input.fromString(response))
                .checkForSimilar()
                .ignoreWhitespace()
                .ignoreComments()
                .build();

        String msg = null;
        if (diff.hasDifferences()) {
            DefaultComparisonFormatter defaultComparisonFormatter = new DefaultComparisonFormatter();
            for (Difference difference : diff.getDifferences()) {
                String description = defaultComparisonFormatter.getDescription(difference.getComparison());
                String controlDetails = defaultComparisonFormatter.getDetails(difference.getComparison().getControlDetails(), null, true);
                String testDetails = defaultComparisonFormatter.getDetails(difference.getComparison().getTestDetails(), null, true);

                msg = new StringBuilder().append("\ndiff: ").append(diff.toString())
                        .append("requestPath: ").append(requestPath)
                        .append("\nrequest: ").append(request)
                        .append("\nresponse: ").append(response)
                        .append("\ndescription: ").append(description)
                        .append("\ncontrolDetails: ").append(controlDetails)
                        .append("\ntestDetails: ").append(testDetails)
                        .toString();
            }
        }
        Assert.assertFalse(msg, diff.hasDifferences());
    }

    private Exchange createExchangeWithBody(String request) {
        Exchange exchange = new DefaultExchange(context, ExchangePattern.InOut);
        exchange.getIn().setHeader(Exchange.CONTENT_TYPE, "application/xml");
        exchange.getIn().setHeader("SESSION_ID", UUID.randomUUID().toString());
        exchange.getIn().setHeader("userId", UUID.randomUUID().toString());
        exchange.getIn().setBody(request);
        return exchange;
    }

    private Path getPath(String resource) throws URISyntaxException {
        return Paths.get(getClass().getResource(resource).toURI());
    }

    private String read(Path path) throws IOException {
        File file = path.toFile();
        BufferedInputStream bis = null;
        try {
            bis = new BufferedInputStream(new FileInputStream(file));
            StringBuilder sb = new StringBuilder();
            byte[] buffer = new byte[BUFFER_SIZE];
            int n = -1;
            while ((n = bis.read(buffer)) > 0) {
                sb.append(new String(buffer, 0, n, ENCODING));
            }
            return sb.toString();
        } finally {
            if (bis != null) {
                bis.close();
            }
        }
    }
}
