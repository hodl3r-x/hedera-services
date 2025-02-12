/*
 * Copyright (C) 2016-2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.event.creation.tipset;

import static com.swirlds.logging.legacy.LogMarker.EXCEPTION;
import static com.swirlds.platform.event.creation.tipset.Tipset.merge;

import com.swirlds.base.time.Time;
import com.swirlds.common.platform.NodeId;
import com.swirlds.common.sequence.map.SequenceMap;
import com.swirlds.common.sequence.map.StandardSequenceMap;
import com.swirlds.common.utility.throttle.RateLimitedLogger;
import com.swirlds.platform.consensus.NonAncientEventWindow;
import com.swirlds.platform.event.AncientMode;
import com.swirlds.platform.system.address.AddressBook;
import com.swirlds.platform.system.events.EventDescriptor;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Computes and tracks tipsets for non-ancient events.
 */
public class TipsetTracker {

    private static final Logger logger = LogManager.getLogger(TipsetTracker.class);

    private static final int INITIAL_TIPSET_MAP_CAPACITY = 64;

    /**
     * Tipsets for all non-ancient events we know about.
     */
    private final SequenceMap<EventDescriptor, Tipset> tipsets;

    /**
     * This tipset is equivalent to a tipset that would be created by merging all tipsets of all events that this object
     * has ever observed. If you ask this tipset for the generation for a particular node, it will return the highest
     * generation of all events we have ever received from that node.
     */
    private Tipset latestGenerations;

    private final AddressBook addressBook;

    private final AncientMode ancientMode;
    private NonAncientEventWindow nonAncientEventWindow;

    private final RateLimitedLogger ancientEventLogger;

    /**
     * Create a new tipset tracker.
     *
     * @param time        provides wall clock time
     * @param addressBook the current address book
     * @param ancientMode the {@link AncientMode} to use
     */
    public TipsetTracker(
            @NonNull final Time time, @NonNull final AddressBook addressBook, @NonNull final AncientMode ancientMode) {

        this.addressBook = Objects.requireNonNull(addressBook);

        this.latestGenerations = new Tipset(addressBook);

        if (ancientMode == AncientMode.BIRTH_ROUND_THRESHOLD) {
            tipsets = new StandardSequenceMap<>(0, INITIAL_TIPSET_MAP_CAPACITY, true, EventDescriptor::getBirthRound);
        } else {
            tipsets = new StandardSequenceMap<>(0, INITIAL_TIPSET_MAP_CAPACITY, true, EventDescriptor::getGeneration);
        }

        ancientEventLogger = new RateLimitedLogger(logger, time, Duration.ofMinutes(1));

        this.ancientMode = Objects.requireNonNull(ancientMode);
        this.nonAncientEventWindow = NonAncientEventWindow.getGenesisNonAncientEventWindow(ancientMode);
    }

    /**
     * Set the non-ancient event window.
     *
     * @param nonAncientEventWindow the minimum non-ancient generation, all lower generations are ancient
     */
    public void setNonAncientEventWindow(@NonNull final NonAncientEventWindow nonAncientEventWindow) {
        this.nonAncientEventWindow = Objects.requireNonNull(nonAncientEventWindow);
        tipsets.shiftWindow(nonAncientEventWindow.getAncientThreshold());
    }

    /**
     * Get the current non-ancient event window (from this class's perspective).
     *
     * @return the non-ancient event window
     */
    @NonNull
    public NonAncientEventWindow getNonAncientEventWindow() {
        return nonAncientEventWindow;
    }

    /**
     * Add a new event to the tracker.
     *
     * @param eventDescriptor the descriptor of the event to add
     * @param parents         the parents of the event being added
     * @return the tipset for the event that was added
     */
    @NonNull
    public Tipset addEvent(
            @NonNull final EventDescriptor eventDescriptor, @NonNull final List<EventDescriptor> parents) {

        if (nonAncientEventWindow.isAncient(eventDescriptor)) {
            // Note: although we don't immediately return from this method, the tipsets.put()
            // will not update the data structure for an ancient event. We should never
            // enter this bock of code. This log is here as a canary to alert us if we somehow do.
            ancientEventLogger.error(
                    EXCEPTION.getMarker(),
                    "Rejecting ancient event from {} with generation {}. Current non-ancient event window is {}",
                    eventDescriptor.getCreator(),
                    eventDescriptor.getGeneration(),
                    nonAncientEventWindow);
        }

        final List<Tipset> parentTipsets = new ArrayList<>(parents.size());
        for (final EventDescriptor parent : parents) {
            final Tipset parentTipset = tipsets.get(parent);
            if (parentTipset != null) {
                parentTipsets.add(parentTipset);
            }
        }

        final Tipset eventTipset;
        if (parentTipsets.isEmpty()) {
            eventTipset =
                    new Tipset(addressBook).advance(eventDescriptor.getCreator(), eventDescriptor.getGeneration());
        } else {
            eventTipset = merge(parentTipsets).advance(eventDescriptor.getCreator(), eventDescriptor.getGeneration());
        }

        tipsets.put(eventDescriptor, eventTipset);
        latestGenerations = latestGenerations.advance(eventDescriptor.getCreator(), eventDescriptor.getGeneration());

        return eventTipset;
    }

    /**
     * Get the tipset of an event, or null if the event is not being tracked.
     *
     * @param eventDescriptor the fingerprint of the event
     * @return the tipset of the event, or null if the event is not being tracked
     */
    @Nullable
    public Tipset getTipset(@NonNull final EventDescriptor eventDescriptor) {
        return tipsets.get(eventDescriptor);
    }

    /**
     * Get the highest generation of all events we have received from a particular node.
     *
     * @param nodeId the node in question
     * @return the highest generation of all events received by a given node
     */
    public long getLatestGenerationForNode(@NonNull final NodeId nodeId) {
        return latestGenerations.getTipGenerationForNode(nodeId);
    }

    /**
     * Get number of tipsets being tracked.
     */
    public int size() {
        return tipsets.getSize();
    }

    /**
     * Reset the tipset tracker to its initial state.
     */
    public void clear() {
        nonAncientEventWindow = NonAncientEventWindow.getGenesisNonAncientEventWindow(ancientMode);
        latestGenerations = new Tipset(addressBook);
        tipsets.clear();
    }
}
