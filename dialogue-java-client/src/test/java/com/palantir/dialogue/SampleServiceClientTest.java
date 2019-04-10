/*
 * (c) Copyright 2019 Palantir Technologies Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.palantir.dialogue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.common.collect.ImmutableList;
import com.palantir.conjure.java.api.config.ssl.SslConfiguration;
import com.palantir.conjure.java.config.ssl.SslSocketFactories;
import com.palantir.conjure.java.dialogue.serde.DefaultConjureRuntime;
import com.palantir.conjure.java.dialogue.serde.DefaultErrorDecoder;
import com.palantir.dialogue.example.AsyncSampleService;
import com.palantir.dialogue.example.SampleService;
import com.palantir.dialogue.example.SampleServiceClient;
import java.net.URL;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLParameters;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public final class SampleServiceClientTest {

    private static final ConjureRuntime runtime = DefaultConjureRuntime.builder().build();
    private static final SslConfiguration SSL_CONFIG = SslConfiguration.of(
            Paths.get("src/test/resources/trustStore.jks"),
            Paths.get("src/test/resources/keyStore.jks"),
            "keystore");

    @Rule
    public final MockWebServer server = new MockWebServer();

    private URL baseUrl;
    private SampleService blockingClient;
    private AsyncSampleService asyncClient;

    private static final ImmutableList<String> FAST_CIPHER_SUITES = ImmutableList.of(
            "TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA384",
            "TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA256",
            "TLS_ECDH_RSA_WITH_AES_256_CBC_SHA384",
            "TLS_ECDH_RSA_WITH_AES_128_CBC_SHA256",
            "TLS_RSA_WITH_AES_128_CBC_SHA256",
            "TLS_RSA_WITH_AES_256_CBC_SHA256",
            "TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA",
            "TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA",
            "TLS_ECDH_RSA_WITH_AES_256_CBC_SHA",
            "TLS_ECDH_RSA_WITH_AES_128_CBC_SHA",
            "TLS_RSA_WITH_AES_256_CBC_SHA",
            "TLS_RSA_WITH_AES_128_CBC_SHA",
            // TODO(rfink): These don't work with Java11, see https://bugs.openjdk.java.net/browse/JDK-8204192
            // "TLS_ECDHE_ECDSA_WITH_CHACHA20_POLY1305_SHA256",
            // "TLS_ECDHE_RSA_WITH_CHACHA20_POLY1305_SHA256",
            "TLS_EMPTY_RENEGOTIATION_INFO_SCSV");

    private static final ImmutableList<String> GCM_CIPHER_SUITES = ImmutableList.of(
            "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384",
            "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256",
            "TLS_ECDH_RSA_WITH_AES_256_GCM_SHA384",
            "TLS_ECDH_RSA_WITH_AES_128_GCM_SHA256",
            "TLS_RSA_WITH_AES_256_GCM_SHA384",
            "TLS_RSA_WITH_AES_128_GCM_SHA256");

    private static final String[] ALL_CIPHER_SUITES = ImmutableList.builder()
            .addAll(FAST_CIPHER_SUITES)
            .addAll(GCM_CIPHER_SUITES)
            .build()
            .toArray(new String[0]);

    @Before
    public void before() {
        server.useHttps(SslSocketFactories.createSslSocketFactory(SSL_CONFIG), false);
        baseUrl = Urls.https("localhost", server.getPort());
        Channel channel = createChannel(baseUrl, Duration.ofSeconds(1));
        blockingClient = SampleServiceClient.blocking(channel, runtime);
        asyncClient = SampleServiceClient.async(channel, runtime);
    }

    private HttpChannel createChannel(URL url, Duration timeout) {
        SSLParameters sslConfig = new SSLParameters(
                ALL_CIPHER_SUITES,
                new String[] {"TLSv1.2"});
        return HttpChannel.of(
                // TODO(rfink): Read timeout
                // TODO(rfink): Write timeout
                HttpClient.newBuilder()
                        .connectTimeout(timeout)
                        .sslParameters(sslConfig)
                        .sslContext(SslSocketFactories.createSslContext(SSL_CONFIG))
                        .build(),
                url,
                DefaultErrorDecoder.INSTANCE);
    }

    @Test
    public void testBlocking_stringToString_expectedCase() throws Exception {
        server.enqueue(
                new MockResponse().setBody("\"myResponse\"").addHeader(Headers.CONTENT_TYPE, "application/json"));
        assertThat(blockingClient.stringToString("myObject", "myHeader", "myBody")).isEqualTo("myResponse");
        RecordedRequest request = server.takeRequest();
        assertThat(request.getPath()).isEqualTo("/stringToString/objects/myObject");
        assertThat(request.getHeader("headerKey")).isEqualTo("myHeader");
        assertThat(request.getBody().readString(StandardCharsets.UTF_8)).isEqualTo("\"myBody\"");
    }

    @Test
    public void testBlocking_stringToString_nullRequestBody() throws Exception {
        assertThatThrownBy(() -> blockingClient.stringToString("myObject", "myHeader", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("body parameter must not be null");
    }

    @Test
    public void testAsync_stringToString_expectedCase() throws Exception {
        server.enqueue(
                new MockResponse().setBody("\"myResponse\"").addHeader(Headers.CONTENT_TYPE, "application/json"));
        assertThat(asyncClient.stringToString("myObject", "myHeader", "myBody").get()).isEqualTo("myResponse");
    }

    @Test
    public void testBlocking_stringToString_throwsWhenResponseBodyIsEmpty() throws Exception {
        server.enqueue(new MockResponse().addHeader(Headers.CONTENT_TYPE, "application/json"));
        assertThatThrownBy(() -> blockingClient.stringToString("myObject", "myHeader", "myBody"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to deserialize response stream. Syntax error?");
    }

    @Test
    public void testAsync_stringToString_throwsWhenResponseBodyIsEmpty() throws Exception {
        server.enqueue(new MockResponse().addHeader(Headers.CONTENT_TYPE, "application/json"));
        assertThatThrownBy(() -> asyncClient.stringToString("myObject", "myHeader", "myBody").get())
                .hasMessageContaining("Failed to deserialize response");
    }

    @Test
    public void testAsync_stringToString_nullRequestBody() throws Exception {
        assertThatThrownBy(() -> asyncClient.stringToString("myObject", "myHeader", null).get())
                .isInstanceOf(NullPointerException.class)
                .hasMessage("body parameter must not be null");
    }

    @Test
    public void testBlocking_voidToVoid_expectedCase() throws Exception {
        server.enqueue(new MockResponse());
        blockingClient.voidToVoid();
        RecordedRequest request = server.takeRequest();
        assertThat(request.getPath()).isEqualTo("/voidToVoid");
    }

    @Test
    public void testAsync_voidToVoid_expectedCase() throws Exception {
        server.enqueue(new MockResponse());
        assertThat(asyncClient.voidToVoid().get()).isNull();
    }

    @Test
    public void testBlocking_voidToVoid_throwsWhenResponseBodyIsNonEmpty() throws Exception {
        server.enqueue(new MockResponse().setBody("Unexpected response"));
        assertThatThrownBy(() -> blockingClient.voidToVoid())
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Expected empty response body");
    }

    @Test
    public void testAsync_voidToVoid_throwsWhenResponseBodyIsNonEmpty() throws Exception {
        server.enqueue(new MockResponse().setBody("Unexpected response"));
        assertThatThrownBy(() -> asyncClient.voidToVoid().get())
                .hasMessageContaining("Expected empty response body");
    }

    @Test
    public void testBlocking_throwsOnConnectError() throws Exception {
        blockingClient = SampleServiceClient.blocking(createChannel(baseUrl, Duration.ofSeconds(1)), runtime);
        server.shutdown();
        assertThatThrownBy(() -> blockingClient.stringToString("", "", ""))
                .isInstanceOf(RuntimeException.class)
                .hasMessageStartingWith("java.net.ConnectException: Connection refused");
    }

    @Ignore("TODO(rfink): Figure out how to inject read/write timeouts")
    @Test(timeout = 2_000)
    public void testBlocking_throwsOnTimeout() throws Exception {
        blockingClient = SampleServiceClient.blocking(createChannel(baseUrl, Duration.ofMillis(500)), runtime);
        server.enqueue(new MockResponse()
                .setBody("\"response\"")
                .addHeader(Headers.CONTENT_TYPE, "application/json")
                .setBodyDelay(10, TimeUnit.SECONDS));
        assertThatThrownBy(() -> blockingClient.stringToString("", "", ""))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to deserialize response");
    }

    @Test
    public void testAsync_throwsOnConnectError() throws Exception {
        asyncClient = SampleServiceClient.async(createChannel(baseUrl, Duration.ofMillis(10)), runtime);
        server.shutdown();

        assertThatThrownBy(() -> asyncClient.voidToVoid().get()).hasMessageContaining("Connection refused");
    }
}
