/*
 * Copyright 2016 Palantir Technologies, Inc. All rights reserved.
 */
package com.palantir.config.service.proxy;

import static com.google.common.base.Strings.isNullOrEmpty;
import static com.google.common.base.Throwables.propagate;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Predicates;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.Lists;
import com.palantir.config.service.BasicCredentials;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;
import javax.annotation.Nullable;

/**
 * Provides a {@link com.palantir.config.service.proxy.ProxyConfiguration} based on environment variables.
 */
public final class EnvironmentVariableProxyConfigurationProvider implements ProxyConfigurationProvider {

    private static final List<String> VARIABLES = Lists.newArrayList("http_proxy", "https_proxy", "HTTP_PROXY",
            "HTTPS_PROXY");
    private static final Function<String, String> SYSTEM_ENV_FUNCTION = new Function<String, String>() {
        @Nullable
        @Override
        public String apply(String input) {
            return System.getenv(input);
        }
    };

    @Override
    public Optional<ProxyConfiguration> getProxyConfiguration() {
        Set<String> proxies = FluentIterable.from(Lists.transform(VARIABLES, SYSTEM_ENV_FUNCTION))
                .filter(Predicates.notNull())
                .toSet();

        if (proxies.isEmpty()) {
            return Optional.absent();
        } else if (proxies.size() > 1) {
            throw new IllegalStateException(String.format("Ambiguous proxy settings %s", proxies));
        }

        URL httpUrl;
        try {
            httpUrl = new URL(proxies.iterator().next());
        } catch (MalformedURLException e) {
            throw propagate(e);
        }
        ProxyConfiguration.Builder builder = ProxyConfiguration.builder()
                .host(httpUrl.getHost())
                .port(httpUrl.getPort());

        String userInfo = httpUrl.getUserInfo();
        if (!isNullOrEmpty(userInfo)) {
            StringTokenizer tokenizer = new StringTokenizer(userInfo, ":");
            builder.credentials(BasicCredentials.of(tokenizer.nextToken(), tokenizer.nextToken()));
        }
        return Optional.<ProxyConfiguration>of(builder.build());
    }
}
