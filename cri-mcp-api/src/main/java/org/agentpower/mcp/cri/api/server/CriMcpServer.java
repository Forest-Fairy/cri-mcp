package org.agentpower.mcp.cri.api.server;

import io.modelcontextprotocol.spec.McpServerTransport;
import org.agentpower.mcp.cri.api.spec.CriMcpServerInfo;

import java.util.Map;

/**
 * mcp server interface
 *  it receive the request defined by self but return a standard response defined by cri-mcp
 */
public interface CriMcpServer {
    /**
     * the mcp-server information that contains:
     *      endpoint path for handling client messages,
     *      requirements of token's encryption,
     *      requirements of parameters' encryption,
     *      requirements of data's decryption
     */
    CriMcpServerInfo getServerInfo();


    String decodeRequestBodyToJson(String encodedRequestBody);

    String encodeMessageJsonToString(Map<String, String> headers, String jsonText);
}
