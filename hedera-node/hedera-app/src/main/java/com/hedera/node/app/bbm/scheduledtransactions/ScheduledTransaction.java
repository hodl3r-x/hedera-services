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

package com.hedera.node.app.bbm.scheduledtransactions;

import com.hedera.hapi.node.state.schedule.Schedule;
import com.hedera.node.app.service.mono.legacy.core.jproto.JKey;
import com.hedera.node.app.service.mono.pbj.PbjConverter;
import com.hedera.node.app.service.mono.state.submerkle.EntityId;
import com.hedera.node.app.service.mono.state.submerkle.RichInstant;
import com.hedera.node.app.service.mono.state.virtual.schedule.ScheduleVirtualValue;
import com.hederahashgraph.api.proto.java.SchedulableTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.security.InvalidKeyException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@SuppressWarnings("java:S6218") // "Equals/hashcode methods should be overridden in records containing array fields"
record ScheduledTransaction(
        long number,
        @NonNull Optional<JKey> adminKey,
        @Nullable String memo,
        boolean deleted,
        boolean executed,
        boolean calculatedWaitForExpiry,
        boolean waitForExpiryProvided,
        @Nullable EntityId payer,
        @NonNull EntityId schedulingAccount,
        @NonNull RichInstant schedulingTXValidStart,
        @Nullable RichInstant expirationTimeProvided,
        @Nullable RichInstant calculatedExpirationTime,
        @Nullable RichInstant resolutionTime,
        @NonNull byte[] bodyBytes,
        @Nullable TransactionBody ordinaryScheduledTxn,
        @Nullable SchedulableTransactionBody scheduledTxn,
        @Nullable List<byte[]> signatories) {

    static ScheduledTransaction fromMod(@NonNull final Schedule value) throws InvalidKeyException {
        return new ScheduledTransaction(
                value.scheduleId().scheduleNum(),
                value.adminKey() != null ? Optional.of(JKey.mapKey(value.adminKey())) : Optional.empty(),
                value.memo(),
                value.deleted(),
                value.executed(),
                // calculatedWaitForExpiry is the same as waitForExpiryProvided;
                // see ScheduleVirtualValue::from` - to.calculatedWaitForExpiry = to.waitForExpiryProvided;
                value.waitForExpiry(),
                value.waitForExpiry(),
                entityIdFrom(value.payerAccountId().accountNum()),
                entityIdFrom(value.schedulerAccountId().accountNum()),
                RichInstant.fromJava(Instant.ofEpochSecond(
                        value.scheduleValidStart().seconds(),
                        value.scheduleValidStart().nanos())),
                RichInstant.fromJava(Instant.ofEpochSecond(value.providedExpirationSecond())),
                RichInstant.fromJava(Instant.ofEpochSecond(value.calculatedExpirationSecond())),
                RichInstant.fromJava(Instant.ofEpochSecond(
                        value.resolutionTime().seconds(), value.resolutionTime().nanos())),
                PbjConverter.fromPbj(value.originalCreateTransaction()).toByteArray(),
                PbjConverter.fromPbj(value.originalCreateTransaction()),
                PbjConverter.fromPbj(value.scheduledTransaction()),
                value.signatories().stream()
                        .map(ScheduledTransaction::toPrimitiveKey)
                        .toList());
    }

    static ScheduledTransaction fromMono(@NonNull final ScheduleVirtualValue scheduleVirtualValue) {
        return new ScheduledTransaction(
                scheduleVirtualValue.getKey().getKeyAsLong(),
                scheduleVirtualValue.adminKey(),
                scheduleVirtualValue.memo().orElse("<EMPTY>"),
                scheduleVirtualValue.isDeleted(),
                scheduleVirtualValue.isExecuted(),
                scheduleVirtualValue.calculatedWaitForExpiry(),
                scheduleVirtualValue.waitForExpiryProvided(),
                scheduleVirtualValue.payer(),
                scheduleVirtualValue.schedulingAccount(),
                scheduleVirtualValue.schedulingTXValidStart(),
                scheduleVirtualValue.expirationTimeProvided(),
                scheduleVirtualValue.calculatedExpirationTime(),
                scheduleVirtualValue.getResolutionTime(),
                scheduleVirtualValue.bodyBytes(),
                scheduleVirtualValue.ordinaryViewOfScheduledTxn(),
                scheduleVirtualValue.scheduledTxn(),
                scheduleVirtualValue.signatories());
    }

    static EntityId entityIdFrom(long num) {
        return new EntityId(0L, 0L, num);
    }

    static byte[] toPrimitiveKey(com.hedera.hapi.node.base.Key key) {
        if (key.hasEd25519()) {
            return key.ed25519().toByteArray();
        } else if (key.hasEcdsaSecp256k1()) {
            return key.ecdsaSecp256k1().toByteArray();
        } else {
            return new byte[] {};
        }
    }
}
