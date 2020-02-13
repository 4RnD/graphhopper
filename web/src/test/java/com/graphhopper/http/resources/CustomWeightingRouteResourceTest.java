package com.graphhopper.http.resources;

import com.fasterxml.jackson.databind.JsonNode;
import com.graphhopper.http.GraphHopperApplication;
import com.graphhopper.http.GraphHopperServerConfiguration;
import com.graphhopper.util.Helper;
import io.dropwizard.testing.junit.DropwizardAppRule;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.File;
import java.io.IOException;

import static com.graphhopper.http.resources.CustomWeightingRouteResourceLMTest.assertBetween;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class CustomWeightingRouteResourceTest {

    private static final String DIR = "./target/north-bayreuth-gh/";
    private static final GraphHopperServerConfiguration config = new GraphHopperServerConfiguration();

    static {
        config.getGraphHopperConfiguration().
                put("graph.flag_encoders", "bike,car").
                put("prepare.ch.weightings", "custom_truck").
                put("routing.ch.disabling_allowed", "true").
                put("graph.custom_profiles.directory", "./src/test/resources/com/graphhopper/http/resources/").
                put("prepare.min_network_size", "0").
                put("prepare.min_one_way_network_size", "0").
                // we need more than the default encoded values (truck.yml and cargo_bike.yml)
                put("graph.encoded_values", "max_height,max_weight,max_width,hazmat,toll,surface,track_type").
                put("datareader.file", "../core/files/north-bayreuth.osm.gz").
                put("graph.location", DIR);
    }

    @ClassRule
    public static final DropwizardAppRule<GraphHopperServerConfiguration> app = new DropwizardAppRule<>(GraphHopperApplication.class, config);

    @BeforeClass
    @AfterClass
    public static void cleanUp() {
        Helper.removeDir(new File(DIR));
    }

    @Test
    public void testCHTruckQuery() {
        String jsonQuery = "{" +
                " \"points\": [[11.58199, 50.0141], [11.5865, 50.0095]]," +
                " \"weighting\": \"custom_truck\"" +
                "}";
        // TODO NOW is it logical to use /route for this instead /custom?
        final Response response = app.client().target("http://localhost:8080/route").request().post(Entity.json(jsonQuery));
        assertEquals(200, response.getStatus());
        JsonNode json = response.readEntity(JsonNode.class);
        JsonNode infoJson = json.get("info");
        assertFalse(infoJson.has("errors"));
        JsonNode path = json.get("paths").get(0);
        assertBetween("distance wasn't correct", path.get("distance").asDouble(), 1400, 1600);
        assertBetween("time wasn't correct", path.get("time").asLong() / 1000.0, 120, 180);
    }

    @Test
    public void testCargoBike() throws IOException {
        String yamlQuery = "points: [[11.58199, 50.0141], [11.5865, 50.0095]]\n" +
                "base: bike\n";
        JsonNode yamlNode = app.client().target("http://localhost:8080/custom").request().post(Entity.entity(yamlQuery,
                new MediaType("application", "yaml"))).readEntity(JsonNode.class);
        JsonNode path = yamlNode.get("paths").get(0);
        assertBetween("distance wasn't correct", path.get("distance").asDouble(), 600, 700);

        // since CustomModel is in the root level of the request we can directly use the yml file:
        String queryYamlFromFile = Helper.isToString(getClass().getResourceAsStream("cargo_bike.yml"));
        yamlQuery = "points: [[11.58199, 50.0141], [11.5865, 50.0095]]\n" + queryYamlFromFile;
        yamlNode = app.client().target("http://localhost:8080/custom").request().post(Entity.entity(yamlQuery,
                new MediaType("application", "yaml"))).readEntity(JsonNode.class);
        path = yamlNode.get("paths").get(0);
        assertBetween("distance wasn't correct", path.get("distance").asDouble(), 1000, 2000);
    }
}