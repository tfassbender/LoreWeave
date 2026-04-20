package com.tfassbender.loreweave.rest;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.Map;

@Path("/ping")
public class PingResource {

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, String> ping() {
        return Map.of("status", "ok");
    }
}
