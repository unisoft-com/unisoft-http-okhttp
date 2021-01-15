package com.unisoft.http.okhttp;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.unisoft.core.http.HttpMethod;
import com.unisoft.core.http.HttpRequest;
import okhttp3.*;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.time.Duration;
import java.util.Collections;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

class OkHttpAsyncHttpClientBuilderTest {
    private static final String COOKIE_VALIDATOR_PATH = "/cookieValidator";
    private static final String DEFAULT_PATH = "/default";
    private static final String DISPATCHER_PATH = "/dispatcher";

    private static WireMockServer server;

    private static String cookieValidatorUrl;
    private static String defaultUrl;
    private static String dispatcherUrl;

    @BeforeAll
    public static void setupWireMock() {
        server = new WireMockServer(WireMockConfiguration.options().dynamicPort().disableRequestJournal());

        // Mocked endpoint to test building a client with a prebuilt OkHttpClient.
        server.stubFor(WireMock.get(COOKIE_VALIDATOR_PATH).withCookie("test", WireMock.matching("success"))
                .willReturn(WireMock.aResponse().withStatus(200)));

        // Mocked endpoint to test building a client with a timeout.
        server.stubFor(WireMock.get(DEFAULT_PATH).willReturn(WireMock.aResponse().withStatus(200)));

        // Mocked endpoint to test building a client with a dispatcher and uses a delayed response.
        server.stubFor(WireMock.get(DISPATCHER_PATH).willReturn(WireMock.aResponse().withStatus(200)
                .withFixedDelay(5000)));

        server.start();

        cookieValidatorUrl = "http://localhost:" + server.port() + COOKIE_VALIDATOR_PATH;
        defaultUrl = "http://localhost:" + server.port() + DEFAULT_PATH;
        dispatcherUrl = "http://localhost:" + server.port() + DISPATCHER_PATH;
    }

    @AfterAll
    public static void shutdownWireMock() {
        if (server.isRunning()) {
            server.shutdown();
        }
    }

    /**
     * Tests that an {@link OkHttpAsyncHttpClient} is able to be built from an existing {@link OkHttpClient}.
     */
    @Test
    void buildClientWithExistingClient() {
        OkHttpClient existingClient = new OkHttpClient.Builder()
                .addInterceptor(chain -> chain
                        .proceed(chain.request().newBuilder().addHeader("Cookie", "test=success").build()))
                .build();

        OkHttpAsyncHttpClient client = (OkHttpAsyncHttpClient) new OkHttpAsyncHttpClientBuilder(existingClient)
                .build();

        StepVerifier.create(client.send(new HttpRequest(HttpMethod.GET, cookieValidatorUrl)))
                .assertNext(response -> assertEquals(200, response.getStatusCode()))
                .verifyComplete();
    }

    /**
     * Tests that instantiating an {@link OkHttpAsyncHttpClientBuilder} with a {@code null} {@link OkHttpClient} will
     * throw a {@link NullPointerException}.
     */
    @Test
    void startingWithNullClientThrows() {
        assertThrows(NullPointerException.class, () -> new OkHttpAsyncHttpClientBuilder(null));
    }

    /**
     * Tests that adding an {@link Interceptor} is handled correctly.
     */
    @Test
    void addNetworkInterceptor() {
        Interceptor testInterceptor = chain -> chain.proceed(chain.request().newBuilder()
                .addHeader("Cookie", "test=success").build());

        OkHttpAsyncHttpClient client = (OkHttpAsyncHttpClient) new OkHttpAsyncHttpClientBuilder()
                .addNetworkInterceptor(testInterceptor)
                .build();

        StepVerifier.create(client.send(new HttpRequest(HttpMethod.GET, cookieValidatorUrl)))
                .assertNext(response -> assertEquals(200, response.getStatusCode()))
                .verifyComplete();
    }

    /**
     * Tests that adding a {@code null} {@link Interceptor} will throw a {@link NullPointerException}.
     */
    @Test
    void nullNetworkInterceptorThrows() {
        assertThrows(NullPointerException.class, () -> new OkHttpAsyncHttpClientBuilder().addNetworkInterceptor(null));
    }

    /**
     * Tests that the {@link Interceptor interceptors} in the client are replace-able by setting a new list of
     * interceptors.
     */
    @Test
    void setNetworkInterceptors() {
        Interceptor badCookieSetter = chain -> chain.proceed(chain.request().newBuilder()
                .addHeader("Cookie", "test=failure").build());
        Interceptor goodCookieSetter = chain -> chain.proceed(chain.request().newBuilder()
                .addHeader("Cookie", "test=success").build());

        OkHttpAsyncHttpClient client = (OkHttpAsyncHttpClient) new OkHttpAsyncHttpClientBuilder()
                .addNetworkInterceptor(badCookieSetter)
                .networkInterceptors(Collections.singletonList(goodCookieSetter))
                .build();

        StepVerifier.create(client.send(new HttpRequest(HttpMethod.GET, cookieValidatorUrl)))
                .assertNext(response -> assertEquals(200, response.getStatusCode()))
                .verifyComplete();
    }

    /**
     * Tests that setting the {@link Interceptor interceptors} to {@code null} will throw a {@link
     * NullPointerException}.
     */
    @Test
    void nullNetworkInterceptorsThrows() {
        assertThrows(NullPointerException.class, () -> new OkHttpAsyncHttpClientBuilder().networkInterceptors(null));
    }

    /**
     * Tests building a client with a given {@code connectionTimeout}.
     */
    @Test
    void buildWithConnectionTimeout() {
        int expectedConnectionTimeoutMillis = 3600 * 1000;
        Interceptor validatorInterceptor = chain -> {
            assertEquals(expectedConnectionTimeoutMillis, chain.connectTimeoutMillis());
            return chain.proceed(chain.request());
        };

        OkHttpAsyncHttpClient okClient = (OkHttpAsyncHttpClient) new OkHttpAsyncHttpClientBuilder()
                .addNetworkInterceptor(validatorInterceptor)
                .connectionTimeout(Duration.ofSeconds(3600))
                .build();

        StepVerifier.create(okClient.send(new HttpRequest(HttpMethod.GET, defaultUrl)))
                .assertNext(response -> assertEquals(200, response.getStatusCode()))
                .verifyComplete();
    }

    /**
     * Tests building a client with a given {@code connectionTimeout}.
     */
    @Test
    void buildWithReadTimeout() {
        int expectedReadTimeoutMillis = 3600 * 1000;
        Interceptor validatorInterceptor = chain -> {
            assertEquals(expectedReadTimeoutMillis, chain.readTimeoutMillis());
            return chain.proceed(chain.request());
        };

        OkHttpAsyncHttpClient okClient = (OkHttpAsyncHttpClient) new OkHttpAsyncHttpClientBuilder()
                .addNetworkInterceptor(validatorInterceptor)
                .readTimeout(Duration.ofSeconds(3600))
                .build();

        StepVerifier.create(okClient.send(new HttpRequest(HttpMethod.GET, defaultUrl)))
                .assertNext(response -> assertEquals(200, response.getStatusCode()))
                .verifyComplete();
    }

    /**
     * Tests building a client with a given {@code connectionPool}.
     */
    @Test
    void buildWithConnectionPool() {
        ConnectionPool connectionPool = new ConnectionPool();

        OkHttpAsyncHttpClient okClient = (OkHttpAsyncHttpClient) new OkHttpAsyncHttpClientBuilder()
                .connectionPool(connectionPool)
                .build();

        StepVerifier.create(okClient.send(new HttpRequest(HttpMethod.GET, defaultUrl)))
                .assertNext(response -> assertEquals(200, response.getStatusCode()))
                .verifyComplete();

        assertEquals(1, connectionPool.connectionCount());
    }

    /**
     * Tests that passing a {@code null} {@code connectionPool} to the builder will throw a
     * {@link NullPointerException}.
     */
    @Test
    void nullConnectionPoolThrows() {
        assertThrows(NullPointerException.class, () -> new OkHttpAsyncHttpClientBuilder().connectionPool(null));
    }

    /**
     * Tests building a client with a given {@code dispatcher}.
     */
    @Test
    void buildWithDispatcher() {
        String expectedThreadName = "testDispatcher";
        Dispatcher dispatcher = new Dispatcher(Executors
                .newFixedThreadPool(1, (Runnable r) -> new Thread(r, expectedThreadName)));

        OkHttpAsyncHttpClient okClient = (OkHttpAsyncHttpClient) new OkHttpAsyncHttpClientBuilder()
                .dispatcher(dispatcher)
                .build();

        /*
         * Schedule a task that will run in one second to cancel all requests sent using the dispatcher. This should
         * result in the request we are about to send to be cancelled since WireMock will wait 5 seconds before
         * returning a response.
         */
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                assertEquals(1, dispatcher.runningCallsCount());
                dispatcher.cancelAll();
            }
        }, 1000);

        StepVerifier.create(okClient.send(new HttpRequest(HttpMethod.GET, dispatcherUrl)))
                .verifyError();
    }

    /**
     * Tests that passing a {@code null} {@code dispatcher} to the builder will throw a {@link NullPointerException}.
     */
    @Test
    void nullDispatcherThrows() {
        assertThrows(NullPointerException.class, () -> new OkHttpAsyncHttpClientBuilder().dispatcher(null));
    }

    @Test
    void buildWithConfigurationNone() {
        OkHttpAsyncHttpClient okClient = (OkHttpAsyncHttpClient) new OkHttpAsyncHttpClientBuilder()
                .configuration(Configuration.NONE)
                .build();

        StepVerifier.create(okClient.send(new HttpRequest(HttpMethod.GET, defaultUrl)))
                .assertNext(response -> assertEquals(200, response.getStatusCode()))
                .verifyComplete();
    }

    private static final class TestEventListenerValidator extends EventListener {
        private final String expectedProxyHost;
        private final int expectedProxyPort;

        private TestEventListenerValidator(String expectedProxyHost, int expectedProxyPort) {
            this.expectedProxyHost = expectedProxyHost;
            this.expectedProxyPort = expectedProxyPort;
        }

        @Override
        public void connectStart(Call call, InetSocketAddress inetSocketAddress, Proxy proxy) {
            InetSocketAddress proxyAddress = (InetSocketAddress) proxy.address();
            assertNotNull(proxyAddress);
            assertEquals(expectedProxyHost, proxyAddress.getHostName());
            assertEquals(expectedProxyPort, proxyAddress.getPort());
            super.connectStart(call, inetSocketAddress, proxy);
        }
    }
}