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

package com.palantir.conjure.java.dialogue.serde;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.common.collect.ImmutableList;
import com.palantir.dialogue.BodySerDe;
import com.palantir.dialogue.RequestBody;
import com.palantir.dialogue.Response;
import com.palantir.dialogue.TypeMarker;
import com.palantir.logsafe.Preconditions;
import com.palantir.logsafe.exceptions.SafeIllegalArgumentException;
import com.palantir.logsafe.exceptions.SafeRuntimeException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import org.junit.Test;

public class ConjureBodySerDeTest {

    private static final TypeMarker<String> TYPE = new TypeMarker<String>() {};

    @Test
    public void testRequestContentType() throws IOException {
        Encoding json = new StubEncoding("application/json");
        Encoding plain = new StubEncoding("text/plain");

        TestResponse response = new TestResponse();
        response.contentType = Optional.of("text/plain");
        BodySerDe serializers = new ConjureBodySerDe(ImmutableList.of(json, plain));
        String value = serializers.deserializer(TYPE).deserialize(response);
        assertThat(value).isEqualTo(plain.getContentType());
    }

    @Test
    public void testRequestNoContentType() {
        TestResponse response = new TestResponse();
        BodySerDe serializers = new ConjureBodySerDe(ImmutableList.of(new StubEncoding("application/json")));
        assertThatThrownBy(() -> serializers.deserializer(TYPE).deserialize(response))
                .isInstanceOf(SafeIllegalArgumentException.class)
                .hasMessageContaining("Response is missing Content-Type header");
    }

    @Test
    public void testUnsupportedRequestContentType() {
        TestResponse response = new TestResponse();
        response.contentType = Optional.of("application/unknown");
        BodySerDe serializers = new ConjureBodySerDe(ImmutableList.of(new StubEncoding("application/json")));
        assertThatThrownBy(() -> serializers.deserializer(TYPE).deserialize(response))
                .isInstanceOf(SafeRuntimeException.class)
                .hasMessageContaining("Unsupported Content-Type");
    }

    @Test
    public void testDefaultContentType() throws IOException {
        Encoding json = new StubEncoding("application/json");
        Encoding plain = new StubEncoding("text/plain");

        BodySerDe serializers = new ConjureBodySerDe(ImmutableList.of(plain, json)); // first encoding is default
        RequestBody body = serializers.serializer(TYPE).serialize("test");
        assertThat(body.contentType()).isEqualTo(plain.getContentType());
    }

    @Test
    public void testResponseNoContentType() throws IOException {
        Encoding json = new StubEncoding("application/json");
        Encoding plain = new StubEncoding("text/plain");

        BodySerDe serializers = new ConjureBodySerDe(ImmutableList.of(json, plain));
        RequestBody body = serializers.serializer(TYPE).serialize("test");
        assertThat(body.contentType()).isEqualTo(json.getContentType());
    }

    @Test
    public void testResponseUnknownContentType() throws IOException {
        Encoding json = new StubEncoding("application/json");
        Encoding plain = new StubEncoding("text/plain");

        TestResponse response = new TestResponse();
        response.contentType = Optional.of("application/unknown");
        BodySerDe serializers = new ConjureBodySerDe(ImmutableList.of(json, plain));
        RequestBody body = serializers.serializer(TYPE).serialize("test");
        assertThat(body.contentType()).isEqualTo(json.getContentType());
    }

    /** Deserializes requests as the configured content type. */
    public static final class StubEncoding implements Encoding {

        private final String contentType;

        StubEncoding(String contentType) {
            this.contentType = contentType;
        }

        @Override
        public <T> Serializer<T> serializer(TypeMarker<T> type) {
            return (value, output) -> {
                // nop
            };
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> Deserializer<T> deserializer(TypeMarker<T> type) {
            return input -> {
                Preconditions.checkArgument(TYPE.equals(type), "This stub encoding only supports String");
                return (T) getContentType();
            };
        }

        @Override
        public String getContentType() {
            return contentType;
        }

        @Override
        public boolean supportsContentType(String input) {
            return contentType.equals(input);
        }

        @Override
        public String toString() {
            return "StubEncoding{" + contentType + '}';
        }
    }

    private static class TestResponse implements Response {

        private InputStream body = new ByteArrayInputStream(new byte[] {});
        private int code = 0;
        private Optional<String> contentType = Optional.empty();

        @Override
        public InputStream body() {
            return body;
        }

        @Override
        public int code() {
            return code;
        }

        @Override
        public Optional<String> contentType() {
            return contentType;
        }
    }
}
