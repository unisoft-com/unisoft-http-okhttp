package com.unisoft.http.okhttp;

import com.unisoft.core.http.HttpClient;
import com.unisoft.core.http.HttpClientProvider;

/**
 * @author omar.H.Ajmi
 * @since 21/10/2020
 */
public class OkHttpClientProvider implements HttpClientProvider {
    @Override
    public HttpClient createInstance() {
        return new OkHttpAsyncHttpClientBuilder().build();
    }
}
