package com.service.test

import groovy.io.FileType
import groovy.util.logging.Slf4j
import groovy.xml.XmlUtil

import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.Charset
import java.util.regex.Pattern

/**
 * Created by gopinathreddy.p on 6/11/2017.
 */
@Slf4j
class TestCase {

    class PS {
        String uuid
        String uri
        String request
        String response
        List<BS> bSs = []

        String toString() {
            "uuid: $uuid, uri: $uri, request: $request, reponse: $response, bSs: $bSs"
        }
    }

    class BS {
        String uuid
        String uri
        ByteBuffer request
        ByteBuffer response
        String type

        String toString() {
            "uuid: $uuid, uri: $uri, request: $request, response: $response, type: $type"
        }
    }

    class Auth {
        String uuid
        String uri
        String request
        String response
        List<Auth> auths = []

        String toString() {
            "uuid: $uuid, uri: $uri, request: $request, reponse: $response, auths: $auths"
        }
    }

    class Nonentra_BS {
        String uuid
        String uri
        String request
        String response
        List<Nonentra_BS> nonentra_bses = []

        String toString() {
            "uuid: $uuid, uri: $uri, request: $request, reponse: $response, bSs: $nonentra_bses"
        }
    }
    String authRequest = "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\">\n" +
            "    <soap:Body>\n" +
            "        <sec:Authorize xmlns:sec=\"http://gemi.lansforsakringar.se/SecurityApi-1.0/SecurityApi\">\n" +
            "            <sec:callInfo>.*</sec:callInfo>\n" +
            "            <sec:ticket>.*</sec:ticket>\n" +
            "            <sec:resource>\n" +
            "                <sec:Type>ApplicationResource</sec:Type>\n" +
            "                <sec:Name>.*</sec:Name>\n" +
            "                <sec:Group>BankServices</sec:Group>\n" +
            "            </sec:resource>\n" +
            "        </sec:Authorize>\n" +
            "    </soap:Body>\n" +
            "</soap:Envelope>"

    String authResponse = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
            "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\">\n" +
            "    <soapenv:Header/>\n" +
            "    <soapenv:Body>\n" +
            "        <ns:AuthorizeResponse xmlns:ns=\"http://gemi.lansforsakringar.se/SecurityApi-1.0/SecurityApi\">\n" +
            "            <ns:AuthorizeResult>\n" +
            "                <ns:ResultCode>AccessAllowed</ns:ResultCode>\n" +
            "                <ns:RequiredSecurityLevel>1</ns:RequiredSecurityLevel>\n" +
            "            </ns:AuthorizeResult>\n" +
            "        </ns:AuthorizeResponse>\n" +
            "    </soapenv:Body>\n" +
            "</soapenv:Envelope>"
    String ps_uuid

    private String parseUri(String header, String body, String type) {
        Map<String, String> nameSpaces = [:]

        def m = header =~ /xmlns:(.*?)="([^"]+)/
        m.findAll().each() { match ->
            nameSpaces[match[1]] = match[2]
        }

        m = body =~ /xmlns:(.*?)="([^"]+)/
        m.findAll().each() { match ->
            nameSpaces[match[1]] = match[2]
        }

        m = body =~ /xmlns="([^"]+)/
        m.findAll().each() { match ->
            nameSpaces["default"] = match[1]
        }

        String nameSpace
        if (type == "request") {
            m = body =~ /([a-zA-Z]*[0-9]*):[a-zA-Z\\-]+Request/
        } else {
            m = body =~ /([a-zA-Z]*[0-9]*):[a-zA-Z\\-]+Response/
        }
        if (m.find()) {
            nameSpace = nameSpaces[m[0][1]]
        }

        if (nameSpace == null) {
            nameSpace = nameSpaces["default"]
        }

        nameSpace?.replaceAll("http://www.lansforsakringar.se/ESB/", "")
    }

    private String putInHeader(String element) {
        '<soap:Header xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">' + element + '</soap:Header>'
    }

    private String putInBody(String element) {
        '<soap:Body>' + element + '</soap:Body>'
    }

    private String putInEnvelope(String header, String body) {
        '<soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">' + header + body + '</soap:Envelope>'
    }

    private PS parsePSRequest(String entry, String logEncoding) {
        entry = entry.replaceAll(" *\n *", "")
        String uuid
        String header
        String body

        def m = entry =~ /UUID="([^"]+).*(<[A-Za-z\\-]+:Header.*:Header>).*(<[A-Za-z\\-]+:Body.*:Body>)/
        if (m.find()) {
            uuid = m[0][1]
            header = m[0][2]
            body = m[0][3]
        } else {
            // Some log entries have switched body and header...
            m = entry =~ /UUID="([^"]+).*(<[A-Za-z\\-]+:Body.*:Body>).*(<[A-Za-z\\-]+:Header.*:Header>)/
            if (m.find()) {
                uuid = m[0][1]
                header = m[0][3]
                body = m[0][2]
            }
        }

        if (uuid == null || header == null || body == null) {
            throw new Exception("Can not parse uuid and/or header and/or body")
        }

        header = addcorrid(header, "Reference", uuid);
        String request = ByteBuffer.wrap(body.getBytes(Charset.forName(logEncoding)))
        request = putInEnvelope(header, body)
        String uri = parseUri(header, body, "request")
        if (uri == null) {
            throw new Exception("Can not parse uri")
        }
        ps_uuid = uuid
        new PS(uuid: uuid, uri: uri, request: request)
    }

    private PS parsePSResponse(String entry, String logEncoding) {
        entry = entry.replaceAll(" *\n *", "")
        String uuid
        String header
        String body
        String remaining

        def m = entry =~ /UUID="([^"]+)(.*)/
        if (m.find()) {
            uuid = m[0][1]
            remaining = m[0][2]
        } else {
            throw new Exception("Can not parse uuid")
        }

        m = remaining =~ /(<[A-Za-z\\-]+:Header.*:Header>)(.*)/
        if (m.find()) {
            header = m[0][1]
            remaining = m[0][2]
        } else {
            // Some log entries have skipped the soap header container...
            m = remaining =~ /(<[^>]*LFBResponseHeader.*LFBResponseHeader>)(.*)/
            if (m.find()) {
                header = putInHeader(m[0][1])
                remaining = m[0][2]
            } else {
                // Some log entries have a LFBHeader...
                m = remaining =~ /(<[^>]*LFBHeader.*LFBHeader>)(.*)/
                if (m.find()) {
                    header = putInHeader(m[0][1])
                    remaining = m[0][2]
                }
            }
        }
        if (header == null) {
            throw new Exception("Can not parse header")
        }

        m = remaining =~ /(<[A-Za-z\\-]+:Body.*:Body>)/
        if (m.find()) {
            body = m[0][1]
        } else {
            // Some log entries have skipped the soap body container...
            m = remaining =~ /(.*)..Log>/
            if (m.find()) {
                body = putInBody(m[0][1])
            }
        }
        if (body == null) {
            throw new Exception("Can not parse body")
        }

        String uri = parseUri(header, body, "response")
        if (uri == null) {
            throw new Exception("Can not parse uri")
        }

        header = addcorrid(header, "Id", uuid);
        String response = ByteBuffer.wrap(body.getBytes(Charset.forName(logEncoding)))
        response = putInEnvelope(header, body)
        new PS(uuid: uuid, uri: uri, response: response)
    }

    String addcorrid(String header, String tag, String uuid) {

        def m = header =~ /<([a-z0-9\\-]*:)*$tag\/>/
        def m2 = header =~ /<([a-z0-9\\-]*:)*$tag>.*<\/([a-z0-9\\-]*:)*$tag>/
        String nsp;
        if (m.find()) {
            nsp = m[0][1]
        } else if (m2.find()) {
            nsp = m2[0][1]
        } else {
            if (tag.equals("Reference")) {
                def m3 = header =~ /<([a-z0-9\\-]*:)*LFBHeader.*>.*<\/([a-z0-9\\-]*:)*LFBHeader>/
                if (m3.find()) {
                    nsp = m3[0][1]
                    if (nsp == null) {
                        header = header.replaceAll("</LFBClientContext>", "<$tag>|CORRID|$uuid</$tag></LFBClientContext>")
                        return header
                    } else {
                        header = header.replaceAll("</[a-z0-9\\\\-]*:*LFBClientContext>", "<$nsp$tag>|CORRID|$uuid</$nsp$tag></$nsp" + "LFBClientContext>")
                        return header
                    }
                } else {
                    //throw new Exception("Invalid log")
                }
            } else if (tag.equals("Id")) {
                def m3 = header =~ /<([a-z0-9\\-]*:)*LFBResponseHeader.*>.*<\/([a-z0-9\\-]*:)*LFBResponseHeader>/
                if (m3.find()) {
                    nsp = m3[0][1]
                    if (nsp == null) {
                        header = header.replaceAll("</LFBResponseHeader>", "<$tag>|CORRID|$uuid</$tag></LFBResponseHeader>")
                        return header
                    } else {
                        header = header.replaceAll("</[a-z0-9\\\\-]*:*LFBResponseHeader>", "<$nsp$tag>|CORRID|$uuid</$nsp$tag></$nsp" + "LFBResponseHeader>")
                        return header
                    }
                } else {
                    //throw new Exception("Invalid tag")
                }
            }
        }

        if (nsp == null) {
            header = header.replaceAll("<([a-z0-9\\\\-]*:)*$tag>.*<\\/([a-z0-9\\\\-]*:)*$tag>", "<$tag>|CORRID|$uuid</$tag>")
            header = header.replaceAll("<[a-z0-9\\\\-]*:*$tag/>", "<$tag>|CORRID|$uuid</$tag>")
        } else {
            header = header.replaceAll("<([a-z0-9\\\\-]*:)*$tag>.*<\\/([a-z0-9\\\\-]*:)*$tag>", "<$nsp$tag>|CORRID|$uuid</$nsp$tag>")
            header = header.replaceAll("<[a-z0-9\\\\-]*:*$tag/>", "<$nsp$tag>|CORRID|$uuid</$nsp$tag>")
        }
    }

    private BS parseEntraRequest(String entry, String logEncoding) {
        def m = entry =~ /UUID: ([^,]+).*requestBody: (.+), request:/
        String uuid = m[0][1]
        String request = m[0][2]
        request = request.replace("+", "\\+").replace("*", "\\*").concat("\\s*")
        String uri = request.substring(0, 3)
        new BS(uuid: uuid, uri: uri, request: ByteBuffer.wrap(request.getBytes(Charset.forName(logEncoding))), type: "txt")
    }

    private BS parseEntraResponse(String entry, String logEncoding) {
        def m = entry =~ /UUID: ([^,]+).*return: (.*)>\s*$/
        String uuid = m[0][1]
        String response = m[0][2]
        response = response.substring(6)
        String uri = response.substring(0, 3)

        new BS(uuid: uuid, uri: uri, response: ByteBuffer.wrap(response.getBytes(Charset.forName(logEncoding))), type: "txt")
    }

    private Auth parseGetAuthenticatedUserOrganisationsResponse(String entry, String logEncoding) {
        entry = entry.replaceAll(" *\n *", "")
        String uuid
        String header
        String body
        String remaining

        def m = entry =~ /UUID="([^"]+)(.*)/
        if (m.find()) {
            uuid = ps_uuid
            remaining = m[0][2]
        } else {
            throw new Exception("Can not parse uuid")
        }

        m = remaining =~ /(<GetCompanyListResponse.*GetCompanyListResponse>)/
        if (m.find()) {
            body = m[0][1]
        } else {
            // Some log entries have skipped the soap body container...
            m = remaining =~ /(.*)..Log>/
            if (m.find()) {
                body = m[0][1]
            }
        }
        if (body == null) {
            throw new Exception("Can not parse body")
        }

        String uri = "Other"

        String response = ByteBuffer.wrap(body.getBytes(Charset.forName(logEncoding)))
        response = putInEnvelope("", putInBody(body))
        new Auth(uuid: uuid, uri: uri, response: response)
    }

    private Auth parseUpdateSecurityObjectResponse(String entry, String logEncoding) {
        entry = entry.replaceAll(" *\n *", "")
        String uuid
        String header
        String body
        String remaining

        def m = entry =~ /UUID="([^"]+)">(.*)/
        if (m.find()) {
            uuid = ps_uuid
            remaining = m[0][2]
        } else {
            throw new Exception("Can not parse uuid")
        }

        m = remaining =~ /(<UnameCodeLoginResponse.*UnameCodeLoginResponse>)/
        if (m.find()) {
            body = m[0][1]
        } else {
            // Some log entries have skipped the soap body container...
            m = remaining =~ /(.*)..Log>/
            if (m.find()) {
                body = m[0][1]
            }
        }
        if (body == null) {
            throw new Exception("Can not parse body")
        }

        String uri = "Other"

        String response = ByteBuffer.wrap(body.getBytes(Charset.forName(logEncoding)))
        response = putInEnvelope("", body)
        new Auth(uuid: uuid, uri: uri, response: response)
    }

    private Auth parseGetAuthenticatedUserOrganisationsRequest(String entry, String logEncoding) {
        entry = entry.replaceAll(" *\n *", "")
        String uuid
        String header
        String body

        def m = entry =~ /UUID="([^"]+).*(<[a-z\\-]+:Header.*\/>).*(<[a-z\\-]+:GetCompanyList.*:GetCompanyList>)/
        if (m.find()) {
            uuid = ps_uuid
            header = m[0][2]
            body = m[0][3]
        }

        if (uuid == null || header == null || body == null) {
            throw new Exception("Can not parse uuid and/or header and/or body")
        }


        String request = ByteBuffer.wrap(body.getBytes(Charset.forName(logEncoding)))
        request = putInEnvelope("", putInBody(body))
        String uri = "Other"

        new Auth(uuid: uuid, uri: uri, request: request)
    }

    private Auth parseUpdateSecurityObjectRequest(String entry, String logEncoding) {
        entry = entry.replaceAll(" *\n *", "")
        String uuid
        String header
        String body
        String remaining

        def m = entry =~ /UUID="([^"]+)">(.*)/
        if (m.find()) {
            uuid = ps_uuid
            remaining = m[0][2]
        }

        if (uuid == null) {
            throw new Exception("Can not parse uuid")
        }

        m = remaining =~ /(<[a-zA-Z\\-]+:Header.*:Header>)(.*)/
        if (m.find()) {
            header = m[0][1]
            remaining = m[0][2]
        }
        if (header == null) {
            m = remaining =~ /(<[a-zA-Z\\-]+:Header[^>]*\/\s*>)(.*)/
            if (m.find()) {
                header = m[0][1]
                remaining = m[0][2]
            }
        }

        m = remaining =~ /(<[a-zA-Z\\-]+:Body.*:Body>)/
        if (m.find()) {
            body = m[0][1]
        } else {
            // Some log entries have skipped the soap body container...
            m = remaining =~ /(.*)..Log>/
            if (m.find()) {
                body = putInBody(m[0][1])
            }
        }
        if (body == null) {
            throw new Exception("Unable to parse body")
        }

        if (uuid == null || header == null || body == null) {
            throw new Exception("Can not parse uuid and/or header and/or body")
        }


        String request = ByteBuffer.wrap(body.getBytes(Charset.forName(logEncoding)))
        request = putInEnvelope(header, body)
        String uri = "Other"

        new Auth(uuid: uuid, uri: uri, request: request)
    }

    void saveuuid(String entry) {
        entry = entry.replaceAll(" *\n *", "")
        def m = entry =~ /UUID="([^"]+).*/
        if (m.find()) {
            ps_uuid = m[0][1]
        }
    }

    private Nonentra_BS parseNonentra_BSRequest(String entry, String logEncoding) {
        entry = entry.replaceAll(" *\n *", "")
        String uuid
        String header
        String body
        String remaining

        def m = entry =~ /UUID="([^"]+)">(.*)/
        if (m.find()) {
            uuid = m[0][1]
            remaining = m[0][2]
        }

        if (uuid == null) {
            throw new Exception("Can not parse uuid")
        }

        m = remaining =~ /(<[a-zA-Z\\-]+:Header.*:Header>)(.*)/
        if (m.find()) {
            header = m[0][1]
            remaining = m[0][2]
        } else {
            // Some log entries have skipped the soap header container...
            m = remaining =~ /(<[^>]*LFBResponseHeader.*LFBResponseHeader>)(.*)/
            if (m.find()) {
                header = putInHeader(m[0][1])
                remaining = m[0][2]
            } else {
                // Some log entries have a LFBHeader...
                m = remaining =~ /(<[^>]*LFBHeader.*LFBHeader>)(.*)/
                if (m.find()) {
                    header = putInHeader(m[0][1])
                    remaining = m[0][2]
                }
            }
        }
        if (header == null) {
            m = remaining =~ /(<[a-zA-Z\\-]+:Header[^>]*\/\s*>)(.*)/
            if (m.find()) {
                header = m[0][1]
                remaining = m[0][2]
            }
        }

        m = remaining =~ /(<[a-zA-Z\\-]+:Body.*:Body>)/
        if (m.find()) {
            body = m[0][1]
        } else {
            // Some log entries have skipped the soap body container...
            m = remaining =~ /(.*)..Log>/
            if (m.find()) {
                body = putInBody(m[0][1])
            }
        }
        if (body == null) {
            throw new Exception("Unable to parse body")
        }
        String request = ByteBuffer.wrap(body.getBytes(Charset.forName(logEncoding)))
        request = putInEnvelope(header, body).replace("*", "\\*")
        String uri = "Other"
        new Nonentra_BS(uuid: uuid, uri: uri, request: request)
    }

    private Nonentra_BS parseNonentra_BSResponse(String entry, String logEncoding) {
        entry = entry.replaceAll(" *\n *", "")
        String uuid
        String header
        String body
        String remaining

        def m = entry =~ /UUID="([^"]+)(.*)/
        if (m.find()) {
            uuid = m[0][1]
            remaining = m[0][2]
            if (remaining[1] == '>') {
                remaining = remaining.substring(2)
            }
        } else {
            throw new Exception("Can not parse uuid")
        }

        m = remaining =~ /(<[a-zA-Z\\-]+:Header.*:Header>)(.*)/
        if (m.find()) {
            header = m[0][1]
            remaining = m[0][2]
        } else {
            // Some log entries have skipped the soap header container...
            m = remaining =~ /(<[^>]*LFBResponseHeader.*LFBResponseHeader>)(.*)/
            if (m.find()) {
                header = putInHeader(m[0][1])
                remaining = m[0][2]
            } else {
                // Some log entries have a LFBHeader...
                m = remaining =~ /(<[^>]*LFBHeader.*LFBHeader>)(.*)/
                if (m.find()) {
                    header = putInHeader(m[0][1])
                    remaining = m[0][2]
                }
            }
        }
        if (header == null) {
            m = remaining =~ /(<[a-zA-Z\\-]+:Header[^>]*\/\s*>)(.*)/
            if (m.find()) {
                header = m[0][1]
                remaining = m[0][2]
            }
        }

        m = remaining =~ /(<[a-zA-Z\\-]+:Body.*:Body>)/
        if (m.find()) {
            body = m[0][1]
        } else {
            // Some log entries have skipped the soap body container...
            m = remaining =~ /(.*)..Log>/
            if (m.find()) {
                body = putInBody(m[0][1])
            }
        }
        if (body == null) {
            throw new Exception("Can not parse body")
        }

        String uri = "Other"

        String response = ByteBuffer.wrap(body.getBytes(Charset.forName(logEncoding)))
        response = putInEnvelope(header, body)
        new Nonentra_BS(uuid: uuid, uri: uri, response: response)
    }

    private boolean isPSRequest(String entry) {
        entry =~ /PS Request/
    }

    private boolean isPSResponse(String entry) {
        entry =~ /PS Response/ || entry =~ /Check errors/
    }

    private boolean isEntraRequest(String entry) {
        entry =~ /EntraSocketAdapter.*Calling Entra with, UUID:/
    }

    private boolean isEntraResponse(String entry) {
        entry =~ /EntraSocketAdapter.*Returning, UUID:/
    }

    private boolean isGetAuthenticatedUserOrganisationsRequest(String entry) {
        entry =~ /GetAuthenticatedUserOrganisations Pipeline_request, Log BS Request/
    }

    private boolean isGetAuthenticatedUserOrganisationsResponse(String entry) {
        entry =~ /GetAuthenticatedUserOrganisations Pipeline_response, Log BS Response/
    }

    private boolean isUpdateSecurityObjectPrivateRequest(String entry) {
        entry =~ /UpdateSecurityObject-Private Pipeline_request, Log BS Request/
    }

    private boolean isUpdateSecurityObjectPrivateResponse(String entry) {
        entry =~ /UpdateSecurityObject-Private Pipeline_response, Log BS Response/
    }

    private boolean isSaveuuid(String entry) {
        entry =~ /Authorize user, REQUEST/
    }

    private boolean isOtherBackendRequest(String entry) {
        if (entry =~ /CallEntra Pipeline/) {
            return false
        } else if (entry =~ /CallEntraRequest/) {
            return false
        }
        entry =~ /Log BS Request/

    }

    private boolean isOtherBackendResponse(String entry) {
        if (entry =~ /CallEntra Pipeline/) {
            return false
        } else if (entry =~ /CallEntraResponse/) {
            return false
        }
        entry =~ /Log BS Response/
    }

    private void parseEntry(String entry, File workDir, String outEncoding, String logEncoding) {
        try {
            if (entry =~ /GetAuthenticatedUserOrganisations Pipeline/) {
                if (isGetAuthenticatedUserOrganisationsRequest(entry)) {
                    Auth auth = parseGetAuthenticatedUserOrganisationsRequest(entry, logEncoding)
                    File dir = new File(workDir, "Simulator/${auth.uuid.replaceAll("-", "_")}/${auth.uri}")
                    dir.mkdirs()
                    int index = dir.list().length / 2
                    File file = new File(dir, "${index}_GetAuthenticatedUserOrganisations_Request.xml")
                    file.setText(XmlUtil.serialize(auth.request.replace("+", "\\+").replace("*", "\\*").replace("|", "\\|")), outEncoding)
                    log.info "Created log entry file: {}", file
                    return
                } else if (isGetAuthenticatedUserOrganisationsResponse(entry)) {
                    Auth auth = parseGetAuthenticatedUserOrganisationsResponse(entry, logEncoding)
                    File dir = new File(workDir, "Simulator/${auth.uuid.replaceAll("-", "_")}/${auth.uri}")
                    dir.mkdirs()
                    int index = dir.list().length / 2
                    File file = new File(dir, "${index}_GetAuthenticatedUserOrganisations_Response.xml")
                    file.setText(XmlUtil.serialize(auth.response), outEncoding)
                    log.info "Created log entry file: {}", file
                    return
                }
            }
            if (entry =~ /UpdateSecurityObject-Private Pipeline/) {
                if (isUpdateSecurityObjectPrivateRequest(entry)) {
                    Auth auth = parseUpdateSecurityObjectRequest(entry, logEncoding)
                    File dir = new File(workDir, "Simulator/${auth.uuid.replaceAll("-", "_")}/${auth.uri}")
                    dir.mkdirs()
                    int index = dir.list().length / 2
                    File file = new File(dir, "${index}_UpdateSecurityObject_Request.xml")
                    file.setText(XmlUtil.serialize(auth.request.replace("*", "\\*").replace("|", "\\|")), outEncoding)
                    log.info "Created log entry file: {}", file
                    return
                } else if (isUpdateSecurityObjectPrivateResponse(entry)) {
                    Auth auth = parseUpdateSecurityObjectResponse(entry, logEncoding)
                    File dir = new File(workDir, "Simulator/${auth.uuid.replaceAll("-", "_")}/${auth.uri}")
                    dir.mkdirs()
                    int index = dir.list().length / 2
                    File file = new File(dir, "${index}_UpdateSecurityObject_Response.xml")
                    file.setText(XmlUtil.serialize(auth.response), outEncoding)
                    log.info "Created log entry file: {}", file
                    return
                }
            } else if (isPSRequest(entry)) {
                PS ps = parsePSRequest(entry, logEncoding)
                File dir = new File(workDir, "${ps.uri}/${ps.uuid.replaceAll("-", "_")}")
                dir.mkdirs()
                File file = new File(dir, "Request.xml")
                file.setText(XmlUtil.serialize(ps.request), outEncoding)
                log.info "Created log entry file: {}", file

                File authDir = new File(workDir, "Simulator/${ps.uuid.replaceAll("-", "_")}/Gemi")
                authDir.mkdirs()
                File authFile = new File(authDir, "Authorize_Request.xml")
                authFile.setText(authRequest, outEncoding)
                log.info "Created log entry file: {}", file
                return

            } else if (isPSResponse(entry)) {
                PS ps = parsePSResponse(entry, logEncoding)
                File dir = new File(workDir, "${ps.uri}/${ps.uuid.replaceAll("-", "_")}")
                dir.mkdirs()
                File file = new File(dir, "Response.xml")
                file.setText(XmlUtil.serialize(ps.response), outEncoding)
                log.info "Created log entry file: {}", file

                File authDir = new File(workDir, "Simulator/${ps.uuid.replaceAll("-", "_")}/Gemi")
                authDir.mkdirs()
                File authFile = new File(authDir, "Authorize_Response.xml")
                authFile.setText(authResponse, outEncoding)
                log.info "Created log entry file: {}", file
                return

            } else if (isEntraRequest(entry)) {
                BS bs = parseEntraRequest(entry, logEncoding)
                File dir = new File(workDir, "Simulator/${bs.uuid.replaceAll("-", "_")}/${bs.uri}")
                dir.mkdirs()
                int index = dir.list().length / 2
                File file = new File(dir, "${index}_Request.${bs.type}")
                Charset isoCharset = Charset.forName(logEncoding)
                CharBuffer fileStr = isoCharset.decode(bs.request)
                file.setText(fileStr.toString(), outEncoding)
                log.info "Created log entry file: {}", file
                return
            } else if (isEntraResponse(entry)) {
                BS bs = parseEntraResponse(entry, logEncoding)
                File dir = new File(workDir, "Simulator/${bs.uuid.replaceAll("-", "_")}/${bs.uri}")
                dir.mkdirs()
                int index = dir.list().length / 2
                File file = new File(dir, "${index}_Response.${bs.type}")
                Charset isoCharset = Charset.forName(logEncoding)
                CharBuffer fileStr = isoCharset.decode(bs.response)
                file.setText(fileStr.toString(), outEncoding)
                log.info "Created log entry file: {}", file
                return
            } else if (isSaveuuid(entry)) {
                saveuuid(entry)
                return
            } else if (isOtherBackendRequest(entry)) {
                Nonentra_BS nonentra_bs = parseNonentra_BSRequest(entry, logEncoding)
                File dir = new File(workDir, "Simulator/${nonentra_bs.uuid.replaceAll("-", "_")}/${nonentra_bs.uri}")
                dir.mkdirs()
                int index = dir.list().length / 2
                File file = new File(dir, "${index}_Request.xml")
                file.setText(XmlUtil.serialize(nonentra_bs.request), outEncoding)
                log.info "Created log entry file: {}", file
            } else if (isOtherBackendResponse(entry)) {
                Nonentra_BS nonentra_bs = parseNonentra_BSResponse(entry, logEncoding)
                File dir = new File(workDir, "Simulator/${nonentra_bs.uuid.replaceAll("-", "_")}/${nonentra_bs.uri}")
                dir.mkdirs()
                int index = dir.list().length / 2
                File file = new File(dir, "${index}_Response.xml")
                file.setText(XmlUtil.serialize(nonentra_bs.response), outEncoding)
                log.info "Created log entry file: {}", file
            }
            // TODO: Add other backends
        } catch (Exception e) {
            log.warn "Can not parse log entry ({}): {}", e.getMessage(), entry
        }
    }

    private void parseFile(File logFile, String logEncoding, File workDir, String outEncoding) {
        int maxNbOfLinesInEntry = 10000
        int nbOfLinesInEntry
        String firstLine
        String entry
        logFile.eachLine(logEncoding) { line ->
            if (line.startsWith("####")) {
                if (entry != null) {
                    parseEntry(entry, workDir, outEncoding, logEncoding)
                }
                nbOfLinesInEntry = 0
                firstLine = line
                entry = line
            } else {
                if (firstLine != null) {
                    nbOfLinesInEntry++
                    if (nbOfLinesInEntry >= maxNbOfLinesInEntry) {
                        log.warn "Too many lines for {}", firstLine
                        entry = ""
                        firstLine = null
                    } else {
                        entry = entry + "\n" + line
                    }
                }
            }
        }
        if (entry != null && entry.length() > 0) {
            parseEntry(entry, workDir, outEncoding, logEncoding)
        }
    }

    private void createTestClass(File classDir, String uri, String uuid, Map<String, String> resourceMap) {
        String servicePackageDir = "se/lf/service/esb" + uri.toLowerCase().replaceAll("/2", "/_2")
        uri = uri.replaceAll("-", "")
        def m = uri =~ /[\w]+\/[\w]+\/([\w]+)\/(.*)/
        if (!m.find()) {
            log.warn("Can not parse serviceName and/or serviceVersion from uri: {}", uri)
            return
        }

        String serviceName = m[0][1]
        String serviceVersion = m[0][2]

        Map<String, String> replacements = [
                "##servicePackageDir##": servicePackageDir,
                "##servicePackage##"   : servicePackageDir.replaceAll("/", "."),
                "##ServiceName##"      : serviceName,
                "##ServiceVersion##"   : serviceVersion,
                "##uuid##"             : uuid,
                "##_uuid##"            : "${uuid.replaceAll("-", "_")}",
                "##requestResource##"  : resourceMap["requestResource"],
                "##responseResource##" : resourceMap["responseResource"],
                "##simulatorResource##": resourceMap["simulatorResource"],
                "##uri##"              : uri
        ]

        //TODO: Update path with your system path
        File templateFile = new File("D:/LFDEV/lf/es/esb/test/src/main/resources/##servicePackageDir##/T_##_uuid##_##ServiceName##_##ServiceVersion##_Test.java")
        String templateContent = templateFile.text

        //TODO: Update path with your system path
        String classFilePath = replacements.inject(templateFile.absolutePath.replace(new File("D:/LFDEV/lf/es/esb/test/src/main/resources/").absolutePath, "")) { path, key, value ->
            path.replaceAll(key, value)
        }
        String classContent = replacements.inject(templateContent) { content, key, value ->
            content.replaceAll(key, value)
        }

        File classFile = new File(classDir, classFilePath);
        classFile.parentFile.mkdirs()
        classFile.text = classContent
    }

    private void createTestCase(File workDir, File outDir) {
        int counter = 0
        File resourcesDir = new File(outDir, "/resources")
        File classDir = new File(outDir, "/java")

        workDir.eachDirRecurse { dir ->
            try {
                File requestFile = new File(dir, "Request.xml")
                File responseFile = new File(dir, "Response.xml")
                File simulatorDir = new File(workDir, "Simulator/${dir.name}")

                if (requestFile.exists() && responseFile.exists() && simulatorDir.exists()) {
                    int index = workDir.absolutePath.length()
                    String requestResource = requestFile.absolutePath.substring(index).replaceAll(Pattern.quote("\\"), "/")
                    String responseResource = responseFile.absolutePath.substring(index).replaceAll(Pattern.quote("\\"), "/")
                    String simulatorResource = requestResource.replaceAll("Request.xml", "Simulator")

                    new File(resourcesDir, requestResource).parentFile.mkdirs()
                    requestFile.renameTo(new File(resourcesDir, requestResource))
                    responseFile.renameTo(new File(resourcesDir, responseResource))
                    simulatorDir.renameTo(new File(resourcesDir, simulatorResource))

                    Map<String, String> resourceMap = [requestResource: requestResource, responseResource: responseResource, simulatorResource: simulatorResource]
                    String uri = dir.parentFile.absolutePath.substring(index).replaceAll(Pattern.quote("\\"), "/")
                    String uuid = dir.name
                    createTestClass(classDir, uri, uuid, resourceMap);

                    counter++
                    log.info "Generated testCase {} {}/{}", counter, uri, uuid
                }
            } catch (Exception e) {
                log.warn "Can not create test case: {}", dir, e
            }
        }
    }

    void generate(File workDir, File logsDir, String logEncoding, File outDir, String outEncoding) {
        workDir.mkdirs()
        workDir.deleteDir()
        workDir.mkdirs()

        logsDir.eachFileRecurse(FileType.FILES) { logFile ->
            parseFile(logFile, logEncoding, workDir, outEncoding)
        }

        createTestCase(workDir, outDir)

        workDir.deleteDir()
    }

    public static void main(String[] args) {
        String logEncoding = "ISO-8859-1"
        String outEncoding = "UTF-8"

        //TODO: Update path with your system path
        File logsDir = new File("D:/LFDEV/lf/es/esb/test/src/test/resources/logs20160125")
        File workDir = new File("./tmp")
        //TODO: Update path with your system path
        File outDir = new File("D:/LFDEV/lf/es/esb/test/src/test")

        if (args != null && args.length == 3) {
            logsDir = new File(args[0])
            workDir = new File(args[1])
            outDir = new File(args[2])
        }
        new TestCase().generate(workDir, logsDir, logEncoding, outDir, outEncoding)
    }
}