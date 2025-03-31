package org.agentpower.mcp.cri.api.client.server;

public class CriMcsRequest {
    /** id */
    public final String requestId;
    /** mcp-server url */
    public final String msUrl;
    /** headers */
    public final String headers;
    /** body: maybe is encrypted */
    public final String body;
    public final Callback callback;

    public CriMcsRequest(String requestId, String msUrl, String headers, String body, Callback callback) {
        this.requestId = requestId;
        this.msUrl = msUrl;
        this.headers = headers;
        this.body = body;
        this.callback = callback;
    }

    public record Callback(String url, String headers) { }

    public static RequestBuilder builder() {
        return new RequestBuilder();
    }

    public static class RequestBuilder {
        private String requestId;
        private String msUrl;
        private String headers;
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
        public RequestBuilder headers(String headers) {
            this.headers = headers;
            return this;
        }
        public RequestBuilder body(String body) {
            this.body = body;
            return this;
        }
        public CallbackBuilder buildCallback() {
            return new CallbackBuilder(this);
        }
        public CriMcsRequest build() {
            return new CriMcsRequest(requestId, msUrl, headers, body, callback);
        }

    }
    public static class CallbackBuilder {
        private final RequestBuilder requestBuilder;
        private String uri;
        private String headers;
        public CallbackBuilder(RequestBuilder requestBuilder) {
            this.requestBuilder = requestBuilder;
        }

        public CallbackBuilder uri(String uri) {
            this.uri = uri;
            return this;
        }
        public CallbackBuilder headers(String headers) {
            this.headers = headers;
            return this;
        }
        public RequestBuilder build() {
            requestBuilder.callback = new Callback(uri, headers);
            return requestBuilder;
        }
    }
}
