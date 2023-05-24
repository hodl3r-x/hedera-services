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

package com.hedera.node.config.testfixtures;

import com.hedera.node.config.converter.AccountIDConverter;
import com.hedera.node.config.converter.CongestionMultipliersConverter;
import com.hedera.node.config.converter.ContractIDConverter;
import com.hedera.node.config.converter.EntityScaleFactorsConverter;
import com.hedera.node.config.converter.EntityTypeConverter;
import com.hedera.node.config.converter.FileIDConverter;
import com.hedera.node.config.converter.HederaFunctionalityConverter;
import com.hedera.node.config.converter.KeyValuePairConverter;
import com.hedera.node.config.converter.KnownBlockValuesConverter;
import com.hedera.node.config.converter.LegacyContractIdActivationsConverter;
import com.hedera.node.config.converter.MapAccessTypeConverter;
import com.hedera.node.config.converter.ProfileConverter;
import com.hedera.node.config.converter.RecomputeTypeConverter;
import com.hedera.node.config.converter.ScaleFactorConverter;
import com.hedera.node.config.converter.SidecarTypeConverter;
import com.hedera.node.config.validation.EmulatesMapValidator;
import com.swirlds.common.config.ConfigUtils;
import com.swirlds.common.config.singleton.ConfigurationHolder;
import com.swirlds.common.config.sources.SimpleConfigSource;
import com.swirlds.common.threading.locks.AutoClosableLock;
import com.swirlds.common.threading.locks.Locks;
import com.swirlds.common.threading.locks.locked.Locked;
import com.swirlds.common.utility.CommonUtils;
import com.swirlds.config.api.Configuration;
import com.swirlds.config.api.ConfigurationBuilder;
import com.swirlds.config.api.converter.ConfigConverter;
import com.swirlds.config.api.source.ConfigSource;
import com.swirlds.config.api.validation.ConfigValidator;
import com.swirlds.test.framework.config.TestConfigBuilder;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * A builder for creating {@link Configuration} instances for testing. In future this builder can be based on
 * {@link TestConfigBuilder} and most of the code can be removed. See {@link TestConfigBuilder} for more details.
 */
public class HederaTestConfigBuilder {

    private final AutoClosableLock configLock;
    private Configuration configuration;
    private final ConfigurationBuilder builder;

    public HederaTestConfigBuilder() {
        this(true);
    }

    public HederaTestConfigBuilder(boolean registerAllTypes) {
        this.configLock = Locks.createAutoLock();
        this.configuration = null;
        if (registerAllTypes) {
            this.builder = ConfigUtils.scanAndRegisterAllConfigTypes(ConfigurationBuilder.create());
        } else {
            this.builder = ConfigurationBuilder.create();
        }
        this.builder
                .withConverter(new CongestionMultipliersConverter())
                .withConverter(new EntityScaleFactorsConverter())
                .withConverter(new EntityTypeConverter())
                .withConverter(new KnownBlockValuesConverter())
                .withConverter(new LegacyContractIdActivationsConverter())
                .withConverter(new MapAccessTypeConverter())
                .withConverter(new RecomputeTypeConverter())
                .withConverter(new ScaleFactorConverter())
                .withConverter(new AccountIDConverter())
                .withConverter(new ContractIDConverter())
                .withConverter(new FileIDConverter())
                .withConverter(new HederaFunctionalityConverter())
                .withConverter(new ProfileConverter())
                .withConverter(new SidecarTypeConverter())
                .withConverter(new KeyValuePairConverter())
                .withValidator(new EmulatesMapValidator());
    }

    public HederaTestConfigBuilder withValue(@NonNull String propertyName, @NonNull String value) {
        return this.withSource(new SimpleConfigSource(propertyName, value));
    }

    public HederaTestConfigBuilder withValue(@NonNull String propertyName, int value) {
        return this.withSource(new SimpleConfigSource(propertyName, value));
    }

    public HederaTestConfigBuilder withValue(@NonNull String propertyName, double value) {
        return this.withSource(new SimpleConfigSource(propertyName, value));
    }

    public HederaTestConfigBuilder withValue(@NonNull String propertyName, long value) {
        return this.withSource(new SimpleConfigSource(propertyName, value));
    }

    public HederaTestConfigBuilder withValue(@NonNull String propertyName, boolean value) {
        return this.withSource(new SimpleConfigSource(propertyName, value));
    }

    public HederaTestConfigBuilder withValue(@NonNull String propertyName, @NonNull Object value) {
        CommonUtils.throwArgNull(value, "value");
        return this.withSource(new SimpleConfigSource(propertyName, value.toString()));
    }

    public Configuration getOrCreateConfig() {
        try (final Locked ignore = configLock.lock()) {
            if (configuration == null) {
                configuration = builder.build();
                ConfigurationHolder.getInstance().setConfiguration(configuration);
            }
            return configuration;
        }
    }

    private void checkConfigState() {
        try (final Locked ignore = configLock.lock()) {
            if (configuration != null) {
                throw new IllegalStateException("Configuration already created!");
            }
        }
    }

    public HederaTestConfigBuilder withSource(@NonNull ConfigSource configSource) {
        this.checkConfigState();
        this.builder.withSource(configSource);
        return this;
    }

    public HederaTestConfigBuilder withConverter(@NonNull ConfigConverter<?> configConverter) {
        this.checkConfigState();
        this.builder.withConverter(configConverter);
        return this;
    }

    public HederaTestConfigBuilder withValidator(@NonNull ConfigValidator configValidator) {
        this.checkConfigState();
        this.builder.withValidator(configValidator);
        return this;
    }

    public <T extends Record> HederaTestConfigBuilder withConfigDataType(@NonNull Class<T> configDataType) {
        this.checkConfigState();
        this.builder.withConfigDataType(configDataType);
        return this;
    }
}