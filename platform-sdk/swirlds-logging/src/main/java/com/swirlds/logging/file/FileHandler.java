/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.swirlds.logging.file;

import com.swirlds.config.api.Configuration;
import com.swirlds.logging.api.Level;
import com.swirlds.logging.api.extensions.event.LogEvent;
import com.swirlds.logging.api.extensions.handler.AbstractSyncedHandler;
import com.swirlds.logging.api.internal.format.FormattedLinePrinter;
import com.swirlds.logging.buffer.BufferedOutputStream;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * A file handler that writes log events to a file.
 * <p>
 * This handler use a {@link BufferedOutputStream} to write {@link LogEvent}s to a file. You can configure the following
 * properties:
 * <ul>
 *     <li>{@code file} - the {@link Path} of the file</li>
 *     <li>{@code append} - whether to append to the file or not</li>
 * </ul>
 */
public class FileHandler extends AbstractSyncedHandler {

    private static final String FILE_NAME_PROPERTY = "%s.file";
    private static final String APPEND_PROPERTY = "%s.append";
    private static final String DEFAULT_FILE_NAME = "swirlds-log.log";
    private static final int BUFFER_CAPACITY = 8192 * 8;
    private final OutputStream outputStream;
    private final FormattedLinePrinter format;

    /**
     * Creates a new file handler.
     *
     * @param handlerName   the unique handler name
     * @param configuration the configuration
     * @param buffered      if true a buffer is used in between the file writing
     */
    public FileHandler(
            @NonNull final String handlerName, @NonNull final Configuration configuration, final boolean buffered)
            throws IOException {
        super(handlerName, configuration);

        this.format = FormattedLinePrinter.createForHandler(handlerName, configuration);

        final String propertyPrefix = PROPERTY_HANDLER.formatted(handlerName);
        final Path filePath = Objects.requireNonNullElse(
                configuration.getValue(FILE_NAME_PROPERTY.formatted(propertyPrefix), Path.class, null),
                Path.of(DEFAULT_FILE_NAME));
        final boolean append = Objects.requireNonNullElse(
                configuration.getValue(APPEND_PROPERTY.formatted(propertyPrefix), Boolean.class, null), true);
        try {
            if (Files.exists(filePath) && !(append && Files.isWritable(filePath))) {
                throw new IOException("Log file exist and is not writable or is not append mode");
            }
            if (filePath.getParent() != null) Files.createDirectories(filePath.getParent());
            final OutputStream fileOutputStream = new FileOutputStream(filePath.toFile(), append);
            this.outputStream =
                    buffered ? new BufferedOutputStream(fileOutputStream, BUFFER_CAPACITY) : fileOutputStream;
        } catch (IOException e) {
            throw new IOException("Could not create log file " + filePath.toAbsolutePath(), e);
        }
    }

    /**
     * Handles a log event by appending it to the file using the {@link FormattedLinePrinter}.
     *
     * @param event The log event to be printed.
     */
    @Override
    protected void handleEvent(@NonNull final LogEvent event) {
        final StringBuilder writer = new StringBuilder(4 * 1024);
        format.print(writer, event);
        try {
            this.outputStream.write(writer.toString().getBytes(StandardCharsets.UTF_8));
        } catch (final Exception exception) {
            EMERGENCY_LOGGER.log(Level.ERROR, "Failed to write to file output stream", exception);
        }
    }

    /**
     * Stops the handler and no further events are processed
     */
    @Override
    protected void handleStopAndFinalize() {
        super.handleStopAndFinalize();
        try {
            outputStream.close();
        } catch (final Exception exception) {
            EMERGENCY_LOGGER.log(Level.ERROR, "Failed to close file output stream", exception);
        }
    }
}
