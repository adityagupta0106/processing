package com.serviceplus.form.validation.auaVerification.engine;

import com.serviceplus.form.validation.auaVerification.dto.AuaApiConfigurationDTO;
import com.serviceplus.form.validation.auaVerification.entity.AuaTransactionLog;
import com.serviceplus.form.validation.auaVerification.enums.AadhaarSystemGeneratedType;
import com.serviceplus.form.validation.auaVerification.enums.AuaMappingType;
import com.serviceplus.form.validation.auaVerification.enums.AuaPluginType;
import com.serviceplus.form.validation.auaVerification.model.AuaCryptoContext;
import com.serviceplus.form.validation.auaVerification.service.AuaCryptoService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

@Component
public class AuaRequestBuilder {

    private static final Logger log = LoggerFactory.getLogger(AuaRequestBuilder.class);

    private final AuaCryptoService auaCryptoService;

    public AuaRequestBuilder(AuaCryptoService auaCryptoService) {
        this.auaCryptoService = auaCryptoService;
    }

    public String buildRequest(AuaApiConfigurationDTO api, Map<String, Object> attributes, AuaTransactionLog logEntry) {

        validateApi(api);

        log.info("Building AUA request. apiType={}", api.getApiType());

        AuaApiConfigurationDTO.AuaPayloadDTO requestPayload = api.getRequestPayload();

        if (requestPayload == null || requestPayload.getNodes() == null || requestPayload.getNodes().isEmpty()) {
            throw new IllegalArgumentException("AUA request payload is not configured");
        }

        RequestContext context = new RequestContext(attributes);

        Document document = buildUnsignedXml(requestPayload, api, context, logEntry);

        System.out.println("DEBUG - Unsigned XML:");
        System.out.println(serializeDocument(document));

        /*
         * Process plugin based crypto nodes.
         *
         * The PID XML is already present inside <Data>
         * at this point.
         */
        processPluginNodes(document, requestPayload, api, context, logEntry);

        String finalXml = serializeDocument(document);


        System.out.println("DEBUG - Signed XML:");
        System.out.println(serializeDocument(document));

        log.info("AUA request generated successfully. apiType={}, size={}", api.getApiType(), finalXml.length());

        return finalXml;
    }

    private Document buildUnsignedXml(AuaApiConfigurationDTO.AuaPayloadDTO requestPayload, AuaApiConfigurationDTO api, RequestContext context, AuaTransactionLog logEntry) {

        String rootElement = extractRootElement(requestPayload, api);

        String prefix = api.getPrefix();
        String suffix = api.getSuffix();

        Document document = createDocument();

        Element rootNode;

        if (api.getXmlNamespace() != null && !api.getXmlNamespace().isBlank()) {

            rootNode = document.createElementNS(api.getXmlNamespace(), rootElement);

            rootNode.setAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, XMLConstants.XMLNS_ATTRIBUTE, api.getXmlNamespace());

        } else {
            rootNode = document.createElement(rootElement);
        }

        document.appendChild(rootNode);

        for (AuaApiConfigurationDTO.AuaNodeDTO node : requestPayload.getNodes()) {

            if (node == null || node.getXpath() == null || node.getXpath().isBlank()) {
                continue;
            }

            AuaMappingType mappingType = node.getMappingType();

            /*
             * NONE
             */
            if (mappingType == null || mappingType == AuaMappingType.NONE) {

                log.info("Skipping AUA node. nodeCode={}, mappingType={}", node.getNodeCode(), mappingType);

                continue;
            }

            /*
             * PLUGIN nodes are processed separately
             * because some plugins depend on other
             * generated values.
             */
            if (mappingType == AuaMappingType.PLUGIN) {
                continue;
            }

            /*
             * RESULT and ERROR are response-side mappings.
             */
            if (mappingType == AuaMappingType.RESULT || mappingType == AuaMappingType.ERROR) {
                continue;
            }

            String value = resolveValue(node, context, logEntry, prefix, suffix);

            if (value == null) {

                log.info("No value resolved for AUA node. nodeCode={}, mappingType={}", node.getNodeCode(), mappingType);

                continue;
            }

            addFieldToXml(document, rootNode, node, value);
        }

        return document;
    }

    private String resolveValue(AuaApiConfigurationDTO.AuaNodeDTO node, RequestContext context, AuaTransactionLog logEntry, String prefix, String suffix) {

        AuaMappingType mappingType = node.getMappingType();

        if (mappingType == null) {
            return null;
        }

        if (mappingType == AuaMappingType.STATIC || mappingType == AuaMappingType.ADD_VALUE) {

            if (node.getDefaultValue() == null) {
                return null;
            }

            return node.getDefaultValue();
        }

        if (mappingType == AuaMappingType.DYNAMIC) {
            return resolveDynamicValue(node, context);
        }

        if (mappingType == AuaMappingType.SYSTEM_GENERATED) {
            return resolveSystemGeneratedValue(node,context, logEntry, prefix, suffix);
        }

        if (mappingType == AuaMappingType.PLUGIN) {
            return resolvePluginValue(node, context);
        }

        return null;
    }

    private String resolveDynamicValue(AuaApiConfigurationDTO.AuaNodeDTO node, RequestContext context) {

        if (node.getSourcePath() == null || node.getSourcePath().isBlank()) {
            return null;
        }

        Object value = getAttributeValue(node, context.getAttributes());

        if (value == null) {

            log.info("Dynamic value not found. nodeCode={}, sourcePath={}", node.getNodeCode(), node.getSourcePath());

            return null;
        }

        return String.valueOf(value);
    }

    private String resolveSystemGeneratedValue(AuaApiConfigurationDTO.AuaNodeDTO node,RequestContext context, AuaTransactionLog logEntry, String prefix, String suffix) {

        String systemVariable = node.getSystemVariable();

        if (systemVariable == null || systemVariable.isBlank()) {
            return null;
        }

        AadhaarSystemGeneratedType type;

        try {

            type = AadhaarSystemGeneratedType.valueOf(systemVariable.trim().toUpperCase());

        } catch (IllegalArgumentException e) {

            log.warn("Unsupported AUA system variable. nodeCode={}, systemVariable={}", node.getNodeCode(), systemVariable);

            return null;
        }

        switch (type) {

            case GENERATE_TXN:

                String txn = logEntry.getProviderTxnId();

                if (txn == null || txn.isBlank()) {

                    txn = generateTransactionId(prefix, suffix);

                    logEntry.setProviderTxnId(txn);
                }

                return txn;

            case CURRENT_TIMESTAMP:
                return generateTimestamp(context);

            case SIGN:
                return null;

            default:

                log.info("Unsupported AUA system variable. nodeCode={}, systemVariable={}", node.getNodeCode(), systemVariable);

                return null;
        }
    }

    private String resolvePluginValue(AuaApiConfigurationDTO.AuaNodeDTO node, RequestContext context) {

        String pluginMethodCode = node.getPluginMethodCode();

        if (pluginMethodCode == null || pluginMethodCode.isBlank()) {

            throw new IllegalArgumentException("Plugin method is not configured for node: " + node.getNodeCode());
        }

        AuaPluginType pluginType = AuaPluginType.fromCode(pluginMethodCode);

        if (pluginType == null) {

            throw new IllegalArgumentException("Invalid AUA plugin method: " + pluginMethodCode);
        }

        log.info("AUA plugin resolved. nodeCode={}, plugin={}", node.getNodeCode(), pluginType);

        /*
         * Actual plugin processing is performed in
         * processPluginNodes().
         */
        return null;
    }

    private void processPluginNodes(Document document, AuaApiConfigurationDTO.AuaPayloadDTO requestPayload, AuaApiConfigurationDTO api, RequestContext context, AuaTransactionLog logEntry) {

        if (requestPayload == null || requestPayload.getNodes() == null || requestPayload.getNodes().isEmpty()) {
            return;
        }

        boolean hasSkeyPlugin = false;
        boolean hasDataPlugin = false;
        boolean hasHmacPlugin = false;

        for (AuaApiConfigurationDTO.AuaNodeDTO node : requestPayload.getNodes()) {

            if (node == null || node.getMappingType() != AuaMappingType.PLUGIN) {
                continue;
            }

            String pluginCode = node.getPluginMethodCode();

            if (pluginCode == null || pluginCode.isBlank()) {
                continue;
            }

            AuaPluginType pluginType = AuaPluginType.fromCode(pluginCode);

            if (pluginType == null) {
                continue;
            }

            switch (pluginType) {

                case CREATE_SKEY:
                    hasSkeyPlugin = true;
                    break;

                case CREATE_DATA:
                    hasDataPlugin = true;
                    break;

                case CREATE_HMAC:
                    hasHmacPlugin = true;
                    break;

                default:
                    break;
            }
        }

        if (!hasSkeyPlugin && !hasDataPlugin && !hasHmacPlugin) {
            return;
        }

        Element rootNode = document.getDocumentElement();

        Element dataNode = findDirectChild(rootNode, "Data");

        if (dataNode == null) {

            log.warn("AUA crypto plugins configured but Data node was not found. apiType={}", api.getApiType());

            return;
        }

        Element pidNode = findDirectChild(dataNode, "Pid");

        if (pidNode == null) {

            log.warn("AUA crypto plugins configured but PID node was not found. apiType={}", api.getApiType());

            return;
        }

        /*
         * Serialize only the PID element.
         *
         * This exact XML string is passed to the
         * crypto service for encryption/HMAC.
         */
        String pidXml = serializeElement(pidNode);

        log.info("========== AUA PID XML START ==========");
        log.info("\n{}", pidXml);
        log.info("========== AUA PID XML END ==========");

        log.info("PID XML prepared for AUA plugin processing. apiType={}, size={}", api.getApiType(), pidXml.length());

        AuaCryptoContext cryptoContext = context.getCryptoContext();

        cryptoContext.setPidXml(pidXml);

        try {

            if (hasSkeyPlugin) {

                String skey = auaCryptoService.createSkey(cryptoContext, api);

                log.info("CREATE_SKEY completed. apiType={}, generated={}", api.getApiType(), skey != null);
            }

            if (hasDataPlugin) {

                String encryptedData = auaCryptoService.createData(cryptoContext, api);

                cryptoContext.setEncryptedData(encryptedData);

                log.info("CREATE_DATA completed. apiType={}, generated={}", api.getApiType(), encryptedData != null);
            }

            if (hasHmacPlugin) {

                String encryptedHmac = auaCryptoService.createHmac(cryptoContext, api);

                cryptoContext.setEncryptedHmac(encryptedHmac);

                log.info("CREATE_HMAC completed. apiType={}, generated={}", api.getApiType(), encryptedHmac != null);
            }

            /*
             * Remove the original plaintext Data/Pid.
             */
            rootNode.removeChild(dataNode);

            /*
             * Add encrypted Skey/Data/Hmac.
             */
            addCryptoPayload(document, rootNode, cryptoContext);

            String finalXml = serializeDocument(document);

            log.info("========== AUA FINAL REQUEST XML START ==========");
            log.info("\n{}", finalXml);
            log.info("========== AUA FINAL REQUEST XML END ==========");

            log.info("Final AUA request XML prepared. apiType={}, size={}", api.getApiType(), finalXml.length());


        } finally {

            /*
             * Certificate/temp-file/encrypter cleanup
             * must happen after all three crypto operations.
             */
            auaCryptoService.cleanup(cryptoContext);
        }
    }

    private Object getAttributeValue(AuaApiConfigurationDTO.AuaNodeDTO node, Map<String, Object> attributes) {

        if (attributes == null || attributes.isEmpty()) {
            return null;
        }

        String sourcePath = node.getSourcePath();

        if (sourcePath == null || sourcePath.isBlank()) {
            return null;
        }

        Object value = attributes.get(sourcePath);

        if (value != null) {
            return value;
        }

        for (Map.Entry<String, Object> entry : attributes.entrySet()) {

            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(sourcePath)) {

                return entry.getValue();
            }
        }

        return null;
    }

    private void addFieldToXml(Document document, Element rootNode, AuaApiConfigurationDTO.AuaNodeDTO node, String value) {

        String xpath = node.getXpath();

        if (xpath == null || xpath.isBlank()) {
            return;
        }

        String[] parts = xpath.split("/");

        Element current = rootNode;

        int startIndex = 1;

        /*
         * Example:
         *
         * /Auth/Uid
         *
         * root = Auth
         *
         * Start from Uid.
         */
        if (parts.length > 1 && rootNode.getNodeName().equals(parts[1])) {
            startIndex = 2;
        }

        for (int i = startIndex; i < parts.length; i++) {

            String part = parts[i];

            if (part == null || part.isBlank()) {
                continue;
            }

            /*
             * Attribute
             *
             * /Auth/@uid
             */
            if (part.startsWith("@")) {
                String attributeName = part.substring(1);
                current.setAttribute(attributeName, value);
                return;
            }

            /*
             * Child element
             */
            Element child = findDirectChild(current, part);

            if (child == null) {
                child = createChildElement(document, current, part);
                current.appendChild(child);
            }

            current = child;

            /*
             * Last element receives the value.
             */
            if (i == parts.length - 1) {
                current.setTextContent(value);
                return;
            }
        }
    }

    private Element createChildElement(Document document, Element parent, String name) {

        String namespace = document.getDocumentElement().getNamespaceURI();

        if (namespace != null && !namespace.isBlank()) {
            return document.createElementNS(namespace, name);
        }

        return document.createElement(name);
    }

    private Element findDirectChild(Element parent, String name) {

        NodeList children = parent.getChildNodes();

        for (int i = 0; i < children.getLength(); i++) {

            Node node = children.item(i);

            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }

            Element element = (Element) node;

            if (name.equals(element.getNodeName()) || name.equals(element.getLocalName())) {
                return element;
            }
        }

        return null;
    }

    private void addCryptoPayload(Document document, Element rootNode, AuaCryptoContext context) {

        if (context == null) {
            throw new IllegalArgumentException("AUA crypto context cannot be null");
        }

        if (context.getEncryptedSessionKey() != null && !context.getEncryptedSessionKey().isBlank()) {

            Element skey = createChildElement(document, rootNode, "Skey");

            if (context.getCertificateIdentifier() != null && !context.getCertificateIdentifier().isBlank()) {
                skey.setAttribute("ci", context.getCertificateIdentifier());
            }

            skey.setTextContent(context.getEncryptedSessionKey());

            rootNode.appendChild(skey);
        }

        /*
         * Encrypted Data
         */
        if (context.getEncryptedData() != null && !context.getEncryptedData().isBlank()) {

            Element data = createChildElement(document, rootNode, "Data");
            data.setAttribute("type", "X");
            data.setTextContent(context.getEncryptedData());

            rootNode.appendChild(data);
        }

        /*
         * HMAC
         */
        if (context.getEncryptedHmac() != null && !context.getEncryptedHmac().isBlank()) {

            Element hmac = createChildElement(document, rootNode, "Hmac");
            hmac.setTextContent(context.getEncryptedHmac());
            rootNode.appendChild(hmac);
        }
    }

    private Document createDocument() {

        try {

            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();

            factory.setNamespaceAware(true);

            /*
             * The document is created locally and does not
             * parse external XML, but these settings keep the
             * factory secure if its usage is extended later.
             */
            try {
                factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            } catch (Exception e) {
                log.info("Unable to configure secure XML processing feature", e);
            }

            return factory.newDocumentBuilder().newDocument();

        } catch (Exception e) {

            log.error("Failed to create AUA XML document", e);

            throw new IllegalStateException("Unable to create AUA XML document", e);
        }
    }

    private String serializeDocument(Document document) {

        try {

            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            transformerFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            Transformer transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            transformer.setOutputProperty(OutputKeys.INDENT, "no");
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            StringWriter writer = new StringWriter();
            transformer.transform(new DOMSource(document), new StreamResult(writer));
            return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" + writer;

        } catch (Exception e) {

            log.error("Failed to serialize AUA XML document", e);
            throw new IllegalStateException("Unable to serialize AUA XML document", e);
        }
    }

    private String serializeElement(Element element) {

        try {

            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            transformerFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            Transformer transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            transformer.setOutputProperty(OutputKeys.INDENT, "no");
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            StringWriter writer = new StringWriter();
            transformer.transform(new DOMSource(element), new StreamResult(writer));
            return writer.toString();

        } catch (Exception e) {

            log.error("Failed to serialize AUA XML element", e);
            throw new IllegalStateException("Unable to serialize AUA XML element", e);
        }
    }

    private void validateApi(AuaApiConfigurationDTO api) {

        if (api == null) {
            throw new IllegalArgumentException("AUA API configuration is required");
        }

        if (api.getRequestPayload() == null) {
            throw new IllegalArgumentException("AUA request payload is not configured");
        }
    }

    private String extractRootElement(AuaApiConfigurationDTO.AuaPayloadDTO payload, AuaApiConfigurationDTO api) {

        if (payload.getNodes() != null) {

            for (AuaApiConfigurationDTO.AuaNodeDTO node : payload.getNodes()) {

                if (node == null || node.getXpath() == null) {
                    continue;
                }

                String[] parts = node.getXpath().split("/");

                if (parts.length > 1 && !parts[1].isBlank()) {

                    return parts[1];
                }
            }
        }

        throw new IllegalArgumentException("Unable to determine AUA root element for apiType: " + api.getApiType());
    }

    private String generateTransactionId(String prefix, String suffix) {

        return generateTXN(prefix, suffix);
    }

    private String generateTimestamp(RequestContext context) {
        DateFormat dfm = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        Date date = new Date();

        String pidTimeStamp = dfm.format(date);
        String timestamp = pidTimeStamp.replace(" ", "T");

        context.getCryptoContext().setTimestamp(pidTimeStamp);

        log.info("AUA timestamp generated. pidTimestamp={}, cryptoTimestamp={}",
                timestamp, pidTimeStamp);

        return timestamp;
    }

    private static class RequestContext {

        private final Map<String, Object> attributes;

        private final AuaCryptoContext cryptoContext;

        private RequestContext(Map<String, Object> attributes) {

            this.attributes = attributes == null ? new LinkedHashMap<>() : new LinkedHashMap<>(attributes);

            this.cryptoContext = new AuaCryptoContext();
        }

        private Map<String, Object> getAttributes() {
            return attributes;
        }

        private AuaCryptoContext getCryptoContext() {
            return cryptoContext;
        }
    }

    public String generateTXN(String prefix, String suffix) {

        Random random = new Random();

        long n = (long) (100000000000L + random.nextFloat() * 900000000000L);

        Date date = new Date();

        SimpleDateFormat ddForm = new SimpleDateFormat("dd");
        SimpleDateFormat mmForm = new SimpleDateFormat("MM");
        SimpleDateFormat yyyyForm = new SimpleDateFormat("yyyy");
        SimpleDateFormat hhForm = new SimpleDateFormat("hh");
        SimpleDateFormat minForm = new SimpleDateFormat("mm");
        SimpleDateFormat ssForm = new SimpleDateFormat("ss");
        String strDate = yyyyForm.format(date) + mmForm.format(date) + ddForm.format(date);
        String strTime = hhForm.format(date) + minForm.format(date) + ssForm.format(date);

        return prefix + n + strDate + strTime + suffix;
    }
}