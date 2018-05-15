package com.l7tech.custom.assertions.injectionfilter.server;

import com.google.common.io.ByteStreams;
import com.l7tech.custom.assertions.injectionfilter.InjectionFilterAssertion;
import com.l7tech.policy.assertion.ext.CustomAssertion;
import com.l7tech.policy.assertion.ext.CustomAssertionStatus;
import com.l7tech.policy.assertion.ext.ServiceInvocation;
import com.l7tech.policy.assertion.ext.message.*;
import com.l7tech.policy.assertion.ext.message.format.CustomMessageFormat;
import com.l7tech.policy.assertion.ext.message.format.NoSuchMessageFormatException;
import com.l7tech.policy.assertion.ext.message.knob.CustomHttpHeadersKnob;
import com.l7tech.policy.assertion.ext.message.knob.CustomPartsKnob;
import com.l7tech.policy.assertion.ext.message.knob.NoSuchKnobException;
import com.l7tech.policy.assertion.ext.targetable.CustomMessageTargetableSupport;
import com.l7tech.policy.variable.NoSuchVariableException;
import org.w3c.dom.*;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class InjectionFilterAssertionServiceInvocation extends ServiceInvocation {

    private static final Logger LOGGER = Logger.getLogger(InjectionFilterAssertionServiceInvocation.class.getName());

    /**
     * Number of characters in front of the suspicious code to log when detected.
     */
    private static final int EVIDENCE_MARGIN_BEFORE = 16;

    /**
     * Number of characters behind the suspicious code to log when detected.
     */
    private static final int EVIDENCE_MARGIN_AFTER = 24;

    private static final String EXTRACT_MESSAGE = "Unable to extract message";

    private static final String CANNOT_PARSE = "Cannot parse %s as %s.";

    private static final String MESSAGE_BODY = " message body";




    private InjectionFilterAssertion getAssertion() {
        return (InjectionFilterAssertion) customAssertion;
    }

    /**
     * @see ServiceInvocation#checkRequest(com.l7tech.policy.assertion.ext.message.CustomPolicyContext)
     */
    @Override
    public CustomAssertionStatus checkRequest(CustomPolicyContext customPolicyContext) {

        if (!isAssertionValid(customAssertion)) {
            return CustomAssertionStatus.FAILED;
        }
        final InjectionFilterAssertion assertion = getAssertion();
        final boolean routed = customPolicyContext.isPostRouting();

        if (isRequest() && routed) {
            auditWarn("Unable to protect against code injection attacks - the request has already been routed.");
            return CustomAssertionStatus.FAILED;
        }

        if (isResponse() && !routed) {
            auditInfo("No response body to check because request has not been routed yet.");
            return CustomAssertionStatus.NONE;
        }

        final String key = assertion.getInjectionFilterKey();
        final InjectionFilterCache.FilterEntry filterEntry = InjectionFilterCache.INSTANCE.get(key);

        if (filterEntry != null) {
            return filter(filterEntry, assertion, customPolicyContext);
        } else {
            auditWarn("Unable to find Filter.");
            return CustomAssertionStatus.FAILED;
        }
    }


    CustomAssertionStatus filter(InjectionFilterCache.FilterEntry filterEntry, InjectionFilterAssertion assertion, CustomPolicyContext customPolicyContext){
        final String filterName = filterEntry.getFilterName();
        if (!filterEntry.isFilterEnabled()) {
            auditWarn(String.format("Injection Filter %s has been disabled", filterName));
            return CustomAssertionStatus.FAILED;
        }

        final CustomMessageTargetableSupport cmts = new CustomMessageTargetableSupport(assertion.getTargetMessageVariable());
        final List<Pattern> patterns = filterEntry.getPatterns();

        try {
            final CustomMessage message = customPolicyContext.getTargetMessage(cmts);
            if (isRequest() && assertion.isIncludeURL()) {
                if (!isHttp(message)) {
                    //bug 5290: URL scan configured but applicable only to HTTP requests
                    auditWarn("Unable to scan Request URL. Request is not HTTP");
                } else {
                    final String paramStr = customPolicyContext.expandVariable("${request.url.query}");
                    final CustomAssertionStatus status = scanRequestURL(paramStr, patterns);
                    if (status != CustomAssertionStatus.NONE) {
                        return status;
                    }
                }
            }
            if (assertion.isIncludeBody() && (!isHttp(message) || putAndPost(customPolicyContext))) {
                return scanBody(customPolicyContext, message, assertion.getTargetName(), patterns, filterName);
            } else {
                return CustomAssertionStatus.NONE;
            }
        } catch (NoSuchVariableException e) {
            LOGGER.log(Level.WARNING, "Unable to obtain message", e);
            return CustomAssertionStatus.FAILED;
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Unable to scan message", e);
            return CustomAssertionStatus.FAILED;
        }
    }

    private CustomAssertionStatus scanBody(final CustomPolicyContext customPolicyContext, final CustomMessage message, final String messageDesc, final List<Pattern> patterns, String filterMapName) throws IOException {
        try {
            final CustomContentType contentType = message.getContentType();
            final CustomMessageFormat<Document> xmlFormat = customPolicyContext.getFormats().getXmlFormat();
            final CustomMessageFormat<CustomJsonData> jsonFormat = customPolicyContext.getFormats().getJsonFormat();
            final CustomMessageFormat<InputStream> streamFormat = customPolicyContext.getFormats().getStreamFormat();

            final byte[] bodyBytes = ByteStreams.toByteArray(message.extract(streamFormat));
            if (bodyBytes.length <= 0) {
                auditInfo("No body content to scan.");
                return CustomAssertionStatus.NONE;
            }

            if (contentType.matches("multipart", "form-data")) {
                return scanBodyAsMultipartFormData(message, messageDesc, patterns, filterMapName);
            } else if (message.extract(xmlFormat) != null) {
                return scanBodyAsXml(message, xmlFormat, messageDesc, patterns, filterMapName);
            } else if (message.extract(jsonFormat) != null) {
                return scanBodyAsJson(message, jsonFormat, messageDesc, patterns, filterMapName);
            } else {
                return scanBodyAsText(bodyBytes, messageDesc, contentType.getEncoding(), patterns, filterMapName);
            }
        } catch (NoSuchMessageFormatException e) {
            LOGGER.log(Level.WARNING, "Unable to obtain message format", e);
            return CustomAssertionStatus.FALSIFIED;
        } catch (CustomMessageAccessException e) {
            LOGGER.log(Level.WARNING, EXTRACT_MESSAGE, e);
            return CustomAssertionStatus.FALSIFIED;
        }
    }

    private CustomAssertionStatus scanRequestURL(final String paramStr, final List<Pattern> patterns) {
        final StringBuilder evidence = new StringBuilder();
        final Map<String, String[]> urlParams = getUrlParams(paramStr);
        for (final Map.Entry<String, String[]> entry : urlParams.entrySet()) {
            final String urlParamName = entry.getKey();
            for (final String urlParamValue : entry.getValue()) {
                final String protectionViolated = scan(urlParamValue, patterns, evidence);
                if (protectionViolated != null) {
                    logAndAudit("request URL", evidence, urlParamName, protectionViolated);
                    return CustomAssertionStatus.FALSIFIED;
                }
            }
        }

        return CustomAssertionStatus.NONE;
    }

    /**
     * Scans the whole message body as multipart/form-data.
     *
     * @param message     either a request Message or a response Message
     * @param messageDesc message description
     * @return an assertion status
     * @throws IOException if error in parsing
     */
    private CustomAssertionStatus scanBodyAsMultipartFormData(final CustomMessage message, final String messageDesc, List<Pattern> patterns, String filterName) throws IOException {
        auditInfo(String.format("Scanning %s message body as multipart/form-data", messageDesc));
        try {
            final CustomPartsKnob mimeKnob = message.getKnob(CustomPartsKnob.class);
            final Iterator<CustomPartsKnob.Part> itor = mimeKnob.iterator();
            for (int partPosition = 0; itor.hasNext(); ++partPosition) {
                final String where = messageDesc + " message MIME part " + Integer.toString(partPosition);
                final CustomPartsKnob.Part partInfo = itor.next();
                final CustomContentType partContentType = partInfo.getContentType();
                if (partContentType.isXml()) {
                    auditInfo(String.format("Scanning %s as text/xml.", where));
                    if(checkXML(patterns, filterName, where, partInfo) != null){
                        return checkXML(patterns, filterName, where, partInfo);
                    }

                } else {
                    auditInfo(String.format("Scanning %s as text/xml.", where));
                    if(checknonXML(patterns, filterName, where, partInfo, partContentType) != null){
                        return checknonXML(patterns, filterName, where, partInfo, partContentType);
                    }
                }
            }
        } catch (NoSuchKnobException e) {
            auditWarn(String.format(CANNOT_PARSE, messageDesc + MESSAGE_BODY, "multipart/form-data"));
            return CustomAssertionStatus.FALSIFIED;
        }
        return CustomAssertionStatus.NONE;
    }

    CustomAssertionStatus checkXML(List<Pattern> patterns, String filterName, String where, CustomPartsKnob.Part partInfo) throws IOException {
        try {
            DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
            final Document partXmlDoc = documentBuilder.parse(new InputSource(partInfo.getInputStream()));
            if (scanXmlForCodeInjection(partXmlDoc, where, patterns, filterName)) {
                return CustomAssertionStatus.FALSIFIED;
            }
        } catch (ParserConfigurationException | SAXException e) {
            auditWarn(String.format(CANNOT_PARSE, where, "text/xml"));
            return CustomAssertionStatus.FALSIFIED;
        }

        return null;

    }

    CustomAssertionStatus checknonXML(List<Pattern> patterns, String filterName, String where, CustomPartsKnob.Part partInfo, CustomContentType partContentType){
        try {
            final byte[] partBytes = ByteStreams.toByteArray(partInfo.getInputStream());
            final String partString = new String(partBytes, partContentType.getEncoding());
            final StringBuilder evidence = new StringBuilder();
            final String protectionViolated = scan(partString, patterns, evidence);
            if (protectionViolated != null) {
                logAndAudit(where, evidence, protectionViolated, filterName);
                return CustomAssertionStatus.FALSIFIED;
            }
        } catch (IOException e) {
            auditWarn(String.format(CANNOT_PARSE, where, "text/xml"));
            return CustomAssertionStatus.FALSIFIED;
        }

        return null;

    }

    private CustomAssertionStatus scanBodyAsJson(final CustomMessage message, final CustomMessageFormat<CustomJsonData> jsonFormat, final String messageDesc, final List<Pattern> patterns, String filterMapName) {
        try {
            auditInfo(String.format("Scanning %s message body as application/json.", messageDesc));
            final String where = messageDesc + MESSAGE_BODY;
            final CustomJsonData jsonData = message.extract(jsonFormat);
            if (scanJson(jsonData, where, patterns, filterMapName)) {
                return CustomAssertionStatus.FALSIFIED;
            }
        } catch (InvalidDataException e) {
            LOGGER.log(Level.WARNING, "Invalid Json Data", e);
            return CustomAssertionStatus.FALSIFIED;
        } catch (CustomMessageAccessException e) {
            LOGGER.log(Level.WARNING, EXTRACT_MESSAGE, e);
            return CustomAssertionStatus.FALSIFIED;
        }
        return CustomAssertionStatus.NONE;
    }

    private CustomAssertionStatus scanBodyAsXml(final CustomMessage message, final CustomMessageFormat<Document> xmlFormat, final String messageDesc, final List<Pattern> patterns, String filterMapName) {
        auditInfo(String.format("\"Scanning %s message body as text/xml.", messageDesc));
        final String where = messageDesc + MESSAGE_BODY;
        try {
            final Document doc = message.extract(xmlFormat);
            if (scanXmlForCodeInjection(doc, where, patterns, filterMapName)) {
                return CustomAssertionStatus.FALSIFIED;
            }
        } catch (CustomMessageAccessException e) {
            LOGGER.log(Level.WARNING, EXTRACT_MESSAGE, e);
            return CustomAssertionStatus.FALSIFIED;
        }
        return CustomAssertionStatus.NONE;
    }

    private CustomAssertionStatus scanBodyAsText(final byte[] message, final String messageDesc, final Charset encoding, List<Pattern> patterns, String filterMapName) {
        LOGGER.log(Level.FINE, () -> String.format("Scanning %s message body as text.", messageDesc));

        final String bodyString = new String(message, encoding);
        final StringBuilder evidence = new StringBuilder();
        final String protectionViolated = scan(bodyString, patterns, evidence);

        if (protectionViolated != null) {
            final String where = messageDesc + MESSAGE_BODY;
            logAndAudit(where, evidence, protectionViolated, filterMapName);
            return CustomAssertionStatus.FALSIFIED;
        }

        return CustomAssertionStatus.NONE;
    }

    private boolean scanJson(final CustomJsonData jsonData, final String where, final List<Pattern> patterns, final String filterMapName) throws InvalidDataException {
        final Object o = jsonData.getJsonObject();
        try {
            process(o, patterns);
        } catch (CodeInjectionDetectedException e) {
            logAndAudit(where + " in JSON value", new StringBuilder(e.getEvidence()), e.getProtectionViolated(), filterMapName);
            return true;
        }
        return false;
    }

    private void processString(String value, List<Pattern> patterns) throws CodeInjectionDetectedException {
        final StringBuilder evidence = new StringBuilder(EVIDENCE_MARGIN_BEFORE + 1 + EVIDENCE_MARGIN_AFTER);
        final String protectionViolated = scan(value, patterns, evidence);
        if (protectionViolated != null) {
            throw new CodeInjectionDetectedException(evidence.toString(), protectionViolated);
        }
    }

    private void processList(List<Object> list, List<Pattern> patterns) throws CodeInjectionDetectedException {
        for (final Object o : list) {
            process(o, patterns);
        }
    }

    private void process(Object value, List<Pattern> patterns) throws CodeInjectionDetectedException {
        if (value instanceof Map) {
            processMap((Map<Object, Object>) value, patterns);
        } else if (value instanceof List) {
            processList((List<Object>) value, patterns);
        } else if (value instanceof String) {
            processString((String) value, patterns);
        }
    }

    private void processMap(Map<Object, Object> map, List<Pattern> patterns) throws CodeInjectionDetectedException {
        for (final Map.Entry<Object, Object> entry : map.entrySet()) {
            process(entry.getKey(), patterns);
            process(entry.getValue(), patterns);
        }
    }

    /**
     * Recursively scans an XML node for code injection.
     *
     * @param node XML node to scan
     * @return <code>true</code> if code injection detected
     */
    private boolean scanXmlForCodeInjection(final Node node, final String where, final List<Pattern> patterns, final String filterMapName) {
        final StringBuilder evidence = new StringBuilder(EVIDENCE_MARGIN_BEFORE + 1 + EVIDENCE_MARGIN_AFTER);
        final int type = node.getNodeType();
        switch (type) {
            case Node.DOCUMENT_NODE:
            case Node.ELEMENT_NODE:
                if (recursivelyScanChildren(node, where, patterns, filterMapName)) return true;
                break;
            case Node.ATTRIBUTE_NODE:
            case Node.CDATA_SECTION_NODE:
            case Node.TEXT_NODE:
                final String protectionViolated = scan(node.getNodeValue(), patterns, evidence);
                if (protectionViolated != null) {
                    String nodePath = constructNodePath(node);
                    logAndAudit(where + " in XML node " + nodePath, evidence, protectionViolated, filterMapName);
                    return true;
                }
                break;
            default:
                break;
        }

        return false;
    }

    private boolean recursivelyScanChildren(Node node, String where, List<Pattern> patterns, String filterMapName) {
        final NamedNodeMap attributes = node.getAttributes();
        if (attributes != null) {
            for (int i = 0; i < attributes.getLength(); i++) {
                final Node attribute = attributes.item(i);
                if (scanXmlForCodeInjection(attribute, where, patterns, filterMapName))
                    return true;
            }
        }
        for (Node child = node.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (scanXmlForCodeInjection(child, where, patterns, filterMapName))
                return true;
        }
        return false;
    }


    String constructNodePath(final Node node){
        String nodePath = node.getNodeName();
        if (node.getParentNode() != null) {
            nodePath = node.getParentNode().getNodeName() + File.separator + node.getNodeName();
        } else if (node instanceof Attr) {
            final Element element = ((Attr) node).getOwnerElement();
            if (element != null) {
                nodePath = element.getNodeName() + "@" + node.getNodeName();
            }
        }
        return nodePath;
    }



    private void logAndAudit(String where, StringBuilder evidence, String protectionViolated, String entityName) {
        makeIgnorableCharactersViewableAsUnicode(evidence);

        auditWarn(String.format("%s detected in %s pattern: %s in entity: %s",
                where, evidence.toString(), protectionViolated, entityName));
    }

    /**
     * Scans for code injection pattern.
     *
     * @param s        string to scan
     * @param patterns patterns entity to apply
     * @param evidence for passing back snippet of string surrounding the
     *                 first match (if found), for logging purpose
     * @return the first protection type violated if found (<code>evidence</code> is then populated);
     * <code>null</code> if none found
     */
    private static String scan(final String s, final List<Pattern> patterns, final StringBuilder evidence) {
        String protectionViolated = null;
        int minIndex = -1;

        for (final Pattern pattern : patterns) {
            final StringBuilder tmpEvidence = new StringBuilder();
            final int index = scan(s, pattern, tmpEvidence);
            if (index != -1 && (minIndex == -1 || index < minIndex)) {
                minIndex = index;
                evidence.setLength(0);
                evidence.append(tmpEvidence);
                protectionViolated = pattern.pattern();
            }
        }

        return protectionViolated;
    }

    /**
     * Scans for a string pattern.
     *
     * @param s        string to scan
     * @param pattern  regular expression pattern to search for
     * @param evidence for passing back snippet of string surrounding the first
     *                 (if found) match, for logging purpose
     * @return starting character index if found (<code>evidence</code> is then populated); -1 if not found
     */
    private static int scan(final String s, final Pattern pattern, final StringBuilder evidence) {
        final Matcher matcher = pattern.matcher(s);
        if (matcher.find()) {
            evidence.setLength(0);

            int start = matcher.start() - EVIDENCE_MARGIN_BEFORE;
            if (start <= 0) {
                start = 0;
            } else {
                evidence.append("...");
            }

            int end = matcher.end() + EVIDENCE_MARGIN_AFTER;
            if (end >= s.length()) {
                end = s.length();
                evidence.append(s, start, end);
            } else {
                evidence.append(s, start, end);
                evidence.append("...");
            }

            return matcher.start();
        } else {
            return -1;
        }
    }

    private boolean isRequest() {
        return CustomMessageTargetableSupport.TARGET_REQUEST.equals(getAssertion().getTargetMessageVariable());
    }

    private boolean isResponse() {
        return CustomMessageTargetableSupport.TARGET_RESPONSE.equals(getAssertion().getTargetMessageVariable());
    }

    private void makeIgnorableCharactersViewableAsUnicode(StringBuilder builder) {
        for (int i = 0; i < builder.length(); i++) {
            final char c = builder.charAt(i);
            if (Character.isIdentifierIgnorable(c)) {
                //%1$04d - %1$ = argument_index, 0 = flags - pad with leading zeros, 4 = width, d = conversion
                builder.replace(i, i + 1, "\\u" + String.format("%1$04d", (int) c));
            }
        }
    }

    private boolean isHttp(CustomMessage message) {
        boolean isHttp;
        try {
            isHttp = message.getKnob(CustomHttpHeadersKnob.class) != null;
        } catch (NoSuchKnobException e) {
            isHttp = false;
        }
        return isHttp;
    }

    private boolean putAndPost(CustomPolicyContext context) {
        final String GET = "GET";
        final String POST = "POST";
        final String PUT = "PUT";

        final String method = context.expandVariable("${request.http.method}");
        return GET.equals(method) || POST.equals(method) || PUT.equals(method);
    }

    private Map<String, String[]> getUrlParams(final String paramStr) {
        final Map<String, String[]> holder = new HashMap<>();
        final StringTokenizer strtok = new StringTokenizer(paramStr, "&");
        while (strtok.hasMoreTokens()) {
            final String nvp = strtok.nextToken();

            final int splitIndex = nvp.indexOf('=');
            if (splitIndex < 0) {
                continue; // ignore parameter with no value
            }

            String name = nvp.substring(0, splitIndex);
            String value = nvp.substring(splitIndex + 1);
            checkContents(name, paramStr);
            checkContents(value, paramStr);

            try {
                name = URLDecoder.decode(name, Charset.defaultCharset().displayName());
                value = URLDecoder.decode(value, Charset.defaultCharset().displayName());
            } catch (UnsupportedEncodingException e) {
                LOGGER.log(Level.FINE, "Unable to decode", e);
            }

            String[] values = holder.get(name);
            if (values == null) {
                values = new String[1];
                values[0] = value;
            } else {
                String[] newValues = new String[values.length + 1];
                System.arraycopy(values, 0, newValues, 0, values.length);
                newValues[values.length] = value;
                values = newValues;
            }

            holder.put(name, values);
        }

        return holder;
    }

    private static void checkContents(String text, String fulltext) {
        final Pattern validcomponent = Pattern.compile("[a-zA-Z0-9$\\-_.+!*'(),%/\\\\:@|{}\\[\\]=;?#\"`^<>~]*");
        final Matcher matcher = validcomponent.matcher(text);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid character in '" + text + "'; '" + fulltext + "'");
        }
    }

    /**
     * Validates the CustomAssertion is of a valid type
     */
    private boolean isAssertionValid(CustomAssertion assertion) {
        if (assertion instanceof InjectionFilterAssertion) {
            return true;
        } else {
            auditWarn(String.format("customAssertion must be of type [{%s}], but it is of type [{%s}] instead",
                    InjectionFilterAssertion.class.getSimpleName(), customAssertion.getClass().getSimpleName()));
            return false;
        }
    }

    public class CodeInjectionDetectedException extends Exception {
        private final String evidence;
        private final String protectionViolated;

        CodeInjectionDetectedException(final String evidence,
                                       final String protectionViolated) {
            this.evidence = evidence;
            this.protectionViolated = protectionViolated;
        }

        private String getEvidence() {
            return evidence;
        }

        private String getProtectionViolated() {
            return protectionViolated;
        }

    }
}
