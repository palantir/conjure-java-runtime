/*
 * Copyright 2015 Palantir Technologies, Inc. All rights reserved.
 */

package feign;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMultimap;
import com.palantir.remoting.http.FeignClientFactory;
import com.palantir.remoting.http.ObjectMappers;
import com.palantir.remoting.http.QueryMap;
import com.palantir.remoting.http.errors.SerializableErrorErrorDecoder;
import feign.QueryMapTestServer.TestService;
import feign.jackson.JacksonDecoder;
import feign.jackson.JacksonEncoder;
import feign.jaxrs.JAXRSContract;
import io.dropwizard.Configuration;
import io.dropwizard.testing.junit.DropwizardAppRule;
import javax.net.ssl.SSLSocketFactory;
import jersey.repackaged.com.google.common.collect.ImmutableList;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

public final class QueryMapUsingServerWithContextTests {

    /**
     * Creates a {@link QueryMapTestServer.WithContext} Dropwizard server.
     * The Java implementation of this server uses the context to read all
     * query parameters from a map and returns them.
     */
    @ClassRule
    public static final DropwizardAppRule<Configuration> APP = new DropwizardAppRule<>(
            QueryMapTestServer.WithContext.class,
            "src/test/resources/test-server.yml");

    private QueryMapTestServer.TestClientService service;

    @Before
    public void before() {
        String endpointUri = "http://localhost:" + APP.getLocalPort();

        FeignClientFactory factory = FeignClientFactory.of(
                new JAXRSContract(),
                // register query map encoder
                new QueryMapEncoder(new JacksonEncoder(ObjectMappers.guavaJdk7())),
                new OptionalAwareDecoder(new TextDelegateDecoder(new JacksonDecoder(ObjectMappers.guavaJdk7()))),
                SerializableErrorErrorDecoder.INSTANCE,
                FeignClientFactory.okHttpClient());

        service = factory.createProxy(
                Optional.<SSLSocketFactory>absent(),
                endpointUri,
                QueryMapTestServer.TestClientService.class);
    }

    @Test
    public void testGetUsingParamMapWithQueryMap() {
        ImmutableMultimap<String, String> requestMultiMap = ImmutableMultimap.of(
                "fooParam", "fooVal",
                "fooParam", "foo value 2",
                "fooParam", "",
                "barParam", "barVal");

        QueryMap queryMap = QueryMap.of(requestMultiMap);
        assertThat(service.getUsingParamMap(queryMap), is(requestMultiMap));
    }

    @Test
    public void testGetUsingParamMapWithString() {
        assertThat(service.getUsingParamMap("fooVal"), is(ImmutableMultimap.of(TestService.MAP_PARAM_NAME, "fooVal")));
    }

    @Test
    public void testGetUsingParamListWithQueryMap() {
        ImmutableMultimap<String, String> requestMultiMap = ImmutableMultimap.of(
                "fooParam", "fooVal",
                "fooParam", "foo value 2",
                "fooParam", "",
                "barParam", "barVal");

        QueryMap queryMap = QueryMap.of(requestMultiMap);
        assertThat(service.getUsingParamList(queryMap), is(requestMultiMap));
    }

    @Test
    public void testGetUsingParamListWithList() {
        assertThat(
                service.getUsingParamList(ImmutableList.of("foo", "bar")),
                is(ImmutableMultimap.of(
                        TestService.LIST_PARAM_NAME, "foo",
                        TestService.LIST_PARAM_NAME, "bar")));
    }

}
