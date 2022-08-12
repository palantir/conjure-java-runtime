package com.palantir.conjure.verification.client;

import com.palantir.conjure.java.lib.internal.ClientEndpoint;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import javax.annotation.processing.Generated;

@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Path("/")
@Generated("com.palantir.conjure.java.services.JerseyServiceGenerator")
public interface VerificationClientService {
    @POST
    @Path("runTestCase")
    @ClientEndpoint(method = "POST", path = "/runTestCase")
    void runTestCase(VerificationClientRequest body);
}
