// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.evm.properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.EVM_VERSION;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.EVM_VERSION_0_30;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.EVM_VERSION_0_34;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.EVM_VERSION_0_38;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.EVM_VERSION_0_46;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.EVM_VERSION_0_50;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.EVM_VERSION_0_51;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mockStatic;

import com.hedera.hapi.node.base.SemanticVersion;
import java.util.Collections;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes32;
import org.hiero.mirror.common.CommonProperties;
import org.hiero.mirror.common.domain.SystemEntity;
import org.hiero.mirror.common.domain.transaction.RecordFile;
import org.hiero.mirror.web3.common.ContractCallContext;
import org.hiero.mirror.web3.evm.properties.MirrorNodeEvmProperties.HederaNetwork;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MirrorNodeEvmPropertiesTest {
    private static final int MAX_REFUND_PERCENT = 100;
    private static final int MAX_CUSTOM_FEES_ALLOWED = 10;
    private static final Address FUNDING_ADDRESS = Address.fromHexString("0x0000000000000000000000000000000000000062");
    private static final Bytes32 CHAIN_ID = Bytes32.fromHexString("0x0128");

    private final CommonProperties commonProperties = new CommonProperties();
    private final SystemEntity systemEntity = new SystemEntity(commonProperties);
    private final MirrorNodeEvmProperties properties = new MirrorNodeEvmProperties(commonProperties, systemEntity);

    @AutoClose
    private final MockedStatic<ContractCallContext> staticMock = mockStatic(ContractCallContext.class);

    @Mock
    private ContractCallContext contractCallContext;

    private static NavigableMap<Long, SemanticVersion> createEvmVersionsMapCustom() {
        NavigableMap<Long, SemanticVersion> evmVersions = new TreeMap<>();
        evmVersions.put(0L, EVM_VERSION_0_30);
        evmVersions.put(50L, EVM_VERSION_0_34);
        evmVersions.put(100L, EVM_VERSION_0_38);
        evmVersions.put(150L, EVM_VERSION_0_46);
        evmVersions.put(200L, EVM_VERSION_0_50);
        evmVersions.put(250L, EVM_VERSION_0_51);
        return Collections.unmodifiableNavigableMap(evmVersions);
    }

    private static NavigableMap<Long, SemanticVersion> createEvmVersionsMapMainnet() {
        NavigableMap<Long, SemanticVersion> evmVersions = new TreeMap<>();
        evmVersions.put(0L, EVM_VERSION_0_30);
        evmVersions.put(44029066L, EVM_VERSION_0_34);
        evmVersions.put(49117794L, EVM_VERSION_0_38);
        evmVersions.put(60258042L, EVM_VERSION_0_46);
        evmVersions.put(65435845L, EVM_VERSION_0_50);
        evmVersions.put(66602102L, EVM_VERSION_0_51);
        return Collections.unmodifiableNavigableMap(evmVersions);
    }

    private static Stream<Arguments> blockNumberToEvmVersionProviderCustom() {
        return blockNumberToEvmVersionProvider(createEvmVersionsMapCustom());
    }

    private static Stream<Arguments> blockNumberToEvmVersionProviderMainnet() {
        return blockNumberToEvmVersionProvider(createEvmVersionsMapMainnet());
    }

    private static Stream<Arguments> blockNumberToEvmVersionProvider(NavigableMap<Long, SemanticVersion> evmVersions) {
        Stream.Builder<Arguments> argumentsBuilder = Stream.builder();

        Long firstKey = evmVersions.firstKey();
        // return default EVM version for key - 1 since none will be found
        argumentsBuilder.add(Arguments.of(firstKey - 1, EVM_VERSION));

        for (Map.Entry<Long, SemanticVersion> entry : evmVersions.entrySet()) {
            Long key = entry.getKey();
            var currentValue = entry.getValue();
            // Test the block number just before the key (key - 1) if it's not the first key
            if (!key.equals(firstKey)) {
                var lowerValue = evmVersions.lowerEntry(key).getValue();
                argumentsBuilder.add(Arguments.of(key - 1, lowerValue));
            }

            // test the exact key
            argumentsBuilder.add(Arguments.of(key, currentValue));

            // Test the next block number after the key (key + 1)
            argumentsBuilder.add(Arguments.of(key + 1, currentValue));
        }
        return argumentsBuilder.build();
    }

    @BeforeEach
    void setup() {
        properties.setEvmVersions(new TreeMap<>());
    }

    @Test
    void correctPropertiesEvaluation() {
        staticMock.when(ContractCallContext::get).thenReturn(contractCallContext);
        given(contractCallContext.useHistorical()).willReturn(false);
        assertThat(properties.evmVersion()).isEqualTo(EVM_VERSION.toString());
        assertThat(properties.dynamicEvmVersion()).isTrue();
        assertThat(properties.maxGasRefundPercentage()).isEqualTo(MAX_REFUND_PERCENT);
        assertThat(properties.fundingAccountAddress()).isEqualTo(FUNDING_ADDRESS);
        assertThat(properties.isRedirectTokenCallsEnabled()).isTrue();
        assertThat(properties.isLazyCreationEnabled()).isTrue();
        assertThat(properties.isCreate2Enabled()).isTrue();
        assertThat(properties.chainIdBytes32()).isEqualTo(CHAIN_ID);
        assertThat(properties.isLimitTokenAssociations()).isFalse();
        assertThat(properties.shouldAutoRenewAccounts()).isFalse();
        assertThat(properties.shouldAutoRenewContracts()).isFalse();
        assertThat(properties.shouldAutoRenewSomeEntityType()).isFalse();
        assertThat(properties.maxCustomFeesAllowed()).isEqualTo(MAX_CUSTOM_FEES_ALLOWED);
    }

    @ParameterizedTest
    @MethodSource("blockNumberToEvmVersionProviderCustom")
    void correctHistoricalEvmVersion(Long blockNumber, SemanticVersion expectedEvmVersion) {
        staticMock.when(ContractCallContext::get).thenReturn(contractCallContext);
        given(contractCallContext.useHistorical()).willReturn(true);
        var recordFile = new RecordFile();
        recordFile.setIndex(blockNumber);
        given(contractCallContext.getRecordFile()).willReturn(recordFile);
        properties.setEvmVersions(createEvmVersionsMapCustom());
        assertThat(properties.evmVersion()).isEqualTo(expectedEvmVersion.toString());
    }

    @ParameterizedTest
    @MethodSource("blockNumberToEvmVersionProviderMainnet")
    void getEvmVersionForBlockFromHederaNetwork(Long blockNumber, SemanticVersion expectedEvmVersion) {
        // given
        properties.setNetwork(HederaNetwork.MAINNET);

        var result = properties.getEvmVersionForBlock(blockNumber);
        assertThat(result).isEqualTo(expectedEvmVersion);
    }

    @ParameterizedTest
    @MethodSource("blockNumberToEvmVersionProviderCustom")
    void getEvmVersionForBlockFromConfig(Long blockNumber, SemanticVersion expectedEvmVersion) {
        // given
        properties.setEvmVersions(createEvmVersionsMapCustom());

        var result = properties.getEvmVersionForBlock(blockNumber);
        assertThat(result).isEqualTo(expectedEvmVersion);
    }
}
