package com.prodigalgal.ircs.storage.image;

import com.prodigalgal.ircs.common.outbound.OutboundHttpPolicy;
import com.prodigalgal.ircs.common.outbound.OutboundHttpRequest;
import com.prodigalgal.ircs.common.outbound.OutboundStreamingHttpClient;
import java.io.IOException;
import java.net.URI;

class JdkCoverImageDownloadAdapter implements CoverImageDownloadAdapter {

    private final OutboundStreamingHttpClient httpClient;

    JdkCoverImageDownloadAdapter(OutboundStreamingHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public CoverImageDownloadResponse download(URI uri, OutboundHttpPolicy policy)
            throws IOException, InterruptedException {
        var response = httpClient.execute(OutboundHttpRequest.get(uri, policy));
        return new CoverImageDownloadResponse(response.statusCode(), response.headers(), response.body());
    }
}
