/*
 * Copyright (c) "Neo4j"
 * Neo4j Sweden AB [http://neo4j.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.kernel.impl.transaction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.neo4j.kernel.impl.transaction.log.entry.LogFormat.CURRENT_FORMAT_LOG_HEADER_SIZE;
import static org.neo4j.kernel.impl.transaction.log.entry.LogFormat.CURRENT_LOG_FORMAT_VERSION;
import static org.neo4j.kernel.impl.transaction.log.entry.LogSegments.UNKNOWN_LOG_SEGMENT_SIZE;
import static org.neo4j.storageengine.api.TransactionIdStore.BASE_TX_CHECKSUM;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.neo4j.io.fs.FileSystemAbstraction;
import org.neo4j.io.fs.StoreChannel;
import org.neo4j.kernel.impl.transaction.log.LogPosition;
import org.neo4j.kernel.impl.transaction.log.LogVersionedStoreChannel;
import org.neo4j.kernel.impl.transaction.log.PhysicalLogVersionedStoreChannel;
import org.neo4j.kernel.impl.transaction.log.ReaderLogVersionBridge;
import org.neo4j.kernel.impl.transaction.log.entry.LogHeader;
import org.neo4j.kernel.impl.transaction.log.entry.LogHeaderWriter;
import org.neo4j.kernel.impl.transaction.log.files.ChannelNativeAccessor;
import org.neo4j.kernel.impl.transaction.log.files.LogFiles;
import org.neo4j.kernel.impl.transaction.log.files.LogFilesBuilder;
import org.neo4j.kernel.impl.transaction.tracing.DatabaseTracer;
import org.neo4j.storageengine.api.StoreId;
import org.neo4j.test.extension.Inject;
import org.neo4j.test.extension.testdirectory.TestDirectoryExtension;
import org.neo4j.test.utils.TestDirectory;

@TestDirectoryExtension
class ReaderLogVersionBridgeTest {
    @Inject
    private TestDirectory testDirectory;

    private final FileSystemAbstraction fs = mock(FileSystemAbstraction.class);
    private final LogVersionedStoreChannel channel = mock(LogVersionedStoreChannel.class);

    private final long version = 10L;
    private LogFiles logFiles;

    @BeforeEach
    void setUp() throws Exception {
        logFiles = prepareLogFiles();
    }

    @Test
    void shouldOpenTheNextChannelWhenItExists() throws IOException {
        // given
        final StoreChannel newStoreChannel = mock(StoreChannel.class);
        final ReaderLogVersionBridge bridge = new ReaderLogVersionBridge(logFiles.getLogFile());

        when(channel.getVersion()).thenReturn(version);
        when(channel.getLogFormatVersion()).thenReturn(CURRENT_LOG_FORMAT_VERSION);
        when(fs.fileExists(any(Path.class))).thenReturn(true);
        when(fs.read(any(Path.class))).thenReturn(newStoreChannel);

        var storeId = new StoreId(1, 1, "engine-1", "format-1", 1, 1);
        when(newStoreChannel.read(ArgumentMatchers.<ByteBuffer>any())).then(new Answer<>() {
            private int count;

            @Override
            public Integer answer(InvocationOnMock invocation) throws IOException {
                if (count++ == 1) {
                    throw new AssertionError("Should only be called twice.");
                }
                ByteBuffer buffer = invocation.getArgument(0);

                LogHeader logHeader = new LogHeader(
                        CURRENT_LOG_FORMAT_VERSION,
                        new LogPosition(version + 1, CURRENT_FORMAT_LOG_HEADER_SIZE),
                        -1L,
                        storeId,
                        UNKNOWN_LOG_SEGMENT_SIZE,
                        BASE_TX_CHECKSUM);
                LogHeaderWriter.putHeader(buffer, logHeader);
                return CURRENT_FORMAT_LOG_HEADER_SIZE;
            }
        });

        // when
        final LogVersionedStoreChannel result = bridge.next(channel, false);

        // then
        PhysicalLogVersionedStoreChannel expected = new PhysicalLogVersionedStoreChannel(
                newStoreChannel,
                version + 1,
                CURRENT_LOG_FORMAT_VERSION,
                Path.of("log.file"),
                ChannelNativeAccessor.EMPTY_ACCESSOR,
                DatabaseTracer.NULL);
        assertEquals(expected, result);
        verify(channel).close();
    }

    @Test
    void shouldReturnOldChannelWhenThereIsNoNextChannel() throws IOException {
        // given
        final ReaderLogVersionBridge bridge = new ReaderLogVersionBridge(logFiles.getLogFile());

        when(channel.getVersion()).thenReturn(version);
        when(fs.read(any(Path.class))).thenThrow(new NoSuchFileException("mock"));

        // when
        final LogVersionedStoreChannel result = bridge.next(channel, false);

        // then
        assertEquals(channel, result);
        verify(channel, never()).close();
    }

    @Test
    void shouldReturnOldChannelWhenNextChannelHasNotGottenCompleteHeaderYet() throws Exception {
        // given
        final ReaderLogVersionBridge bridge = new ReaderLogVersionBridge(logFiles.getLogFile());
        final StoreChannel nextVersionWithIncompleteHeader = mock(StoreChannel.class);
        when(nextVersionWithIncompleteHeader.read(any(ByteBuffer.class)))
                .thenReturn(CURRENT_FORMAT_LOG_HEADER_SIZE / 2);

        when(channel.getVersion()).thenReturn(version);
        when(fs.fileExists(any(Path.class))).thenReturn(true);
        when(fs.read(any(Path.class))).thenReturn(nextVersionWithIncompleteHeader);

        // when
        final LogVersionedStoreChannel result = bridge.next(channel, false);

        // then
        assertEquals(channel, result);
        verify(channel, never()).close();
    }

    private LogFiles prepareLogFiles() throws IOException {
        return LogFilesBuilder.logFilesBasedOnlyBuilder(testDirectory.homePath(), fs)
                .build();
    }
}
