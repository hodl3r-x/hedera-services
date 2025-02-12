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

package com.hedera.services.bdd.suites.fees;

import static com.hedera.services.bdd.spec.HapiSpec.CostSnapshotMode;
import static com.hedera.services.bdd.spec.HapiSpec.CostSnapshotMode.TAKE;
import static com.hedera.services.bdd.spec.HapiSpec.customHapiSpec;
import static com.hedera.services.bdd.spec.assertions.AssertUtils.inOrder;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.isLiteralResult;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.resultWith;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.keys.KeyShape.SIMPLE;
import static com.hedera.services.bdd.spec.keys.KeyShape.listOf;
import static com.hedera.services.bdd.spec.keys.KeyShape.threshOf;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.contractCallLocal;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountRecords;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.suites.contract.Utils.FunctionType.FUNCTION;
import static com.hedera.services.bdd.suites.contract.Utils.getABIFor;
import static java.util.stream.Collectors.toList;

import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.HapiTestSuite;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.keys.KeyShape;
import com.hedera.services.bdd.suites.HapiSuite;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.TransferList;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@HapiTestSuite
public class CostOfEverythingSuite extends HapiSuite {
    private static final Logger log = LogManager.getLogger(CostOfEverythingSuite.class);
    private static final String PAYING_SENDER = "payingSender";
    private static final String RECEIVER = "receiver";
    private static final String CANONICAL = "canonical";
    private static final String CIVILIAN = "civilian";
    private static final String HAIR_TRIGGER_PAYER = "hairTriggerPayer";
    private static final String COST_SNAPSHOT_MODE = "cost.snapshot.mode";

    CostSnapshotMode costSnapshotMode = TAKE;
    //	CostSnapshotMode costSnapshotMode = COMPARE;

    public static void main(String... args) {
        new CostOfEverythingSuite().runSuiteSync();
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return Stream.of(
                        //				cryptoCreatePaths(),
                        //				cryptoTransferPaths(),
                        //				cryptoGetAccountInfoPaths(),
                        //				cryptoGetAccountRecordsPaths(),
                        //				transactionGetRecordPaths(),
                        miscContractCreatesAndCalls())
                .map(Stream::of)
                .reduce(Stream.empty(), Stream::concat)
                .collect(toList());
    }

    HapiSpec[] transactionGetRecordPaths() {
        return new HapiSpec[] {
            txnGetCreateRecord(), txnGetSmallTransferRecord(), txnGetLargeTransferRecord(),
        };
    }

    @HapiTest
    HapiSpec miscContractCreatesAndCalls() {
        // Note that contracts are prohibited to sending value to system
        // accounts below 0.0.750
        Object[] donationArgs = new Object[] {800L, "Hey, Ma!"};
        final var multipurposeContract = "Multipurpose";
        final var lookupContract = "BalanceLookup";

        return customHapiSpec("MiscContractCreatesAndCalls")
                .withProperties(Map.of(COST_SNAPSHOT_MODE, costSnapshotMode.toString()))
                .given(
                        cryptoCreate(CIVILIAN).balance(ONE_HUNDRED_HBARS),
                        uploadInitCode(multipurposeContract, lookupContract))
                .when(
                        contractCreate(multipurposeContract)
                                .payingWith(CIVILIAN)
                                .balance(652),
                        contractCreate(lookupContract).payingWith(CIVILIAN).balance(256))
                .then(
                        contractCall(multipurposeContract, "believeIn", 256L).payingWith(CIVILIAN),
                        contractCallLocal(multipurposeContract, "pick")
                                .payingWith(CIVILIAN)
                                .logged()
                                .has(resultWith()
                                        .resultThruAbi(
                                                getABIFor(FUNCTION, "pick", multipurposeContract),
                                                isLiteralResult(new Object[] {256L}))),
                        contractCall(multipurposeContract, "donate", donationArgs)
                                .payingWith(CIVILIAN),
                        contractCallLocal(lookupContract, "lookup", spec -> new Object[] {
                                    BigInteger.valueOf(spec.registry()
                                            .getAccountID(CIVILIAN)
                                            .getAccountNum())
                                })
                                .payingWith(CIVILIAN)
                                .logged());
    }

    @HapiTest
    HapiSpec txnGetCreateRecord() {
        return customHapiSpec("TxnGetCreateRecord")
                .withProperties(Map.of(COST_SNAPSHOT_MODE, costSnapshotMode.toString()))
                .given(cryptoCreate(HAIR_TRIGGER_PAYER).balance(99_999_999_999L).sendThreshold(1L))
                .when(cryptoCreate("somebodyElse")
                        .payingWith(HAIR_TRIGGER_PAYER)
                        .via("txn"))
                .then(getTxnRecord("txn").logged());
    }

    @HapiTest
    HapiSpec txnGetSmallTransferRecord() {
        return customHapiSpec("TxnGetSmalTransferRecord")
                .withProperties(Map.of(COST_SNAPSHOT_MODE, costSnapshotMode.toString()))
                .given(cryptoCreate(HAIR_TRIGGER_PAYER).sendThreshold(1L))
                .when(cryptoTransfer(tinyBarsFromTo(GENESIS, FUNDING, 1L))
                        .payingWith(HAIR_TRIGGER_PAYER)
                        .via("txn"))
                .then(getTxnRecord("txn").logged());
    }

    @HapiTest
    HapiSpec txnGetLargeTransferRecord() {
        return customHapiSpec("TxnGetLargeTransferRecord")
                .withProperties(Map.of(COST_SNAPSHOT_MODE, costSnapshotMode.toString()))
                .given(
                        cryptoCreate(HAIR_TRIGGER_PAYER).sendThreshold(1L),
                        cryptoCreate("a"),
                        cryptoCreate("b"),
                        cryptoCreate("c"),
                        cryptoCreate("d"))
                .when(cryptoTransfer(spec -> TransferList.newBuilder()
                                .addAccountAmounts(aa(spec, GENESIS, -4L))
                                .addAccountAmounts(aa(spec, "a", 1L))
                                .addAccountAmounts(aa(spec, "b", 1L))
                                .addAccountAmounts(aa(spec, "c", 1L))
                                .addAccountAmounts(aa(spec, "d", 1L))
                                .build())
                        .payingWith(HAIR_TRIGGER_PAYER)
                        .via("txn"))
                .then(getTxnRecord("txn").logged());
    }

    private AccountAmount aa(HapiSpec spec, String id, long amount) {
        return AccountAmount.newBuilder()
                .setAmount(amount)
                .setAccountID(spec.registry().getAccountID(id))
                .build();
    }

    HapiSpec[] cryptoGetAccountRecordsPaths() {
        return new HapiSpec[] {
            cryptoGetRecordsHappyPathS(), cryptoGetRecordsHappyPathM(), cryptoGetRecordsHappyPathL(),
        };
    }

    @HapiTest
    HapiSpec cryptoGetRecordsHappyPathS() {
        return customHapiSpec("CryptoGetRecordsHappyPathS")
                .withProperties(Map.of(COST_SNAPSHOT_MODE, costSnapshotMode.toString()))
                .given(cryptoCreate(HAIR_TRIGGER_PAYER).sendThreshold(1L))
                .when(cryptoTransfer(tinyBarsFromTo(GENESIS, FUNDING, 1L)).payingWith(HAIR_TRIGGER_PAYER))
                .then(getAccountRecords(HAIR_TRIGGER_PAYER).has(inOrder(recordWith())));
    }

    @HapiTest
    HapiSpec cryptoGetRecordsHappyPathM() {
        return customHapiSpec("CryptoGetRecordsHappyPathM")
                .withProperties(Map.of(COST_SNAPSHOT_MODE, costSnapshotMode.toString()))
                .given(cryptoCreate(HAIR_TRIGGER_PAYER).sendThreshold(1L))
                .when(
                        cryptoTransfer(tinyBarsFromTo(GENESIS, FUNDING, 1L)).payingWith(HAIR_TRIGGER_PAYER),
                        cryptoTransfer(tinyBarsFromTo(GENESIS, FUNDING, 1L)).payingWith(HAIR_TRIGGER_PAYER))
                .then(getAccountRecords(HAIR_TRIGGER_PAYER).has(inOrder(recordWith(), recordWith())));
    }

    @HapiTest
    HapiSpec cryptoGetRecordsHappyPathL() {
        return customHapiSpec("CryptoGetRecordsHappyPathL")
                .withProperties(Map.of(COST_SNAPSHOT_MODE, costSnapshotMode.toString()))
                .given(cryptoCreate(HAIR_TRIGGER_PAYER).sendThreshold(1L))
                .when(
                        cryptoTransfer(tinyBarsFromTo(GENESIS, FUNDING, 1L)).payingWith(HAIR_TRIGGER_PAYER),
                        cryptoTransfer(tinyBarsFromTo(GENESIS, FUNDING, 1L)).payingWith(HAIR_TRIGGER_PAYER),
                        cryptoTransfer(tinyBarsFromTo(GENESIS, FUNDING, 1L)).payingWith(HAIR_TRIGGER_PAYER),
                        cryptoTransfer(tinyBarsFromTo(GENESIS, FUNDING, 1L)).payingWith(HAIR_TRIGGER_PAYER),
                        cryptoTransfer(tinyBarsFromTo(GENESIS, FUNDING, 1L)).payingWith(HAIR_TRIGGER_PAYER))
                .then(getAccountRecords(HAIR_TRIGGER_PAYER)
                        .has(inOrder(recordWith(), recordWith(), recordWith(), recordWith(), recordWith())));
    }

    HapiSpec[] cryptoGetAccountInfoPaths() {
        return new HapiSpec[] {cryptoGetAccountInfoHappyPath()};
    }

    @HapiTest
    HapiSpec cryptoGetAccountInfoHappyPath() {
        KeyShape smallKey = threshOf(1, 3);
        KeyShape midsizeKey = listOf(SIMPLE, listOf(2), threshOf(1, 2));
        KeyShape hugeKey = threshOf(4, SIMPLE, SIMPLE, listOf(4), listOf(3), listOf(2));

        return customHapiSpec("CryptoGetAccountInfoHappyPath")
                .withProperties(Map.of(COST_SNAPSHOT_MODE, costSnapshotMode.toString()))
                .given(
                        newKeyNamed("smallKey").shape(smallKey),
                        newKeyNamed("midsizeKey").shape(midsizeKey),
                        newKeyNamed("hugeKey").shape(hugeKey))
                .when(
                        cryptoCreate("small").key("smallKey"),
                        cryptoCreate("midsize").key("midsizeKey"),
                        cryptoCreate("huge").key("hugeKey"))
                .then(getAccountInfo("small"), getAccountInfo("midsize"), getAccountInfo("huge"));
    }

    HapiSpec[] cryptoCreatePaths() {
        return new HapiSpec[] {
            cryptoCreateSimpleKey(),
        };
    }

    @HapiTest
    HapiSpec cryptoCreateSimpleKey() {
        KeyShape shape = SIMPLE;

        return customHapiSpec("SuccessfulCryptoCreate")
                .withProperties(Map.of(COST_SNAPSHOT_MODE, costSnapshotMode.toString()))
                .given(newKeyNamed("key").shape(shape))
                .when()
                .then(cryptoCreate("a").key("key"));
    }

    HapiSpec[] cryptoTransferPaths() {
        return new HapiSpec[] {
            cryptoTransferGenesisToFunding(),
        };
    }

    @HapiTest
    HapiSpec cryptoTransferGenesisToFunding() {
        return customHapiSpec("CryptoTransferGenesisToFunding")
                .withProperties(Map.of(COST_SNAPSHOT_MODE, costSnapshotMode.toString()))
                .given()
                .when()
                .then(cryptoTransfer(tinyBarsFromTo(GENESIS, FUNDING, 1_000L)));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
