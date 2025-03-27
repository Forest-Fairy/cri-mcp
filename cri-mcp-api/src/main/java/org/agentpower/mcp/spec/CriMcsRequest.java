package org.agentpower.mcp.spec;

public class CriMcsRequest {
    /** mcp-server url */
    public final String msUrl;
    /** headers */
    public final String headers;
    /** body: maybe is encrypted */
    public final String body;
    public final Callback callback;

    public CriMcsRequest(String msUrl, String headers, String body, Callback callback) {
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
        private String msUrl;
        private String headers;
        private String body;
        private Callback callback;
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
            return new CriMcsRequest(msUrl, headers, body, callback);
        }

    }
    public static class CallbackBuilder {
        private final RequestBuilder requestBuilder;
        private String url;
        private String headers;
        public CallbackBuilder(RequestBuilder requestBuilder) {
            this.requestBuilder = requestBuilder;
        }

        public CallbackBuilder url(String url) {
            this.url = url;
            return this;
        }
        public CallbackBuilder headers(String headers) {
            this.headers = headers;
            return this;
        }
        public RequestBuilder build() {
            requestBuilder.callback = new Callback(url, headers);
            return requestBuilder;
        }
    }
}
