/*
 * Copyright 2016 Palantir Technologies, Inc. All rights reserved.
 */
package com.palantir.config.service.proxy;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Predicates;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.Lists;
import com.palantir.config.service.BasicCredentials;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * Provides a {@link com.palantir.config.service.proxy.ProxyConfiguration} based on system properties.
 */
public final class SystemPropertiesProxyConfigurationProvider implements ProxyConfigurationProvider {

    private static final List<String> HOST_PROPERTIES = Lists.newArrayList(
            "http.proxyHost", "https.proxyHost", "socksProxyHost");
    private static final List<String> PORT_PROPERTIES = Lists.newArrayList(
            "http.proxyPort", "https.proxyPort", "socksProxyPort");
    private static final List<String> USERNAME_PROPERTIES = Lists.newArrayList(
            "http.proxyUser", "https.proxyUser", "socksProxyUser");
    private static final List<String> PASSWORD_PROPERTIES = Lists.newArrayList(
            "http.proxyPassword", "https.proxyPassword", "socksProxyPassword");
    private static final Function<String, String> SYSTEM_PROPERTY_FUNCTION = new Function<String, String>() {
        @Nullable
        @Override
        public String apply(String input) {
            return System.getProperty(input);
        }
    };

    @Override
    public Optional<ProxyConfiguration> getProxyConfiguration() {
        Set<String> host = getProperty(HOST_PROPERTIES);
        Set<String> port = getProperty(PORT_PROPERTIES);
        if (host.isEmpty() || port.isEmpty()) {
            return Optional.absent();
        } else if (host.size() > 1) {
            throw new IllegalStateException(String.format("Ambiguous proxy host %s from %s", host, HOST_PROPERTIES));
        } else if (port.size() > 1) {
            throw new IllegalStateException(String.format("Ambiguous proxy port %s from %s", port, PORT_PROPERTIES));
        }

        ProxyConfiguration.Builder builder = ProxyConfiguration.builder()
                .host(host.iterator().next())
                .port(Integer.parseInt(port.iterator().next()));

        Set<String> username = getProperty(USERNAME_PROPERTIES);
        Set<String> password = getProperty(PASSWORD_PROPERTIES);
        if (username.size() > 1) {
            throw new IllegalStateException(String.format("Ambiguous proxy username %s from %s", username,
                    USERNAME_PROPERTIES));
        } else if (password.size() > 1) {
            throw new IllegalStateException(String.format("Ambiguous proxy password from %s", PASSWORD_PROPERTIES));
        } else if (username.size() == 1 && password.size() == 1) {
            builder.credentials(BasicCredentials.of(username.iterator().next(), password.iterator().next()));
        }

        return Optional.<ProxyConfiguration>of(builder.build());
    }

    private static Set<String> getProperty(List<String> properties) {
        return FluentIterable.from(Lists.transform(properties, SYSTEM_PROPERTY_FUNCTION))
                .filter(Predicates.notNull())
                .toSet();
    }
}
