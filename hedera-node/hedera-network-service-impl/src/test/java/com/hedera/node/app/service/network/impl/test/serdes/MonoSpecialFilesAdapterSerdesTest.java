/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hedera.node.app.service.network.impl.test.serdes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.hedera.node.app.service.mono.state.merkle.MerkleSpecialFiles;
import com.hedera.node.app.service.mono.state.merkle.internals.BytesElement;
import com.hedera.node.app.service.network.impl.serdes.MonoSpecialFilesAdapterSerdes;
import com.hederahashgraph.api.proto.java.FileID;
import com.swirlds.common.constructable.ClassConstructorPair;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.fcqueue.FCQueue;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

class MonoSpecialFilesAdapterSerdesTest {
    private static final FileID SOME_FILE_ID =
            FileID.newBuilder().setFileNum(666).build();
    private static final MerkleSpecialFiles SOME_SPECIAL_FILES = new MerkleSpecialFiles();

    static {
        SOME_SPECIAL_FILES.append(SOME_FILE_ID, "abcdef".getBytes());
    }

    @Mock
    private DataInput input;

    @Mock
    private DataOutput output;

    final MonoSpecialFilesAdapterSerdes subject = new MonoSpecialFilesAdapterSerdes();

    @Test
    void doesntSupportUnnecessary() {
        assertThrows(UnsupportedOperationException.class, subject::typicalSize);
        assertThrows(UnsupportedOperationException.class, () -> subject.measure(input));
        assertThrows(UnsupportedOperationException.class, () -> subject.fastEquals(SOME_SPECIAL_FILES, input));
    }

    @Test
    void canSerializeAndDeserializeFromAppropriateStream() throws IOException, ConstructableRegistryException {
        ConstructableRegistry.getInstance()
                .registerConstructable(new ClassConstructorPair(FCQueue.class, FCQueue::new));
        ConstructableRegistry.getInstance()
                .registerConstructable(new ClassConstructorPair(BytesElement.class, BytesElement::new));
        final var baos = new ByteArrayOutputStream();
        final var actualOut = new SerializableDataOutputStream(baos);
        subject.write(SOME_SPECIAL_FILES, actualOut);
        actualOut.flush();

        final var actualIn = new SerializableDataInputStream(new ByteArrayInputStream(baos.toByteArray()));
        final var parsed = subject.parse(actualIn);
        assertEquals(SOME_SPECIAL_FILES.getHash(), parsed.getHash());
    }

    @Test
    void doesntSupportOtherStreams() {
        assertThrows(IllegalArgumentException.class, () -> subject.parse(input));
        assertThrows(IllegalArgumentException.class, () -> subject.write(SOME_SPECIAL_FILES, output));
    }
}