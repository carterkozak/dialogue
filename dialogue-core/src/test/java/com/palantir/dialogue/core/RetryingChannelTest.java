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

package com.palantir.dialogue.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.palantir.dialogue.Channel;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.HttpMethod;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import com.palantir.dialogue.UrlBuilder;
import java.io.InputStream;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class RetryingChannelTest {
    private static final TestResponse EXPECTED_RESPONSE = new TestResponse();
    private static final ListenableFuture<Response> SUCCESS = Futures.immediateFuture(EXPECTED_RESPONSE);
    private static final ListenableFuture<Response> FAILED =
            Futures.immediateFailedFuture(new IllegalArgumentException());
    private static final TestEndpoint ENDPOINT = new TestEndpoint();
    private static final Request REQUEST = Request.builder().build();

    @Mock private Channel channel;
    private RetryingChannel retryer;

    @Before
    public void before() {
        retryer = new RetryingChannel(channel, 3);
    }

    @Test
    public void testNoFailures() throws ExecutionException, InterruptedException {
        when(channel.execute(any(), any()))
                .thenReturn(SUCCESS);

        ListenableFuture<Response> response = retryer.execute(ENDPOINT, REQUEST);
        assertThat(response.get()).isEqualTo(EXPECTED_RESPONSE);
    }

    @Test
    public void testRetriesUpToMaxRetries() throws ExecutionException, InterruptedException {
        when(channel.execute(any(), any()))
                .thenReturn(FAILED)
                .thenReturn(FAILED)
                .thenReturn(SUCCESS);

        ListenableFuture<Response> response = retryer.execute(ENDPOINT, REQUEST);
        assertThat(response.get()).isEqualTo(EXPECTED_RESPONSE);
    }

    @Test
    public void testRetriesMax() {
        when(channel.execute(any(), any()))
                .thenReturn(FAILED);

        ListenableFuture<Response> response = retryer.execute(ENDPOINT, REQUEST);
        assertThatThrownBy(response::get)
                .hasCauseInstanceOf(IllegalArgumentException.class);
        verify(channel, times(3)).execute(ENDPOINT, REQUEST);
    }

    private static final class TestResponse implements Response {
        @Override
        public InputStream body() {
            return InputStream.nullInputStream();
        }

        @Override
        public int code() {
            return 200;
        }

        @Override
        public Optional<String> contentType() {
            return Optional.empty();
        }
    }

    private static final class TestEndpoint implements Endpoint {
        @Override
        public void renderPath(Map<String, String> params, UrlBuilder url) {}

        @Override
        public HttpMethod httpMethod() {
            return HttpMethod.GET;
        }
    }
}
