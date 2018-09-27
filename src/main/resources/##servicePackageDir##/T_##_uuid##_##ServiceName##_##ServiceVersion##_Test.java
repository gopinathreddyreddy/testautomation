package ##servicePackage##;

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

        import java.io.BufferedReader;
        import java.io.FileInputStream;
        import java.io.IOException;
        import java.io.InputStreamReader;
        import java.net.URISyntaxException;
        import java.nio.file.Path;
        import java.nio.file.Paths;
        import java.util.ResourceBundle;
        import java.util.UUID;
        import java.util.regex.Matcher;
        import java.util.regex.Pattern;
        import org.slf4j.Logger;
        import org.slf4j.LoggerFactory;
/**
 * Created by gopinathreddy.p on 6/27/2017.
 * Test case generation template.
 */
public class T_##_uuid##_##ServiceName##_##ServiceVersion##_Test{
private static final int BUFFER_SIZE=1000;
private static final String ENCODING="UTF-8";
private CamelContext context;
private ProducerTemplate template;
private String requestResource="##requestResource##";
private String responseResource="##responseResource##";
private String simulatorResource="##simulatorResource##";
private ResourceBundle bundle=ResourceBundle.getBundle("testConfigurations");
private String endPointUri=bundle.getString("esServerURL")+"##uri##";
private String simulatorServerURL=bundle.getString("simulatorServerURL");
private String nameSpace="xmlns:xs=\"http://www.w3.org/2001/XMLSchema\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:type=\"xs:string\"";
private String nameSpace2="xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xmlns:xs=\"http://www.w3.org/2001/XMLSchema\" xsi:type=\"xs:string\"";
private boolean exceptionalCases=true;

public T_##_uuid##_##ServiceName##_##ServiceVersion##_Test(){
        context=new DefaultCamelContext();
        context.addComponent("http",new HttpComponent());
        template=context.createProducerTemplate();
        }

@Test
public void test()throws Exception{
        Path requestPath=getPath(requestResource);
        Path responsePath=getPath(responseResource);
        String request=read(requestPath);
        String expectedResponse=read(responsePath);
        String rootPath=getPath(simulatorResource).toFile().getAbsolutePath();

        HttpSimulatorConfig.config(rootPath,false,simulatorServerURL);

        Exchange requestExchange=createExchangeWithBody(request);
        Exchange responseExchange=template.send(endPointUri,requestExchange);

        String response=responseExchange.getOut().getBody(String.class).replace(nameSpace,"").replace(nameSpace2,"");

        Diff diff=DiffBuilder.compare(Input.fromString(expectedResponse)).withTest(Input.fromString(response))
        .checkForSimilar()
        .ignoreWhitespace()
        .ignoreComments()
        .build();

        String msg=null;
        if(diff.hasDifferences()){
        DefaultComparisonFormatter defaultComparisonFormatter=new DefaultComparisonFormatter();
        for(Difference difference:diff.getDifferences()){
        String description=defaultComparisonFormatter.getDescription(difference.getComparison());
        String controlDetails=defaultComparisonFormatter.getDetails(difference.getComparison().getControlDetails(),null,true);
        String testDetails=defaultComparisonFormatter.getDetails(difference.getComparison().getTestDetails(),null,true);

        if(!exceptionalCases){
        exceptionalCases=true;
        }
        exceptionalCases=!isExceptionalCase(difference.toString());

        msg=new StringBuilder().append("\ndiff: ").append(difference.toString())
        .append("requestPath: ").append(requestPath)
        .append("\nrequest: ").append(request)
        .append("\nresponse: ").append(response)
        .append("\ndescription: ").append(description)
        .append("\ncontrolDetails: ").append(controlDetails)
        .append("\ntestDetails: ").append(testDetails)
        .toString();
        }
        }
        Assert.assertFalse(msg,exceptionalCases&&diff.hasDifferences());
        }

private Exchange createExchangeWithBody(String request){
        Exchange exchange=new DefaultExchange(context,ExchangePattern.InOut);
        exchange.getIn().setHeader(Exchange.CONTENT_TYPE,"application/xml;charset=UTF-8");
        exchange.getIn().setHeader("user-session-id",UUID.randomUUID().toString());
        exchange.getIn().setHeader("SESSION_ID",UUID.randomUUID().toString());
        exchange.getIn().setHeader("userId",UUID.randomUUID().toString());
        exchange.getIn().setBody(request);
        return exchange;
        }

private boolean isExceptionalCase(String s){

        if(s.contains("CORRID"))
        return true;
        String patternString=".*>false</.*";
        Pattern patternNew=Pattern.compile(patternString);
        Matcher matcher=patternNew.matcher(s);
        if(s.contains("Expected child 'null' but was '#text' - comparing <NULL> to")&&matcher.matches())
        return true;
        if(!s.contains("Expected text value"))
        return false;
        int quoteIndices[]=new int[4];
        int currentIndex=0;
        while(currentIndex!=4){
        quoteIndices[currentIndex]=s.indexOf('\'',quoteIndices[(currentIndex==0)?0:currentIndex-1]+1);
        currentIndex++;
        }
        String expectedValue=s.substring(quoteIndices[0]+1,quoteIndices[1]);
        String actualValue=s.substring(quoteIndices[2]+1,quoteIndices[3]);
        Pattern pattern=Pattern.compile("((-|\\\\+)?[0-9]+(\\\\.[0-9]+)?)+");

        if(!isNumber(expectedValue)||!isNumber(actualValue)){
        return false;
        }
        if(Double.parseDouble(expectedValue)==Double.parseDouble(actualValue)){
        return true;
        }
        return false;
        }

private Path getPath(String resource)throws URISyntaxException{
        return Paths.get(getClass().getResource(resource).toURI());
        }
private boolean isNumber(String value){
        for(int index=0;index<value.length();index++){
        char ch=value.charAt(index);
        if((ch<'0'||ch>'9')&&(ch!='+')&&(ch!='-')&&(ch!='E')&&(ch!='e')&&(ch!='.')){
        return false;
        }
        }
        if(value.indexOf('-')>0&&(value.indexOf('-')!=value.indexOf('E')+1)&&(value.indexOf('-')!=value.indexOf('e')+1))
        return false;
        return true;
        }
private String read(Path path)throws IOException{
        BufferedReader bis=null;
        try{
        bis=new BufferedReader(new InputStreamReader(new FileInputStream(path.toFile()),"UTF8"));
        StringBuilder sb=new StringBuilder();
        String buffer=new String();
        int n=-1;
        while((buffer=bis.readLine())!=null){
        sb.append(buffer);
        }
        return sb.toString();
        }finally{
        if(bis!=null){
        bis.close();
        }
        }
        }
        }
