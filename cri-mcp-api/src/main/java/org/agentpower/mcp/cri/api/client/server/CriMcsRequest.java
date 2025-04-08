package org.agentpower.mcp.cri.api.client.server;

import java.util.Map;

public class CriMcsRequest {
    /** id */
    public final String requestId;
    /** mcp-server uri */
    public final String msUrl;
    /** headers */
    public final Map<String, String> headers;
    /** body: maybe is encrypted */
    public final String body;
    public final Callback callback;

    public CriMcsRequest(String requestId, String msUrl, Map<String, String> headers, String body, Callback callback) {
        this.requestId = requestId;
        this.msUrl = msUrl;
        this.headers = headers;
        this.body = body;
        this.callback = callback;
    }

    public record Callback(String callbackUri, Map<String, String> headers) { }

    public static RequestBuilder builder() {
        return new RequestBuilder();
    }

    public static class RequestBuilder {
        private String requestId;
        private String msUrl;
        private Map<String, String> headers;
        private String body;
        private Callback callback;
        public RequestBuilder requestId(String requestId) {
            this.requestId = requestId;
            return this;
        }
        public RequestBuilder msUrl(String msUrl) {
            this.msUrl = msUrl;
            return this;
        }
        public RequestBuilder headers(Map<String, String> headers) {
            this.headers = headers;
            return this;
        }
        public RequestBuilder body(String body) {
            this.body = body;
            return this;
        }
        public CallbackBuilder callbackBuilder() {
            return new CallbackBuilder(this);
        }
        public CriMcsRequest build() {
            return new CriMcsRequest(requestId, msUrl, headers, body, callback);
        }

    }
    public static class CallbackBuilder {
        private final RequestBuilder requestBuilder;
        private String callbackUri;
        private Map<String, String> headers;
        public CallbackBuilder(RequestBuilder requestBuilder) {
            this.requestBuilder = requestBuilder;
        }

        public CallbackBuilder uri(String uri) {
            this.callbackUri = uri;
            return this;
        }
        public CallbackBuilder headers(Map<String, String> headers) {
            this.headers = headers;
            return this;
        }
        public RequestBuilder buildCallback() {
            requestBuilder.callback = new Callback(callbackUri, headers);
            return requestBuilder;
        }
    }
}
