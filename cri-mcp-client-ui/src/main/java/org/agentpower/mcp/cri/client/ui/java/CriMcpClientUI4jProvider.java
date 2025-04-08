package org.agentpower.mcp.cri.client.ui.java;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.agentpower.mcp.cri.api.client.server.CriMcsCallback;
import org.agentpower.mcp.cri.api.client.server.CriMcsRequest;
import org.agentpower.mcp.cri.api.client.ui.CriMcpClientUI;
import org.agentpower.mcp.cri.api.spec.CriMcpCommonEvents;
import org.agentpower.mcp.cri.api.spec.CriMcpEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.regex.Pattern;

import static io.modelcontextprotocol.client.transport.FlowSseClient.*;

public abstract class CriMcpClientUI4jProvider {
    private static final Logger logger = LoggerFactory.getLogger(CriMcpClientUI4jProvider.class);
    private final Map<String, CriMcpClientUI> servers = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    public CriMcpClientUI4jProvider(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }




    private static class Client implements CriMcpClientUI {
        /** Holds the SSE connection future */
        private final AtomicReference<CompletableFuture<Void>> connectionFuture = new AtomicReference<>();
        private volatile boolean isClosing = false;
        private final ObjectMapper objectMapper;
        private final String clientServerUri;
        private final HttpClient httpClient;
        private CriMcsRequest currentRequest;
        Client(ObjectMapper objectMapper, String clientServerUri, HttpClient httpClient) {
            this.objectMapper = objectMapper;
            this.clientServerUri = clientServerUri;
            this.httpClient = httpClient;
        }
        @Override
        public Mono<Void> sendRequestToMcpServer(CriMcsRequest request) {
            this.currentRequest = request;
            if (CriMcpCommonEvents.ENDPOINT_EVENT_TYPE.equals(this.currentRequest.body)) {
                CompletableFuture<Void> future = new CompletableFuture<>();
                connectionFuture.set(future);
                // endpoint: do sse connect using GET method
                new SseClient(this.httpClient).subscribe(this.currentRequest.msUrl, this.currentRequest.headers,
                        new EventHandler(this, future));
                return Mono.fromFuture(future);
            } else {
                // do remote call to mcp server using POST method
                try {
                    HttpRequest messageRequest = wrapHeaders(HttpRequest.newBuilder()
                            .uri(URI.create(request.msUrl)), request.headers)
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(this.objectMapper.writeValueAsString(this.currentRequest)))
                            .build();
                    return Mono.fromFuture(httpClient.sendAsync(messageRequest, HttpResponse.BodyHandlers.discarding()).thenAccept(response -> {
                        if (response.statusCode() != 200 && response.statusCode() != 201 && response.statusCode() != 202
                                && response.statusCode() != 206) {
                            logger.error("Error sending message: {}", response.statusCode());
                        }
                    }));
                } catch (JsonProcessingException e) {
                    if (!isClosing) {
                        return Mono.error(new RuntimeException("Failed to serialize message", e));
                    }
                    return Mono.empty();
                }
            }
        }

        @Override
        public Mono<Void> sendCallbackToClientServer(CriMcsCallback callback) {
            try {
                HttpRequest messageRequest = wrapHeaders(HttpRequest.newBuilder()
                        .uri(URI.create(clientServerUri + "/" + callback.csUri)), callback.headers)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(this.objectMapper.writeValueAsString(callback.event)))
                        .build();
                return Mono.fromFuture(httpClient.sendAsync(messageRequest, HttpResponse.BodyHandlers.discarding()).thenAccept(response -> {
                    if (response.statusCode() != 200 && response.statusCode() != 201 && response.statusCode() != 202
                            && response.statusCode() != 206) {
                        logger.error("Error sending message: {}", response.statusCode());
                    }
                }));
            } catch (JsonProcessingException e) {
                if (!isClosing) {
                    return Mono.error(new RuntimeException("Failed to serialize message", e));
                }
                return Mono.empty();
            }
        }

        @Override
        public Mono<Void> closeGracefully() {
            return Mono.fromRunnable(() -> {
                isClosing = true;
                CompletableFuture<Void> future = connectionFuture.get();
                if (future != null && !future.isDone()) {
                    future.cancel(true);
                }
            });
        }
    }
    private static class EventHandler implements SseEventHandler {
        private final Client client;
        private final CompletableFuture<Void> future;
        EventHandler(Client client, CompletableFuture<Void> future) {
            this.client = client;
            this.future = future;
        }
        @Override
        public void onEvent(SseEvent event) {
            client.sendCallbackToClientServer(CriMcsCallback.fromRequest(client.currentRequest, new CriMcpEvent(event.type(), event.data())))
                    .subscribe();
            if (CriMcpCommonEvents.ENDPOINT_EVENT_TYPE.equals(event.type())) {
                future.complete(null);
            }
        }
        @Override
        public void onError(Throwable error) {
            try {
                client.sendCallbackToClientServer(CriMcsCallback.fromRequest(client.currentRequest,
                                new CriMcpEvent(CriMcpCommonEvents.CLOSE_EVENT_TYPE, error.getMessage())))
                        .subscribe();
            } finally {
                future.completeExceptionally(error);
            }
        }
    }

    private static class SseClient {
        private static final Pattern EVENT_DATA_PATTERN = Pattern.compile("^data:(.+)$", 8);
        private static final Pattern EVENT_ID_PATTERN = Pattern.compile("^id:(.+)$", 8);
        private static final Pattern EVENT_TYPE_PATTERN = Pattern.compile("^event:(.+)$", 8);
        private final HttpClient httpClient;
        public SseClient(HttpClient httpClient) {
            this.httpClient = httpClient;
        }

        public void subscribe(String url, Map<?, ?> headers, SseEventHandler eventHandler) {
            HttpRequest request = wrapHeaders(
                    HttpRequest.newBuilder().uri(URI.create(url)), headers)
                    .header("Accept", "text/event-stream")
                    .header("Cache-Control", "no-cache")
                    .GET()
                    .build();

            StringBuilder eventBuilder = new StringBuilder();
            AtomicReference<String> currentEventId = new AtomicReference<>();
            AtomicReference<String> currentEventType = new AtomicReference<>("message");

            Flow.Subscriber<String> lineSubscriber = new Flow.Subscriber<>() {
                private Flow.Subscription subscription;

                @Override
                public void onSubscribe(Flow.Subscription subscription) {
                    this.subscription = subscription;
                    subscription.request(Long.MAX_VALUE);
                }

                @Override
                public void onNext(String line) {
                    if (line.isEmpty()) {
                        // Empty line means end of event
                        if (eventBuilder.length() > 0) {
                            String eventData = eventBuilder.toString();
                            SseEvent event = new SseEvent(currentEventId.get(), currentEventType.get(), eventData.trim());
                            eventHandler.onEvent(event);
                            eventBuilder.setLength(0);
                        }
                    }
                    else {
                        if (line.startsWith("data:")) {
                            var matcher = EVENT_DATA_PATTERN.matcher(line);
                            if (matcher.find()) {
                                eventBuilder.append(matcher.group(1).trim()).append("\n");
                            }
                        }
                        else if (line.startsWith("id:")) {
                            var matcher = EVENT_ID_PATTERN.matcher(line);
                            if (matcher.find()) {
                                currentEventId.set(matcher.group(1).trim());
                            }
                        }
                        else if (line.startsWith("event:")) {
                            var matcher = EVENT_TYPE_PATTERN.matcher(line);
                            if (matcher.find()) {
                                currentEventType.set(matcher.group(1).trim());
                            }
                        }
                    }
                    subscription.request(1);
                }

                @Override
                public void onError(Throwable throwable) {
                    eventHandler.onError(throwable);
                }

                @Override
                public void onComplete() {
                    // Handle any remaining event data
                    if (eventBuilder.length() > 0) {
                        String eventData = eventBuilder.toString();
                        SseEvent event = new SseEvent(currentEventId.get(), currentEventType.get(), eventData.trim());
                        eventHandler.onEvent(event);
                    }
                }
            };

            Function<Flow.Subscriber<String>, HttpResponse.BodySubscriber<Void>> subscriberFactory = subscriber -> HttpResponse.BodySubscribers
                    .fromLineSubscriber(subscriber);

            CompletableFuture<HttpResponse<Void>> future = this.httpClient.sendAsync(request,
                    info -> subscriberFactory.apply(lineSubscriber));

            future.thenAccept(response -> {
                int status = response.statusCode();
                if (status != 200 && status != 201 && status != 202 && status != 206) {
                    throw new RuntimeException("Failed to connect to SSE stream. Unexpected status code: " + status);
                }
            }).exceptionally(throwable -> {
                eventHandler.onError(throwable);
                return null;
            });
        }
    }

    private static HttpRequest.Builder wrapHeaders(HttpRequest.Builder builder, Map<?, ?> headers) {
        if (headers != null && ! headers.isEmpty()) {
            for (Map.Entry<?, ?> entry : headers.entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null) {
                    continue;
                }
                builder.header(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
            }
        }
        return builder;
    }
}
