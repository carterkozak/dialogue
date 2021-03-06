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

import java.net.URL;
import java.util.concurrent.Executors;
import okhttp3.Dispatcher;
import okhttp3.OkHttpClient;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public final class OkHttpChannelTest extends AbstractChannelTest {

    @Override
    Channel createChannel(URL baseUrl) {
        OkHttpClient client = new OkHttpClient()
                .newBuilder()
                .dispatcher(new Dispatcher(Executors.newSingleThreadExecutor()))
                .build();
        return OkHttpChannel.of(client, baseUrl);
    }
}
