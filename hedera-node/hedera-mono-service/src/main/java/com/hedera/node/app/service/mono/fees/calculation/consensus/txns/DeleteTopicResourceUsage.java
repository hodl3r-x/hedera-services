/*
 * Copyright (C) 2020-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.mono.fees.calculation.consensus.txns;

import com.hedera.node.app.hapi.utils.fee.ConsensusServiceFeeBuilder;
import com.hedera.node.app.hapi.utils.fee.SigValueObj;
import com.hedera.node.app.service.mono.context.primitives.StateView;
import com.hedera.node.app.service.mono.fees.calculation.TxnResourceUsageEstimator;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.TransactionBody;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public final class DeleteTopicResourceUsage implements TxnResourceUsageEstimator {
    @Inject
    public DeleteTopicResourceUsage() {
        /* No-op */
    }

    @Override
    public boolean applicableTo(final TransactionBody txn) {
        return txn.hasConsensusDeleteTopic();
    }

    @Override
    public FeeData usageGiven(final TransactionBody txn, final SigValueObj sigUsage, final StateView view) {
        return ConsensusServiceFeeBuilder.getConsensusDeleteTopicFee(txn, sigUsage);
    }
}
