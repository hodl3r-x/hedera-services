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

package com.hedera.node.app.bbm.singleton;

import com.hedera.hapi.node.state.token.NetworkStakingRewards;
import com.hedera.node.app.service.mono.state.merkle.MerkleNetworkContext;
import edu.umd.cs.findbugs.annotations.NonNull;

public record StakingRewards(
        boolean stakingRewardsActivated, long totalStakedRewardStart, long totalStakedStart, long pendingRewards) {

    public static StakingRewards fromMono(@NonNull final MerkleNetworkContext merkleNetworkContext) {

        return new StakingRewards(
                merkleNetworkContext.areRewardsActivated(),
                merkleNetworkContext.getTotalStakedRewardStart(),
                merkleNetworkContext.getTotalStakedStart(),
                merkleNetworkContext.pendingRewards());
    }

    public static StakingRewards fromMod(@NonNull final NetworkStakingRewards networkStakingRewards) {
        return new StakingRewards(
                networkStakingRewards.stakingRewardsActivated(),
                networkStakingRewards.totalStakedRewardStart(),
                networkStakingRewards.totalStakedStart(),
                networkStakingRewards.pendingRewards());
    }
}
