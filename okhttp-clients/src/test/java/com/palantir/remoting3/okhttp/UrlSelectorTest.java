/*
 * Copyright 2017 Palantir Technologies, Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.palantir.remoting3.okhttp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.Optional;
import okhttp3.HttpUrl;
import okhttp3.Request;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public final class UrlSelectorTest extends TestBase {

    @Test
    public void baseUrlsMustBeCanonical() throws Exception {
        for (String url : new String[] {
                "user:pass@foo.com/path",
                ""
        }) {
            assertThatThrownBy(() -> UrlSelectorImpl.create(list(url)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Not a valid URL: %s", url);
        }

        for (String url : new String[] {
                "http://user:pass@foo.com/path",
                "http://foo.com/path?bar",
                }) {
            assertThatThrownBy(() -> UrlSelectorImpl.create(list(url)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage(
                            "Base URLs must be 'canonical' and consist of schema, host, port, and path only: %s", url);
        }

        for (String url : new String[] {
                "http://foo.com/path",
                "http://foo.com:80/path",
                "http://foo.com:8080",
                }) {
            UrlSelectorImpl.create(list(url));
        }
    }

    @Test
    public void testRedirectTo_succeedsWhenRequestedBaseUrlPathIsPrefixOfCurrentPath() throws Exception {
        String url1 = "http://foo/a";
        String url2 = "https://bar:8080/a/b/c";
        List<String> baseUrls = list(url1, url2);
        UrlSelectorImpl selector = UrlSelectorImpl.create(baseUrls);

        // Redirect to self is OK.
        assertThat(selector.redirectTo(parse(url1), url1)).contains(parse(url1));
        assertThat(selector.redirectTo(parse(url1 + "/123?abc"), url1)).contains(parse(url1 + "/123?abc"));

        // Redirect to other is OK when current path is a prefix of the requested base URL.
        // Note: url2 -->url1 redirects across schema+host+port
        assertThat(selector.redirectTo(parse(url2), url1)).contains(parse(url1 + "/b/c"));
        assertThat(selector.redirectTo(parse(url2 + "/123?abc"), url1)).contains(parse(url1 + "/b/c/123?abc"));
    }

    @Test
    public void testRedirectTo_findsMatchesWithCaseInsensitiveHostNames() throws Exception {
        String baseUrl = "http://foo/a";
        UrlSelectorImpl selector = UrlSelectorImpl.create(list(baseUrl));

        assertThat(selector.redirectTo(parse(baseUrl), "http://FOO/a")).contains(parse(baseUrl));
    }

    @Test
    public void testRedirectTo_doesNotFindMatchesForCaseSentitivePaths() throws Exception {
        String baseUrl = "http://foo/a";
        UrlSelectorImpl selector = UrlSelectorImpl.create(list(baseUrl));

        assertThat(selector.redirectTo(parse(baseUrl), "http://foo/A")).isEmpty();
    }

    @Test
    public void testRedirectTo_failsWhenRequestedBaseUrlPathIsNotPrefixOfCurrentPath() throws Exception {
        String url1 = "http://foo/a";
        String url2 = "https://bar:8080/a/b/c";
        UrlSelectorImpl selector = UrlSelectorImpl.create(list(url1, url2));

        assertThat(selector.redirectTo(parse(url1), url2)).isEmpty();
    }

    @Test
    public void testIsBaseUrlFor() throws Exception {
        // Negative cases
        assertThat(UrlSelectorImpl.isBaseUrlFor(parse("http://foo/a"), parse("https://foo/a"))).isFalse();
        assertThat(UrlSelectorImpl.isBaseUrlFor(parse("http://foo/a"), parse("http://bar/a"))).isFalse();
        assertThat(UrlSelectorImpl.isBaseUrlFor(parse("http://foo/a"), parse("http://foo:8080/a"))).isFalse();
        assertThat(UrlSelectorImpl.isBaseUrlFor(parse("http://foo/b"), parse("http://foo/a"))).isFalse();

        // Positive cases: schema, host, port must be equal, path must be a prefix
        assertThat(UrlSelectorImpl.isBaseUrlFor(parse("http://foo/a"), parse("http://foo/a"))).isTrue();
        assertThat(UrlSelectorImpl.isBaseUrlFor(parse("http://foo/a"), parse("http://foo/a/"))).isTrue();
        assertThat(UrlSelectorImpl.isBaseUrlFor(parse("http://foo/a"), parse("http://foo/a/b"))).isTrue();
        assertThat(UrlSelectorImpl.isBaseUrlFor(parse("http://foo"), parse("http://foo/a"))).isTrue();
    }

    @Test
    public void testRedirectToNext() throws Exception {
        UrlSelectorImpl selector = UrlSelectorImpl.create(list("http://foo/a"));
        HttpUrl current = HttpUrl.parse("http://bar/a/b/").resolve("foo");
        assertThat(selector.redirectToNext(current)).contains(HttpUrl.parse("http://foo/a/b/foo"));
    }

    @Test
    public void testWorksWithWebSockets() throws Exception {
        Request wsRequest = new Request.Builder()
                .url("wss://foo/a")
                .build();
        UrlSelectorImpl selector = UrlSelectorImpl.create(ImmutableList.of("wss://foo/", "wss://bar/"));

        // Silently replace web socket URLs with HTTP URLs. See https://github.com/square/okhttp/issues/1652.
        assertThat(selector.redirectToNext(wsRequest.url())).isEqualTo(Optional.of(parse("https://bar/a")));
    }

    private static HttpUrl parse(String url) {
        return HttpUrl.parse(url);
    }
}
