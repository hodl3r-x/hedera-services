/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.spi.validation;

import com.hedera.hapi.node.base.Key;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * A type that any {@link TransactionHandler} can use to validate entity
 * attributes like memos or keys.
 */
public interface AttributeValidator {
    int MAX_NESTED_KEY_LEVELS = 15;

    /**
     * Validates the given key. If the key is more than allowed depth, throws {@code ResponseCodeEnum.BAD_ENCODING}
     * Then validates each key in the given structure. If the key is not valid throws {@code ResponseCodeEnum.BAD_ENCODING}
     *
     * @param key the key to validate
     * @throws HandleException if the key is invalid or more than {@value MAX_NESTED_KEY_LEVELS}
     */
    void validateKey(@NonNull Key key);

    /**
     * Validates the given memo.
     *
     * @param memo the memo to validate
     * @throws HandleException if the key is invalid
     */
    void validateMemo(@Nullable String memo);

    /**
     * Validates the given expiry.
     *
     * @param expiry the expiry to validate
     * @throws HandleException if the expiry is invalid
     */
    void validateExpiry(long expiry);

    /**
     * Validates the given auto-renew period.
     *
     * @param autoRenewPeriod the auto-renew period to validate
     * @throws HandleException if the auto-renew period is invalid
     */
    void validateAutoRenewPeriod(long autoRenewPeriod);

    /**
     * Validates if immutable entity with the key
     *
     * @param key the key to validate
     * @return true if immutable entity with the key
     */
    boolean isImmutableKey(@NonNull Key key);
}
