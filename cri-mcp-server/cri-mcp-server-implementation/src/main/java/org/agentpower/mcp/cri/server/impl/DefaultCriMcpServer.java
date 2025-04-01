package org.agentpower.mcp.cri.server.impl;

import org.agentpower.mcp.cri.api.server.CriMcpServer;
import org.agentpower.mcp.cri.api.spec.CriMcpServerInfo;

import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;

public class DefaultCriMcpServer implements CriMcpServer {
    private final CriMcpServerInfo criMcpServerInfo;
    private final Function<String, String> decoder;
    private final BiFunction<String, CriMcpServerInfo.DataCodecInfo, String> encoder;

    public DefaultCriMcpServer(CriMcpServerInfo criMcpServerInfo, Function<String, String> decoder, BiFunction<String, CriMcpServerInfo.DataCodecInfo, String> encoder) {
        this.criMcpServerInfo = criMcpServerInfo;
        this.decoder = decoder;
        this.encoder = encoder;
    }

    @Override
    public CriMcpServerInfo getServerInfo() {
        return criMcpServerInfo;
    }

    @Override
    public String decodeRequestBodyToJson(String encodedRequestBody) {
//        this is the information for encode params, so we should decode request body with pri-key
//        CriMcpServerInfo.ParamCodecInfo paramCodecInfo = criMcpServerInfo.paramCodec();
//        String algorithm = paramCodecInfo.algorithm();
//        String keyForEncode = paramCodecInfo.keyForEncode();
//        String encodingParams = paramCodecInfo.encodingParams();
        return decoder.apply(encodedRequestBody);
    }

    @Override
    public String encodeMessageJsonToString(Map<String, String> headers, String jsonText) {
        CriMcpServerInfo.DataCodecInfo dataCodec = criMcpServerInfo.dataCodec();
        String algorithm = headers.get(dataCodec.headerOfAlgorithm());
        String keyForEncode = headers.get(dataCodec.headerOfKeyForEncode());
        String encodeParams = headers.get(dataCodec.headerOfEncodingParams());
        if (Objects.nonNull(algorithm) && Objects.nonNull(keyForEncode) && Objects.nonNull(encodeParams)) {
            CriMcpServerInfo.DataCodecInfo dataCodecInfo = new CriMcpServerInfo.DataCodecInfo(algorithm, keyForEncode, encodeParams);
            return encoder.apply(jsonText, dataCodecInfo);
        }
        return jsonText;
    }

}
