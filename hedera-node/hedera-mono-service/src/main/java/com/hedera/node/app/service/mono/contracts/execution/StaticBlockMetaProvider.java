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

package com.hedera.node.app.service.mono.contracts.execution;

import com.hedera.node.app.service.evm.contracts.execution.BlockMetaSource;
import com.hedera.node.app.service.mono.context.primitives.SignedStateViewFactory;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class StaticBlockMetaProvider {
    private final SignedStateViewFactory stateViewFactory;

    @Inject
    public StaticBlockMetaProvider(final SignedStateViewFactory stateViewFactory) {
        this.stateViewFactory = stateViewFactory;
    }

    public Optional<BlockMetaSource> getSource() {
        return stateViewFactory
                .childrenOfLatestSignedState()
                .map(children -> StaticBlockMetaSource.from(children.networkCtx()));
    }
}
