package com.l7tech.custom.assertions.injectionfilter.server;

import com.l7tech.custom.assertions.injectionfilter.InjectionFilterAssertion;
import com.l7tech.custom.assertions.injectionfilter.entity.InjectionFilterEntity;
import com.l7tech.custom.assertions.injectionfilter.entity.InjectionFilterSerializer;
import com.l7tech.custom.assertions.injectionfilter.entity.InjectionPatternEntity;
import com.l7tech.policy.assertion.ext.CustomAssertionStatus;
import com.l7tech.policy.assertion.ext.ServiceFinder;
import com.l7tech.policy.assertion.ext.message.CustomContentType;
import com.l7tech.policy.assertion.ext.message.CustomJsonData;
import com.l7tech.policy.assertion.ext.message.CustomMessage;
import com.l7tech.policy.assertion.ext.message.CustomPolicyContext;
import com.l7tech.policy.assertion.ext.message.format.CustomMessageFormat;
import com.l7tech.policy.assertion.ext.message.format.CustomMessageFormatFactory;
import com.l7tech.policy.assertion.ext.message.format.NoSuchMessageFormatException;
import com.l7tech.policy.assertion.ext.message.knob.CustomHttpHeadersKnob;
import com.l7tech.policy.assertion.ext.message.knob.NoSuchKnobException;
import com.l7tech.policy.assertion.ext.store.KeyValueStore;
import com.l7tech.policy.assertion.ext.store.KeyValueStoreServices;
import com.l7tech.policy.assertion.ext.targetable.CustomMessageTargetableSupport;
import com.l7tech.policy.variable.NoSuchVariableException;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.StringReader;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.*;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Test the InjectionFilterAssertion.
 */
@RunWith(MockitoJUnitRunner.Silent.class)
public class InjectionFilterAssertionServiceInvocationTest {
    private InjectionFilterAssertionServiceInvocation server;
    private InjectionFilterAssertion assertion;

    @Mock
    private CustomPolicyContext mockContext;
    @Mock
    private ServiceFinder mockServiceFinder;
    @Mock
    private KeyValueStoreServices mockKeyValStoreServices;
    @Mock
    private KeyValueStore mockKeyValStore;
    @Mock
    private Map mockContextMap;
    @Mock
    private CustomMessage mockMessage;
    @Mock
    private CustomMessageFormatFactory mockMsgFormatFactory;
    @Mock
    private CustomContentType mockContentType;
    @Mock
    private CustomMessageFormat<Document> mockXmlFormat;
    @Mock
    private CustomMessageFormat<CustomJsonData> mockJsonFormat;
    @Mock
    private CustomMessageFormat<InputStream> mockStreamFormat;
    @Mock
    private CustomHttpHeadersKnob mockHeadersKnob;

    @Before
    public void setUp() throws NoSuchMessageFormatException, NoSuchKnobException, NoSuchVariableException {

        //setup entity and a filter map
        InjectionFilterEntity entity = getSqlEntity();
        InjectionFilterSerializer serializer = new InjectionFilterSerializer();
        byte[] serializedEntity = serializer.serialize(entity);

        Map<String, byte[]> filters = new HashMap<>();
        filters.put(entity.getFilterName(), serializedEntity);

        // Setup various mocks necessary for test
        when(mockContext.getContext()).thenReturn(mockContextMap);
        when(mockContext.getFormats()).thenReturn(mockMsgFormatFactory);
        when(mockContext.getTargetMessage(any(CustomMessageTargetableSupport.class))).thenReturn(mockMessage);

        when(mockContextMap.get("serviceFinder")).thenReturn(mockServiceFinder);
        when(mockServiceFinder.lookupService(KeyValueStoreServices.class)).thenReturn(mockKeyValStoreServices);
        when(mockKeyValStoreServices.getKeyValueStore()).thenReturn(mockKeyValStore);

        when(mockMsgFormatFactory.getXmlFormat()).thenReturn(mockXmlFormat);
        when(mockMsgFormatFactory.getJsonFormat()).thenReturn(mockJsonFormat);
        when(mockMsgFormatFactory.getStreamFormat()).thenReturn(mockStreamFormat);

        when(mockMessage.getKnob(CustomHttpHeadersKnob.class)).thenReturn(mockHeadersKnob);
        when(mockMessage.getContentType()).thenReturn(mockContentType);


        when(mockKeyValStore.get(anyString())).thenReturn(serializedEntity);
        when(mockKeyValStore.findAllWithKeyPrefix(InjectionFilterAssertion.INJECTION_FILTER_NAME_PREFIX)).thenReturn(filters);

        InjectionFilterCache.INSTANCE.init(mockKeyValStore);

        assertion = new InjectionFilterAssertion();
        assertion.setInjectionFilterKey(entity.getFilterName());

        server = new InjectionFilterAssertionServiceInvocation();
        server.setCustomAssertion(assertion);
    }

    @After
    public void teardown() {
        InjectionFilterCache.INSTANCE.invalidateAll();
    }

    @Test
    public void testSuccessfulRequestTextFormats() throws Exception {
        assertion.setTargetMessageVariable(CustomMessageTargetableSupport.TARGET_REQUEST);
        assertion.setIncludeBody(true);
        assertion.setIncludeURL(false);

        when(mockContext.expandVariable("${request.http.method}")).thenReturn("POST");

        when(mockMessage.extract(mockXmlFormat)).thenReturn(null);
        when(mockMessage.extract(mockJsonFormat)).thenReturn(null);

        when(mockContentType.matches("multipart", "form-data")).thenReturn(false);
        when(mockContentType.getEncoding()).thenReturn(Charset.defaultCharset());

        //testing with @ : and .
        String message = "Steve's email is: steve@unixwiz.net";
        InputStream stream = new ByteArrayInputStream(message.getBytes(Charset.defaultCharset()));
        when(mockMessage.extract(mockStreamFormat)).thenReturn(stream);

        CustomAssertionStatus result = server.checkRequest(mockContext);
        Assert.assertEquals(CustomAssertionStatus.NONE, result);

        //testing with '
        message = "His name is Ryan O' Neil";
        stream = new ByteArrayInputStream(message.getBytes(Charset.defaultCharset()));
        when(mockMessage.extract(mockStreamFormat)).thenReturn(stream);

        result = server.checkRequest(mockContext);
        Assert.assertEquals(CustomAssertionStatus.NONE, result);


        //testing with ' and .
        message = "8 =X 40' CONTAINERS S.L.A.C";
        stream = new ByteArrayInputStream(message.getBytes(Charset.defaultCharset()));
        when(mockMessage.extract(mockStreamFormat)).thenReturn(stream);

        result = server.checkRequest(mockContext);
        Assert.assertEquals(CustomAssertionStatus.NONE, result);

        //testing with "
        message = "8 =X 40\" CONTAINERS S.L.A.C";
        stream = new ByteArrayInputStream(message.getBytes(Charset.defaultCharset()));
        when(mockMessage.extract(mockStreamFormat)).thenReturn(stream);

        result = server.checkRequest(mockContext);
        Assert.assertEquals(CustomAssertionStatus.NONE, result);


        //testing with \n and \r characters
        message = "This is a story\n" +
                "about a sql injection patterns \t that should not be used \n\r" +
                "if you really don't need";
        stream = new ByteArrayInputStream(message.getBytes(Charset.defaultCharset()));
        when(mockMessage.extract(mockStreamFormat)).thenReturn(stream);

        result = server.checkRequest(mockContext);
        Assert.assertEquals(CustomAssertionStatus.NONE, result);
    }

    @Test
    public void testFailedResponseTextFormats() throws Exception {
        assertion.setTargetMessageVariable(CustomMessageTargetableSupport.TARGET_RESPONSE);
        assertion.setIncludeBody(true);

        when(mockContext.expandVariable("${request.http.method}")).thenReturn("POST");
        when(mockContext.isPostRouting()).thenReturn(true);

        when(mockMessage.extract(mockXmlFormat)).thenReturn(null);
        when(mockMessage.extract(mockJsonFormat)).thenReturn(null);

        when(mockContentType.matches("multipart", "form-data")).thenReturn(false);
        when(mockContentType.getEncoding()).thenReturn(Charset.defaultCharset());

        //testing
        String message = "SELECT fieldlist FROM table WHERE field = 'steve@unixwiz.net'';";
        InputStream stream = new ByteArrayInputStream(message.getBytes(Charset.defaultCharset()));
        when(mockMessage.extract(mockStreamFormat)).thenReturn(stream);

        CustomAssertionStatus result = server.checkRequest(mockContext);
        Assert.assertEquals(CustomAssertionStatus.FALSIFIED, result);

        //testing
        message = "SELECT fieldlist FROM table WHERE field = 'anything' OR 'x'='x';";
        stream = new ByteArrayInputStream(message.getBytes(Charset.defaultCharset()));
        when(mockMessage.extract(mockStreamFormat)).thenReturn(stream);

        result = server.checkRequest(mockContext);
        Assert.assertEquals(CustomAssertionStatus.FALSIFIED, result);

        //testing
        message = "SELECT fieldlist FROM table WHERE field = 'x' AND email IS NULL; --';";
        stream = new ByteArrayInputStream(message.getBytes(Charset.defaultCharset()));
        when(mockMessage.extract(mockStreamFormat)).thenReturn(stream);

        result = server.checkRequest(mockContext);
        Assert.assertEquals(CustomAssertionStatus.FALSIFIED, result);

        //testing
        message = "SELECT fieldlist FROM table WHERE email = 'x' AND userid IS NULL; --';";
        stream = new ByteArrayInputStream(message.getBytes(Charset.defaultCharset()));
        when(mockMessage.extract(mockStreamFormat)).thenReturn(stream);

        result = server.checkRequest(mockContext);
        Assert.assertEquals(CustomAssertionStatus.FALSIFIED, result);

        //testing
        message = "SELECT email, passwd, login_id, full_name FROM table WHERE email = 'x' AND 1=(SELECT COUNT(*) FROM tabname); --';";
        stream = new ByteArrayInputStream(message.getBytes(Charset.defaultCharset()));
        when(mockMessage.extract(mockStreamFormat)).thenReturn(stream);

        result = server.checkRequest(mockContext);
        Assert.assertEquals(CustomAssertionStatus.FALSIFIED, result);

        //testing
        message = "SELECT email, passwd, login_id, full_name FROM members WHERE email = 'x' AND members.email IS NULL; --';";
        stream = new ByteArrayInputStream(message.getBytes(Charset.defaultCharset()));
        when(mockMessage.extract(mockStreamFormat)).thenReturn(stream);

        result = server.checkRequest(mockContext);
        Assert.assertEquals(CustomAssertionStatus.FALSIFIED, result);


        //testing
        message = "SELECT email, passwd, login_id, full_name FROM members WHERE email = 'x' OR full_name LIKE '%Bob%';";
        stream = new ByteArrayInputStream(message.getBytes(Charset.defaultCharset()));
        when(mockMessage.extract(mockStreamFormat)).thenReturn(stream);

        result = server.checkRequest(mockContext);
        Assert.assertEquals(CustomAssertionStatus.FALSIFIED, result);

        //testing
        message = "SELECT email, passwd, login_id, full_name FROM members WHERE email = 'bob@example.com' AND passwd = 'hello123';";
        stream = new ByteArrayInputStream(message.getBytes(Charset.defaultCharset()));
        when(mockMessage.extract(mockStreamFormat)).thenReturn(stream);

        result = server.checkRequest(mockContext);
        Assert.assertEquals(CustomAssertionStatus.FALSIFIED, result);

        //testing
        message = "SELECT email, passwd, login_id, full_name FROM members WHERE email = 'x'; DROP TABLE members; --';";
        stream = new ByteArrayInputStream(message.getBytes(Charset.defaultCharset()));
        when(mockMessage.extract(mockStreamFormat)).thenReturn(stream);

        result = server.checkRequest(mockContext);
        Assert.assertEquals(CustomAssertionStatus.FALSIFIED, result);

        //testing
        message = "SELECT email, passwd, login_id, full_name FROM members WHERE email = 'x'; INSERT INTO members ('email','passwd','login_id','full_name') \n" +
                "        VALUES ('steve@unixwiz.net','hello','steve','Steve Friedl');--';";
        stream = new ByteArrayInputStream(message.getBytes(Charset.defaultCharset()));
        when(mockMessage.extract(mockStreamFormat)).thenReturn(stream);

        result = server.checkRequest(mockContext);
        Assert.assertEquals(CustomAssertionStatus.FALSIFIED, result);

        //testing
        message = "SELECT email, passwd, login_id, full_name\n" +
                "  FROM members\n" +
                " WHERE email = 'x';\n" +
                "      UPDATE members\n" +
                "      SET email = 'steve@unixwiz.net'\n" +
                "      WHERE email = 'bob@example.com';";
        stream = new ByteArrayInputStream(message.getBytes(Charset.defaultCharset()));
        when(mockMessage.extract(mockStreamFormat)).thenReturn(stream);

        result = server.checkRequest(mockContext);
        Assert.assertEquals(CustomAssertionStatus.FALSIFIED, result);

    }

    @Test
    public void testFailedRequestTextFormatsBadUriValues() throws Exception {
        assertion.setTargetMessageVariable(CustomMessageTargetableSupport.TARGET_REQUEST);
        assertion.setIncludeBody(true);
        assertion.setIncludeURL(true);

        when(mockContext.expandVariable("${request.http.method}")).thenReturn("POST");

        when(mockMessage.extract(mockXmlFormat)).thenReturn(null);
        when(mockMessage.extract(mockJsonFormat)).thenReturn(null);

        when(mockContentType.matches("multipart", "form-data")).thenReturn(false);
        when(mockContentType.getEncoding()).thenReturn(Charset.defaultCharset());

        //testing
        String url = URLEncoder.encode("?id=4'", Charset.defaultCharset().displayName());
        String message = "http://www.site.com/news.php?id=4'";
        InputStream stream = new ByteArrayInputStream(message.getBytes(Charset.defaultCharset()));
        when(mockMessage.extract(mockStreamFormat)).thenReturn(stream);
        when(mockContext.expandVariable("${request.url.query}")).thenReturn(url);

        CustomAssertionStatus result = server.checkRequest(mockContext);
        Assert.assertEquals(CustomAssertionStatus.FALSIFIED, result);

        //testing
        url = URLEncoder.encode("id=4 ORDER BY 1-", Charset.defaultCharset().displayName());
        message = "http://www.site.com/news.php?id=4 ORDER BY 1–";
        stream = new ByteArrayInputStream(message.getBytes(Charset.defaultCharset()));
        when(mockMessage.extract(mockStreamFormat)).thenReturn(stream);
        when(mockContext.expandVariable("${request.url.query}")).thenReturn(url);

        result = server.checkRequest(mockContext);
        Assert.assertEquals(CustomAssertionStatus.FALSIFIED, result);

        //testing
        url = URLEncoder.encode("?id=4 UNION ALL SELECT 1,2,3,4–", Charset.defaultCharset().displayName());
        message = "http://www.site.com/news.php?id=4 UNION ALL SELECT 1,2,3,4–";
        stream = new ByteArrayInputStream(message.getBytes(Charset.defaultCharset()));
        when(mockMessage.extract(mockStreamFormat)).thenReturn(stream);
        when(mockContext.expandVariable("${request.url.query}")).thenReturn(url);

        result = server.checkRequest(mockContext);
        Assert.assertEquals(CustomAssertionStatus.FALSIFIED, result);

        //testing
        url = URLEncoder.encode("?lastname=chapple&firstname=mike'+AND+(select+count(*)+from+fake)+%3e0+OR+'1'%3d'1", Charset.defaultCharset().displayName());
        message = "http://myfakewebsite.com/directory.asp?lastname=chapple&firstname=mike'+AND+(select+count(*)+from+fake)+%3e0+OR+'1'%3d'1";
        stream = new ByteArrayInputStream(message.getBytes(Charset.defaultCharset()));
        when(mockMessage.extract(mockStreamFormat)).thenReturn(stream);
        when(mockContext.expandVariable("${request.url.query}")).thenReturn(url);

        result = server.checkRequest(mockContext);
        Assert.assertEquals(CustomAssertionStatus.FALSIFIED, result);

        //testing
        url = URLEncoder.encode("?id=2 and 1=1", Charset.defaultCharset().displayName());
        message = "http://newspaper.com/items.php?id=2 and 1=1";
        stream = new ByteArrayInputStream(message.getBytes(Charset.defaultCharset()));
        when(mockMessage.extract(mockStreamFormat)).thenReturn(stream);
        when(mockContext.expandVariable("${request.url.query}")).thenReturn(url);

        result = server.checkRequest(mockContext);
        Assert.assertEquals(CustomAssertionStatus.FALSIFIED, result);
    }


    @Test
    public void testBlackListValuesFail1() throws Exception {
        assertion.setTargetMessageVariable("message");
        assertion.setIncludeBody(true);

        when(mockContext.expandVariable("${request.http.method}")).thenReturn("POST");

        when(mockMessage.extract(mockXmlFormat)).thenReturn(null);
        when(mockMessage.extract(mockJsonFormat)).thenReturn(null);

        when(mockContentType.matches("multipart", "form-data")).thenReturn(false);
        when(mockContentType.getEncoding()).thenReturn(Charset.defaultCharset());

        //testing
        String message = "Steve's email is: steve@unixwiz.net'";
        InputStream stream = new ByteArrayInputStream(message.getBytes(Charset.defaultCharset()));
        when(mockMessage.extract(mockStreamFormat)).thenReturn(stream);

        CustomAssertionStatus result = server.checkRequest(mockContext);
        Assert.assertEquals(CustomAssertionStatus.FALSIFIED, result);

        //testing
        message = "This is bad email: anything' OR 'x'='x";
        stream = new ByteArrayInputStream(message.getBytes(Charset.defaultCharset()));
        when(mockMessage.extract(mockStreamFormat)).thenReturn(stream);

        result = server.checkRequest(mockContext);
        Assert.assertEquals(CustomAssertionStatus.FALSIFIED, result);

        //testing
        message = "Very bad email: x' AND email IS NULL; --";
        stream = new ByteArrayInputStream(message.getBytes(Charset.defaultCharset()));
        when(mockMessage.extract(mockStreamFormat)).thenReturn(stream);

        result = server.checkRequest(mockContext);
        Assert.assertEquals(CustomAssertionStatus.FALSIFIED, result);

        //testing
        message = "Another bad email: x' AND userid IS NULL; --";
        stream = new ByteArrayInputStream(message.getBytes(Charset.defaultCharset()));
        when(mockMessage.extract(mockStreamFormat)).thenReturn(stream);

        result = server.checkRequest(mockContext);
        Assert.assertEquals(CustomAssertionStatus.FALSIFIED, result);

        //testing
        message = "This is not acceptable : x' AND 1=(SELECT COUNT(*) FROM tabname); --";
        stream = new ByteArrayInputStream(message.getBytes(Charset.defaultCharset()));
        when(mockMessage.extract(mockStreamFormat)).thenReturn(stream);

        result = server.checkRequest(mockContext);
        Assert.assertEquals(CustomAssertionStatus.FALSIFIED, result);
    }


    @Test
    //testing message as request
    public void testJsonValueSuccess() throws Exception {
        assertion.setTargetMessageVariable("jsonMessage");
        assertion.setIncludeBody(true);

        final String message = "{\n" +
                "   \"type\":\"object\",\n" +
                "   \"properties\": {\n" +
                "\t\t\"reportType\": {\"type\":\"string\"},\n" +
                "\t\t\"entityType\": {\"type\":\"string\"},\n" +
                "\t\t\"isIgnoringPagination\": {\"type\":\"boolean\"},\n" +
                "\t\t\"entities\": {\n" +
                "\t\t\t\"type\":\"array\",\n" +
                "\t\t\t\"items\": {\n" +
                "\t\t\t\t\"type\":\"object\",\n" +
                "\t\t\t\t\"properties\":{\"clusterId\":{\"type\":\"string\"}},\n" +
                "\t\t\t\t\"additionalProperties\":false\n" +
                "\t\t\t}\n" +
                "\t\t},\n" +
                "\t\t\"summaryChart\": {\"type\":\"boolean\"},\n" +
                "\t\t\"summaryReport\": {\"type\":\"boolean\"},\n" +
                "\t\t\"reportName\": {\"type\":\"string\"}\n" +
                "\t},\n" +
                "\t\"additionalProperties\":false\n" +
                "}";


        final Map<String, String> props = new LinkedHashMap<>();
        props.put("reportType", new LinkedHashMap<String, String>().put("type", "string"));
        props.put("entityType", new LinkedHashMap<String, String>().put("type", "string"));
        props.put("isIgnoringPagination", new LinkedHashMap<String, String>().put("type", "string"));
        props.put("entities", new LinkedHashMap<String, String>().put("type", "string"));
        props.put("summaryChart", new LinkedHashMap<String, String>().put("type", "string"));
        props.put("summaryReport", new LinkedHashMap<String, String>().put("type", "string"));
        props.put("reportName", new LinkedHashMap<String, String>().put("type", "string"));

        final Map<String, Object> jsonObject = new LinkedHashMap<>();
        jsonObject.put("type", "Object");
        jsonObject.put("properties", props);
        jsonObject.put("additionalProperties", Boolean.FALSE);


        when(mockContext.expandVariable("${request.http.method}")).thenReturn("POST");

        InputStream stream = new ByteArrayInputStream(message.getBytes(Charset.defaultCharset()));
        when(mockMessage.extract(mockStreamFormat)).thenReturn(stream);

        when(mockMessage.extract(mockXmlFormat)).thenReturn(null);
        CustomJsonData mockJsonData = mock(CustomJsonData.class);
        when(mockMessage.extract(mockJsonFormat)).thenReturn(mockJsonData);
        when(mockJsonData.getJsonObject()).thenReturn(jsonObject);

        when(mockContentType.matches("multipart", "form-data")).thenReturn(false);
        when(mockContentType.getEncoding()).thenReturn(Charset.defaultCharset());

        CustomAssertionStatus result = server.checkRequest(mockContext);
        Assert.assertEquals(CustomAssertionStatus.NONE, result);
    }

    @Test
    public void testXmlValueSuccess() throws Exception {
        assertion.setTargetMessageVariable("xmlMessage");
        assertion.setIncludeBody(true);

        final String message = "<message>8 =X 40' CONTAINERS S.L.A.C</message>";
        DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        Document doc = builder.parse(new InputSource(new StringReader(message)));


        when(mockContext.expandVariable("${request.http.method}")).thenReturn("POST");

        InputStream stream = new ByteArrayInputStream(message.getBytes(Charset.defaultCharset()));
        when(mockMessage.extract(mockStreamFormat)).thenReturn(stream);

        when(mockMessage.extract(mockXmlFormat)).thenReturn(doc);
        when(mockMessage.extract(mockJsonFormat)).thenReturn(null);

        when(mockContentType.matches("multipart", "form-data")).thenReturn(false);
        when(mockContentType.getEncoding()).thenReturn(Charset.defaultCharset());

        CustomAssertionStatus result = server.checkRequest(mockContext);
        Assert.assertEquals(CustomAssertionStatus.NONE, result);
    }


    @Test
    public void testFailWhenReqeustIsRouted() {
        assertion.setTargetMessageVariable(CustomMessageTargetableSupport.TARGET_REQUEST);
        assertion.setIncludeBody(true);

        when(mockContext.isPostRouting()).thenReturn(true);

        CustomAssertionStatus result = server.checkRequest(mockContext);
        Assert.assertEquals(CustomAssertionStatus.FAILED, result);
    }

    @Test
    public void testSuccessWhenResponseIsNotRouted() {
        assertion.setTargetMessageVariable(CustomMessageTargetableSupport.TARGET_RESPONSE);
        assertion.setIncludeBody(true);

        when(mockContext.isPostRouting()).thenReturn(false);

        CustomAssertionStatus result = server.checkRequest(mockContext);
        Assert.assertEquals(CustomAssertionStatus.NONE, result);
    }

    @Test
    public void testFailWhenFilterIsNotFound() {
        assertion.setTargetMessageVariable(CustomMessageTargetableSupport.TARGET_REQUEST);
        assertion.setIncludeBody(true);

        when(mockKeyValStore.get(anyString())).thenReturn(null);

        CustomAssertionStatus result = server.checkRequest(mockContext);
        Assert.assertEquals(CustomAssertionStatus.FAILED, result);
    }

    @Test
    public void testSuccessWhenNotScanningBody() {
        assertion.setTargetMessageVariable(CustomMessageTargetableSupport.TARGET_REQUEST);
        assertion.setIncludeBody(false);
        assertion.setIncludeURL(false);

        CustomAssertionStatus result = server.checkRequest(mockContext);
        Assert.assertEquals(CustomAssertionStatus.NONE, result);
    }

    private InjectionFilterEntity getSqlEntity() {
        final InjectionFilterEntity sql = new InjectionFilterEntity();
        final List<InjectionPatternEntity> patterns = new ArrayList<>();

        final InjectionPatternEntity sql_p1 = new InjectionPatternEntity();
        sql_p1.setEnabled(true);
        sql_p1.setName("SQL OR/AND Attack");
        sql_p1.setDescription("' and  ' or");
        sql_p1.setPattern("[\\s]+(and|or)");

        final InjectionPatternEntity sql_p2 = new InjectionPatternEntity();
        sql_p2.setEnabled(true);
        sql_p2.setName("Single line comment");
        sql_p2.setDescription("--comment");
        sql_p2.setPattern("--[^\\r\\n]*");

        final InjectionPatternEntity sql_p3 = new InjectionPatternEntity();
        sql_p3.setEnabled(true);
        sql_p3.setName("Line breaks and tabs");
        sql_p3.setDescription("tab and new line");
        sql_p3.setPattern("(;\\t)|(;\\r)|(;\\n)");

        final InjectionPatternEntity sql_p4 = new InjectionPatternEntity();
        sql_p4.setEnabled(true);
        sql_p4.setName("Multiple line comment");
        sql_p4.setDescription("/*comment*/");
        sql_p4.setPattern("/\\*[\\w\\W]*?(?=\\*/)\\*/");

        final InjectionPatternEntity sql_p5 = new InjectionPatternEntity();
        sql_p5.setEnabled(true);
        sql_p5.setName("Text blocks");
        sql_p5.setDescription("'text'");
        sql_p5.setPattern("'(''|[^'])*'");

        final InjectionPatternEntity sql_p6 = new InjectionPatternEntity();
        sql_p6.setEnabled(true);
        sql_p6.setName("SQL LIKE% Match/Escape Sequences");
        sql_p6.setDescription("'%  '=  ';  '*  '+  ')");
        sql_p6.setPattern("('[\\s]*[\\%]+|'[\\s]*[\\=]+|'[\\s]*;|'[\\s]*[\\+]+|'[\\s]*[\\*]+|'[\\s]*[\\)]+)");

        final InjectionPatternEntity sql_p7 = new InjectionPatternEntity();
        sql_p7.setEnabled(false);
        sql_p7.setName("SQL Keywords Injection");
        sql_p7.setDescription("SQL Keywords");
        sql_p7.setPattern("(insert|as|select|or|procedure|limit|order by|ORDER BY|asc|desc|delete|update|distinct|having|truncate|replace|handler|like)");

        final InjectionPatternEntity sql_p8 = new InjectionPatternEntity();
        sql_p8.setEnabled(true);
        sql_p8.setName("MySQL Commands");
        sql_p8.setDescription("MySQL commands");
        sql_p8.setPattern("\\b(exec sp|exec xp)\\b");

        final InjectionPatternEntity sql_p9 = new InjectionPatternEntity();
        sql_p9.setEnabled(false);
        sql_p9.setName("SQL Metacharacters");
        sql_p9.setDescription("= '--;");
        sql_p9.setPattern("((\\%3D)|(=))[^\\n]*((\\%27)|(\\')|(\\-\\-)|(\\%3B)|(;))");

        final InjectionPatternEntity sql_p10 = new InjectionPatternEntity();
        sql_p10.setEnabled(true);
        sql_p10.setName("Oracle Buffer Overflow");
        sql_p10.setDescription("");
        sql_p10.setPattern("\\b(bfilename|tz_offset|to_timestamp_tz)\\b");

        final InjectionPatternEntity sql_p11 = new InjectionPatternEntity();
        sql_p11.setEnabled(true);
        sql_p11.setName("UNION check");
        sql_p11.setDescription("x' UNION");
        sql_p11.setPattern("\\w*\\d*((\\%27)|(\\')*)\\s*(u|U|(\\%55)|(\\%75))(n|N|(\\%6E)|(\\%4E))(i|I|(\\%69)|(\\%49))(o|O|(\\%6F)|(\\%4F))(n|N|(\\%6E)|(\\%4E))");

        final InjectionPatternEntity sql_p12 = new InjectionPatternEntity();
        sql_p12.setEnabled(true);
        sql_p12.setName("OR check");
        sql_p12.setDescription("x 'or");
        sql_p12.setPattern("\\w*((\\%27)|(\\'))((\\%6F)|o|O|(\\%4F))((\\%72)|r|R|(\\%52))");

        final InjectionPatternEntity sql_p13 = new InjectionPatternEntity();
        sql_p13.setEnabled(false);
        sql_p13.setName("SQL Simple Metacharacters");
        sql_p13.setDescription("'--#");
        sql_p13.setPattern("^('|--|#|\\\\x27|\\\\x23)$");

        final InjectionPatternEntity sql_p14 = new InjectionPatternEntity();
        sql_p14.setEnabled(true);
        sql_p14.setName("Apostrophe and");
        sql_p14.setDescription("'--#");
        sql_p14.setPattern("('\\s+and)");

        final InjectionPatternEntity sql_p15 = new InjectionPatternEntity();
        sql_p15.setEnabled(true);
        sql_p15.setName("Apostrophe AND");
        sql_p15.setDescription("Apostrophe AND");
        sql_p15.setPattern("('\\s+AND)");

        final InjectionPatternEntity sql_p16 = new InjectionPatternEntity();
        sql_p16.setEnabled(true);
        sql_p16.setName("Apostrophe or");
        sql_p16.setDescription("Apostrophe or");
        sql_p16.setPattern("('\\s+or)");

        final InjectionPatternEntity sql_p17 = new InjectionPatternEntity();
        sql_p17.setEnabled(true);
        sql_p17.setName("Apostrophe OR");
        sql_p17.setDescription("Apostrophe OR");
        sql_p17.setPattern("('\\s+OR)");

        final InjectionPatternEntity sql_p18 = new InjectionPatternEntity();
        sql_p18.setEnabled(true);
        sql_p18.setName("Apostrophe and spaces");
        sql_p18.setDescription("Apostrophe and spaces");
        sql_p18.setPattern("'\\s{2}");

        final InjectionPatternEntity sql_p19 = new InjectionPatternEntity();
        sql_p19.setEnabled(true);
        sql_p19.setName("Two apostrophes and spaces");
        sql_p19.setDescription("Two apostrophes and spaces");
        sql_p19.setPattern("'$");

        patterns.add(sql_p1);
        patterns.add(sql_p2);
        patterns.add(sql_p3);
        patterns.add(sql_p4);
        patterns.add(sql_p5);
        patterns.add(sql_p6);
        patterns.add(sql_p7);
        patterns.add(sql_p8);
        patterns.add(sql_p9);
        patterns.add(sql_p10);
        patterns.add(sql_p11);
        patterns.add(sql_p12);
        patterns.add(sql_p13);
        patterns.add(sql_p14);
        patterns.add(sql_p15);
        patterns.add(sql_p16);
        patterns.add(sql_p17);
        patterns.add(sql_p18);
        patterns.add(sql_p19);

        sql.setEnabled(true);
        sql.setDescription("Regex used to protect against SQL Injection");
        sql.setFilterName("SQL Injection Filter");
        sql.setPatterns(patterns);

        return sql;
    }

}