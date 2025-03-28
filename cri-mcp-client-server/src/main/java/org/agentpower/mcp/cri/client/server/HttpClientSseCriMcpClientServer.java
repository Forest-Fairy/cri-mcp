package org.agentpower.mcp.cri.client.server;

import org.agentpower.mcp.cri.api.client.server.CriMcpClientServer;
import org.agentpower.mcp.cri.api.client.server.CriMcsRequest;
import org.agentpower.mcp.cri.api.client.ui.CriMcpClientUI;
import org.agentpower.mcp.cri.client.server.spec.CriMcsResponse;

public class HttpClientSseCriMcpClientServer implements CriMcpClientServer<CriMcsRequest, CriMcsResponse> {
    public HttpClientSseCriMcpClientServer() {

    }
    @Override
    public CriMcsResponse send(CriMcsRequest criMcsRequest) {
        criMcsRequest
        return null;
    }

    @Override
    public boolean isClosed() {
        return false;
    }

    @Override
    public void close() throws Exception {

    }


    private static class HttpClientSseCriMcpClientUI implements CriMcpClientUI<CriMcsRequest, CriMcsResponse> {
        @Override
        public CriMcsResponse send(CriMcsRequest criMcsRequest) {
            return null;
        }

        @Override
        public boolean isClosed() {
            return false;
        }

        @Override
        public void close() throws Exception {

        }
    }

    public static class HttpClientSseRequest extends CriMcsRequest {
        private final String type;
        private final String functionName;
        private final String functionParams;

        /**
         * constructor of {@link HttpClientSseRequest}
         * @param msUrl mcp server url
         * @param headers headers to use
         * @param encryptedBody encrypted body
         * @param callback the callback info for MCUI
         * @param type type of request
         * @param functionName function name if it is a function call
         * @param functionParams function params if it is a function call
         */
        public HttpClientSseRequest(String msUrl, String headers, String encryptedBody, Callback callback, String type, String functionName, String functionParams) {
            super(msUrl, headers, encryptedBody, callback);
            this.type = type;
            this.functionName = functionName;
            this.functionParams = functionParams;
        }

        public String getType() {
            return type;
        }

        public String getFunctionName() {
            return functionName;
        }

        public String getFunctionParams() {
            return functionParams;
        }
    }
}
