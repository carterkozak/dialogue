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
import static org.junit.Assert.fail;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.palantir.conjure.java.api.errors.ErrorType;
import com.palantir.conjure.java.api.errors.RemoteException;
import com.palantir.conjure.java.api.errors.SerializableError;
import com.palantir.conjure.java.api.errors.ServiceException;
import com.palantir.conjure.java.serialization.ObjectMappers;
import com.palantir.dialogue.ErrorDecoder;
import com.palantir.dialogue.Response;
import com.palantir.logsafe.Preconditions;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.exceptions.SafeRuntimeException;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import javax.annotation.CheckForNull;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public final class DefaultErrorDecoderTest {

    private static final ObjectMapper SERVER_MAPPER = ObjectMappers.newServerObjectMapper();

    private static final ServiceException SERVICE_EXCEPTION =
            new ServiceException(ErrorType.FAILED_PRECONDITION, SafeArg.of("key", "value"));
    private static final String SERIALIZED_EXCEPTION = createServiceException(SERVICE_EXCEPTION);

    private static String createServiceException(ServiceException exception) {
        try {
            return SERVER_MAPPER.writeValueAsString(SerializableError.forException(exception));
        } catch (JsonProcessingException e) {
            fail("failed to serialize");
            return "";
        }
    }

    private static final ErrorDecoder decoder = DefaultErrorDecoder.INSTANCE;

    @Test
    public void extractsRemoteExceptionForAllErrorCodes() {
        for (int code : ImmutableList.of(300, 400, 404, 500)) {
            RemoteException exception = decode("application/json", code, SERIALIZED_EXCEPTION);
            assertThat(exception.getCause()).isNull();
            assertThat(exception.getStatus()).isEqualTo(code);
            assertThat(exception.getError().errorCode()).isEqualTo(ErrorType.FAILED_PRECONDITION.code().name());
            assertThat(exception.getError().errorName()).isEqualTo(ErrorType.FAILED_PRECONDITION.name());
            assertThat(exception.getMessage()).isEqualTo(
                    "RemoteException: " + ErrorType.FAILED_PRECONDITION.code().name()
                            + " (" + ErrorType.FAILED_PRECONDITION.name() + ") with instance ID "
                            + SERVICE_EXCEPTION.getErrorInstanceId());
        }
    }

    @Test
    public void testSpecificException() {
        RemoteException exception = encodeAndDecode(new IllegalArgumentException("msg"));
        assertThat(exception).isInstanceOf(RemoteException.class);
        assertThat(exception.getMessage()).startsWith("RemoteException: java.lang.IllegalArgumentException (msg)");
    }

    @Test
    public void cannotDecodeNonJsonMediaTypes() {
        assertThatThrownBy(() -> decode("text/plain", 500, SERIALIZED_EXCEPTION))
                .isInstanceOf(SafeRuntimeException.class)
                .hasMessage("Failed to interpret response body as SerializableError: {code=500}");
    }

    @Test
    public void doesNotHandleUnparseableBody() {
        assertThatThrownBy(() -> decode("application/json/", 500, "not json"))
                .isInstanceOf(SafeRuntimeException.class)
                .hasMessageStartingWith("Failed to interpret response body as SerializableError:");
    }

    @Test
    public void doesNotHandleNullBody() {
        assertThatThrownBy(() -> decode("application/json", 500, null))
                .isInstanceOf(SafeRuntimeException.class)
                .hasMessageStartingWith(
                        "Failed to deserialize response body as JSON, could not deserialize SerializableError:");
    }

    private static RemoteException encodeAndDecode(Exception exception) {
        Preconditions.checkArgument(!(exception instanceof ServiceException), "Use SerializableError#forException");
        Object error = SerializableError.builder()
                .errorCode(exception.getClass().getName())
                .errorName(exception.getMessage())
                .build();
        String json;
        try {
            json = SERVER_MAPPER.writeValueAsString(error);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

        // TODO(rfink): Resurrect
        // int status = (exception instanceof WebApplicationException)
        //         ? ((WebApplicationException) exception).getResponse().getStatus()
        //         : 400;
        int status = 400;
        return decode("application/json", status, json);
    }

    private static RemoteException decode(String contentType, int status, @CheckForNull String body) {
        return decoder.decode(response(status, contentType, body));
    }

    private static Response response(int code, String mediaType, @CheckForNull String body) {
        return new Response() {
            @Override
            public InputStream body() {
                if (body == null) {
                    return new ByteArrayInputStream(new byte[] {});
                } else {
                    return new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8));
                }
            }

            @Override
            public int code() {
                return code;
            }

            @Override
            public Optional<String> contentType() {
                return Optional.of(mediaType);
            }
        };
    }
}
