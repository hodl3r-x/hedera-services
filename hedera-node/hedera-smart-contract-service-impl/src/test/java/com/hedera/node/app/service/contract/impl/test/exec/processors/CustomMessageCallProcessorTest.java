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

package com.hedera.node.app.service.contract.impl.test.exec.processors;

import static com.hedera.node.app.service.contract.impl.test.TestHelpers.isSameResult;
import static org.hyperledger.besu.datatypes.Address.ALTBN128_ADD;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.hedera.node.app.service.contract.impl.exec.AddressChecks;
import com.hedera.node.app.service.contract.impl.exec.FeatureFlags;
import com.hedera.node.app.service.contract.impl.exec.failure.CustomExceptionalHaltReason;
import com.hedera.node.app.service.contract.impl.exec.processors.CustomMessageCallProcessor;
import com.hedera.node.app.service.contract.impl.state.ProxyWorldUpdater;
import com.hedera.node.app.service.contract.impl.test.TestHelpers;
import com.swirlds.config.api.Configuration;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.operation.Operation;
import org.hyperledger.besu.evm.precompile.PrecompileContractRegistry;
import org.hyperledger.besu.evm.precompile.PrecompiledContract;
import org.hyperledger.besu.evm.tracing.OperationTracer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CustomMessageCallProcessorTest {
    private static final long GAS_REQUIREMENT = 2L;
    private static final Bytes INPUT_DATA = Bytes.fromHexString("0x1234");
    private static final Bytes OUTPUT_DATA = Bytes.fromHexString("0x5678");
    private static final Address NON_EVM_PRECOMPILE_SYSTEM_ADDRESS = Address.fromHexString("0x222");
    private static final Address CODE_ADDRESS = Address.fromHexString("0x111222333");
    private static final Address SENDER_ADDRESS = Address.fromHexString("0x222333444");
    private static final Address RECEIVER_ADDRESS = Address.fromHexString("0x33344455");

    @Mock
    private EVM evm;

    @Mock
    private MessageFrame frame;

    @Mock
    private FeatureFlags featureFlags;

    @Mock
    private AddressChecks addressChecks;

    @Mock
    private PrecompiledContract htsPrecompile;

    @Mock
    private PrecompiledContract nativePrecompile;

    @Mock
    private OperationTracer operationTracer;

    @Mock
    private PrecompileContractRegistry registry;

    @Mock
    private Configuration config;

    @Mock
    private ProxyWorldUpdater proxyWorldUpdater;

    private CustomMessageCallProcessor subject;

    @BeforeEach
    void setUp() {
        subject = new CustomMessageCallProcessor(
                evm, featureFlags, registry, addressChecks, Map.of(TestHelpers.HTS_PRECOMPILE_ADDRESS, htsPrecompile));
    }

    @Test
    void delegatesLazyCreationCheck() {
        given(featureFlags.isImplicitCreationEnabled(config)).willReturn(true);
        assertTrue(subject.isImplicitCreationEnabled(config));
    }

    @Test
    void hederaPrecompilesNotYetSupported() {
        givenCallWithCode(TestHelpers.HTS_PRECOMPILE_ADDRESS);
        assertThrows(UnsupportedOperationException.class, () -> subject.start(frame, operationTracer));
    }

    @Test
    void callsToNonStandardSystemContractsAreNotSupported() {
        final var isHalted = new AtomicBoolean();
        givenHaltableFrame(isHalted);
        givenCallWithCode(NON_EVM_PRECOMPILE_SYSTEM_ADDRESS);
        given(addressChecks.isSystemAccount(NON_EVM_PRECOMPILE_SYSTEM_ADDRESS)).willReturn(true);

        subject.start(frame, operationTracer);

        verifyHalt(ExceptionalHaltReason.PRECOMPILE_ERROR);
    }

    @Test
    void valueCannotBeTransferredToSystemContracts() {
        final var isHalted = new AtomicBoolean();
        givenHaltableFrame(isHalted);
        givenCallWithCode(ALTBN128_ADD);
        given(addressChecks.isSystemAccount(ALTBN128_ADD)).willReturn(true);
        given(registry.get(ALTBN128_ADD)).willReturn(nativePrecompile);
        given(frame.getValue()).willReturn(Wei.ONE);

        subject.start(frame, operationTracer);

        verifyHalt(CustomExceptionalHaltReason.INVALID_VALUE_TRANSFER);
    }

    @Test
    void haltsIfValueTransferFails() {
        final var isHalted = new AtomicBoolean();
        givenWellKnownUserSpaceCall();
        givenHaltableFrame(isHalted);
        given(frame.getValue()).willReturn(Wei.ONE);
        given(frame.getWorldUpdater()).willReturn(proxyWorldUpdater);
        given(addressChecks.isPresent(RECEIVER_ADDRESS, frame)).willReturn(true);
        given(proxyWorldUpdater.tryTransferFromContract(SENDER_ADDRESS, RECEIVER_ADDRESS, Wei.ONE.toLong(), true))
                .willReturn(Optional.of(ExceptionalHaltReason.ILLEGAL_STATE_CHANGE));

        subject.start(frame, operationTracer);

        verifyHalt(ExceptionalHaltReason.ILLEGAL_STATE_CHANGE);
    }

    @Test
    void haltsIfPrecompileGasRequirementExceedsRemaining() {
        final var isHalted = new AtomicBoolean();
        givenHaltableFrame(isHalted);
        givenEvmPrecompileCall();
        given(nativePrecompile.gasRequirement(INPUT_DATA)).willReturn(GAS_REQUIREMENT);
        given(frame.getRemainingGas()).willReturn(1L);

        subject.start(frame, operationTracer);

        verifyHalt(ExceptionalHaltReason.INSUFFICIENT_GAS, false);
    }

    @Test
    void updatesFrameBySuccessfulPrecompileResultWithGasRefund() {
        givenEvmPrecompileCall();
        final var result = new PrecompiledContract.PrecompileContractResult(
                OUTPUT_DATA, true, MessageFrame.State.CODE_SUCCESS, Optional.empty());
        given(nativePrecompile.computePrecompile(INPUT_DATA, frame)).willReturn(result);
        given(nativePrecompile.gasRequirement(INPUT_DATA)).willReturn(GAS_REQUIREMENT);
        given(frame.getRemainingGas()).willReturn(3L);
        given(frame.getState()).willReturn(MessageFrame.State.COMPLETED_SUCCESS);

        subject.start(frame, operationTracer);

        verify(operationTracer).tracePrecompileCall(frame, GAS_REQUIREMENT, OUTPUT_DATA);
        verify(frame).decrementRemainingGas(GAS_REQUIREMENT);
        verify(frame).incrementRemainingGas(GAS_REQUIREMENT);
        verify(frame).setOutputData(OUTPUT_DATA);
        verify(frame).setState(MessageFrame.State.CODE_SUCCESS);
        verify(frame).setExceptionalHaltReason(Optional.empty());
    }

    @Test
    void revertsFrameFromPrecompileResult() {
        givenEvmPrecompileCall();
        final var result = new PrecompiledContract.PrecompileContractResult(
                OUTPUT_DATA, false, MessageFrame.State.REVERT, Optional.empty());
        given(nativePrecompile.computePrecompile(INPUT_DATA, frame)).willReturn(result);
        given(nativePrecompile.gasRequirement(INPUT_DATA)).willReturn(GAS_REQUIREMENT);
        given(frame.getRemainingGas()).willReturn(3L);
        given(frame.getState()).willReturn(MessageFrame.State.REVERT);

        subject.start(frame, operationTracer);

        verify(operationTracer).tracePrecompileCall(frame, GAS_REQUIREMENT, OUTPUT_DATA);
        verify(frame).decrementRemainingGas(GAS_REQUIREMENT);
        verify(frame).setRevertReason(OUTPUT_DATA);
        verify(frame, never()).setOutputData(OUTPUT_DATA);
        verify(frame).setState(MessageFrame.State.REVERT);
        verify(frame).setExceptionalHaltReason(Optional.empty());
    }

    @Test
    void triesLazyCreationBeforeValueTransferIfRecipientMissing() {
        givenWellKnownUserSpaceCall();
        given(frame.getValue()).willReturn(Wei.ONE);
        given(frame.getWorldUpdater()).willReturn(proxyWorldUpdater);
        given(proxyWorldUpdater.tryLazyCreation(RECEIVER_ADDRESS, frame)).willReturn(Optional.empty());
        given(proxyWorldUpdater.tryTransferFromContract(SENDER_ADDRESS, RECEIVER_ADDRESS, Wei.ONE.toLong(), true))
                .willReturn(Optional.empty());

        subject.start(frame, operationTracer);

        verify(frame).setState(MessageFrame.State.CODE_EXECUTING);
    }

    private void givenHaltableFrame(@NonNull final AtomicBoolean isHalted) {
        doAnswer(invocation -> {
                    isHalted.set(true);
                    return null;
                })
                .when(frame)
                .setExceptionalHaltReason(any());
        doAnswer(invocation -> isHalted.get() ? MessageFrame.State.EXCEPTIONAL_HALT : MessageFrame.State.NOT_STARTED)
                .when(frame)
                .getState();
    }

    private void givenCallWithCode(@NonNull final Address contract) {
        given(frame.getContractAddress()).willReturn(contract);
    }

    private void givenWellKnownUserSpaceCall() {
        given(frame.getContractAddress()).willReturn(CODE_ADDRESS);
        given(frame.getRecipientAddress()).willReturn(RECEIVER_ADDRESS);
        given(frame.getSenderAddress()).willReturn(SENDER_ADDRESS);
    }

    private void givenEvmPrecompileCall() {
        given(addressChecks.isSystemAccount(ALTBN128_ADD)).willReturn(true);
        given(registry.get(ALTBN128_ADD)).willReturn(nativePrecompile);
        given(frame.getContractAddress()).willReturn(ALTBN128_ADD);
        given(frame.getInputData()).willReturn(INPUT_DATA);
        given(frame.getValue()).willReturn(Wei.ZERO);
    }

    private void verifyHalt(@NonNull final ExceptionalHaltReason reason) {
        verifyHalt(reason, true);
    }

    private void verifyHalt(@NonNull final ExceptionalHaltReason reason, final boolean alsoVerifyTrace) {
        verify(frame).setExceptionalHaltReason(Optional.of(reason));
        verify(frame).setState(MessageFrame.State.EXCEPTIONAL_HALT);
        verify(frame, never()).setState(MessageFrame.State.CODE_EXECUTING);
        if (alsoVerifyTrace) {
            verify(operationTracer)
                    .tracePostExecution(
                            eq(frame),
                            argThat(result -> isSameResult(new Operation.OperationResult(0, reason), result)));
        }
    }
}