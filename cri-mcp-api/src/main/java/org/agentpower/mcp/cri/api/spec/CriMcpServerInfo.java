package org.agentpower.mcp.cri.api.spec;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * information of the mcp-server
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CriMcpServerInfo(
        @JsonProperty String messageEndpoint,
        @JsonProperty TokenCodecInfo tokenCodec,
        @JsonProperty ParamCodecInfo paramCodec,
        @JsonProperty DataCodecInfo dataCodec
) {
    public static CriMcpServerInfo onlyEndpoint(String messageEndpoint) {
        return new CriMcpServerInfo(messageEndpoint, null, null, null);
    }

    /**
     * the encryption information
     *  which the mcp-server requires to encrypt the recognition token
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TokenCodecInfo(
            @JsonProperty String headerOfToken,
            @JsonProperty String algorithm,
            @JsonProperty String keyForEncode,
            @JsonProperty String encodingParams
    ) {
    }


    /**
     * the encryption information
     *  which the mcp-server requires to decrypt the request params
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ParamCodecInfo(
            @JsonProperty String algorithm,
            @JsonProperty String keyForEncode,
            @JsonProperty String encodingParams
    ) {
    }


    /**
     * the header information
     *  that the mcp-server will use to encrypt the data
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DataCodecInfo(
            @JsonProperty String headerOfAlgorithm,
            @JsonProperty String headerOfKeyForEncode,
            @JsonProperty String headerOfEncodingParams
    ) {
    }

}
