/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.evm.store.contracts.precompile.codec;

import org.hyperledger.besu.datatypes.Address;

public class TokenExpiryInfo {

    private long second;
    private Address autoRenewAccount;
    private long autoRenewPeriod;

    public TokenExpiryInfo(long second, Address autoRenewAccount, long autoRenewPeriod) {
        this.second = second;
        this.autoRenewAccount = autoRenewAccount;
        this.autoRenewPeriod = autoRenewPeriod;
    }

    public TokenExpiryInfo() {
        this.second = 0;
        this.autoRenewAccount = Address.ZERO;
        this.autoRenewPeriod = 0;
    }

    public long getSecond() {
        return second;
    }

    public Address getAutoRenewAccount() {
        return autoRenewAccount != null ? autoRenewAccount : Address.ZERO;
    }

    public long getAutoRenewPeriod() {
        return autoRenewPeriod;
    }
}
