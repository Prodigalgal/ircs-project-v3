package com.prodigalgal.ircs.metadata.provider.douban;

import java.net.URI;
import java.util.Optional;

public interface DoubanHttpClient {

    DoubanHttpResponse getText(URI uri, Optional<DoubanCredential> credential);
}
