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

public final class QueryMapUsingServerWithoutContextTests {

    /**
     * Creates a {@link QueryMapTestServer.WithoutContext} Dropwizard server.
     * The Java implementation of this server uses strongly typed methods that
     * will only process the query parameters that are declared as part of the
     * method parameters.
     */
    @ClassRule
    public static final DropwizardAppRule<Configuration> APP = new DropwizardAppRule<>(
            QueryMapTestServer.WithoutContext.class,
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
        QueryMap queryMap = QueryMap.of(ImmutableMultimap.of(
                TestService.MAP_PARAM_NAME, "namedVal 1",
                TestService.MAP_PARAM_NAME, "namedVal 2",
                "fooParam", "fooVal"));

        // client GET call contains all of the query parameters in queryMap,
        // but Java server implementation is such that only first value of
        // TestService.MAP_PARAM_NAME query parameter is processed
        assertThat(
                service.getUsingParamMap(queryMap),
                is(ImmutableMultimap.of(TestService.MAP_PARAM_NAME, "namedVal 1")));
    }

    @Test
    public void testGetUsingParamMapWithString() {
        assertThat(service.getUsingParamMap("fooVal"), is(ImmutableMultimap.of(TestService.MAP_PARAM_NAME, "fooVal")));
    }

    @Test
    public void testGetUsingParamListWithQueryMap() {
        QueryMap queryMap = QueryMap.of(ImmutableMultimap.of(
                TestService.LIST_PARAM_NAME, "list val 1",
                TestService.LIST_PARAM_NAME, "list val 2",
                "fooParam", "foo value",
                TestService.MAP_PARAM_NAME, "bar value"));

        // client GET call contains all of the query parameters in queryMap,
        // but Java server implementation is such that only values of
        // TestService.LIST_PARAM_NAME query parameter are processed
        assertThat(
                service.getUsingParamList(queryMap),
                is(ImmutableMultimap.of(
                        TestService.LIST_PARAM_NAME, "list val 1",
                        TestService.LIST_PARAM_NAME, "list val 2")));
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
