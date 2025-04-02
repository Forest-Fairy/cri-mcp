package org.agentpower.mcp.cri.client.ui.java;

import io.modelcontextprotocol.client.transport.FlowSseClient;
import org.agentpower.mcp.cri.api.client.server.CriMcsRequest;
import org.agentpower.mcp.cri.api.client.ui.CriMcpClientUI;
import org.agentpower.mcp.cri.api.spec.CriMcpCommonEvents;
import reactor.core.publisher.Mono;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CriMcpClientUi4j implements CriMcpClientUI {
    private final Map<String, FlowSseClient> servers = new ConcurrentHashMap<>();
    private final FlowSseClient flowSseClient;
    private volatile boolean isCLosing;

    CriMcpClientUi4j(String clientServer, String promptUri) {
        flowSseClient = new FlowSseClient(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
        flowSseClient.subscribe(clientServer + promptUri, new FlowSseClient.SseEventHandler() {
            @Override
            public void onEvent(FlowSseClient.SseEvent event) {
                if (isCLosing) {
                    return;
                }
                String clientServerTransportId = event.id();
                if (CriMcpCommonEvents.ENDPOINT_EVENT_TYPE.equals(event.type())) {
                    buildNewServerConnection(clientServerTransportId, event.data());
                } else if (CriMcpCommonEvents.MESSAGE_EVENT_TYPE.equals(event.type())) {
                    sendRequestToMcpServer()
                }
            }

            @Override
            public void onError(Throwable error) {
                if (isCLosing) {
                    return;
                }
                seca
            }
        });
    }
    @Override
    public Mono<Void> sendRequestToMcpServer(CriMcsRequest request) {
        if (CriMcpCommonEvents.ENDPOINT_EVENT_TYPE.equals(request.body)) {

        }
        return null;
    }

    @Override
    public Mono<Void> sendCallbackToClientServer(CriMcsRequest request) {
        return null;
    }

    @Override
    public Mono<Void> closeGracefully() {
        servers.forEach((k, v) -> {

        });
        return null;
    }
}
