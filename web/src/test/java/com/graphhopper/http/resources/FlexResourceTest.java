package com.graphhopper.http.resources;

import com.fasterxml.jackson.databind.JsonNode;
import com.graphhopper.http.GraphHopperApplication;
import com.graphhopper.http.GraphHopperServerConfiguration;
import com.graphhopper.util.CmdArgs;
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

import static org.junit.Assert.*;

public class FlexResourceTest {

    private static final String DIR = "./target/andorra-gh/";
    private static final GraphHopperServerConfiguration config = new GraphHopperServerConfiguration();

    static {
        config.getGraphHopperConfiguration().merge(new CmdArgs().
                put("graph.flag_encoders", "car,foot").
                put("prepare.ch.weightings", "no").
                put("prepare.min_network_size", "0").
                put("prepare.min_one_way_network_size", "0").
                put("graph.encoded_values", "surface").
                put("datareader.file", "../core/files/andorra.osm.pbf").
                put("graph.location", DIR));
    }

    @ClassRule
    public static final DropwizardAppRule<GraphHopperServerConfiguration> app = new DropwizardAppRule<>(GraphHopperApplication.class, config);

    @BeforeClass
    @AfterClass
    public static void cleanUp() {
        Helper.removeDir(new File(DIR));
    }

    @Test
    public void testBasicQuery() {
        String jsonQuery = "{" +
                " \"points\": [[1.518946,42.531453],[1.54006,42.511178]]," +
                " \"model\": { \"base\": \"car\" }" +
                "}";
        final Response response = app.client().target("http://localhost:8080/route").request().post(Entity.json(jsonQuery));
        assertEquals(200, response.getStatus());
        JsonNode json = response.readEntity(JsonNode.class);
        JsonNode infoJson = json.get("info");
        assertFalse(infoJson.has("errors"));
        JsonNode path = json.get("paths").get(0);
        assertBetween("distance wasn't correct", path.get("distance").asDouble(), 3100, 3300);
        assertBetween("time wasn't correct", path.get("time").asLong() / 1000.0, 170, 200);
    }

    @Test
    public void testYamlQuery() {
        String yamlQuery = "points: [[1.518946,42.531453], [1.54006,42.511178]]\n" +
                "model:\n" +
                "  base: car\n";
        JsonNode yamlNode = queryYaml(yamlQuery, 200).readEntity(JsonNode.class);
        JsonNode infoElement = yamlNode.get("info");
        assertFalse(infoElement.has("errors"));
        JsonNode path = yamlNode.get("paths").get(0);
        assertBetween("distance wasn't correct", path.get("distance").asDouble(), 3100, 3300);
    }

    @Test
    public void testCustomWeight() {
        String yamlQuery = "points: [[1.529106,42.506567], [1.54006,42.511178]]\n" +
                "model:\n" +
                "  base: car\n" +
                "  priority:\n" +
                "    road_class:\n" +
                "      secondary: 2\n";
        JsonNode yamlNode = queryYaml(yamlQuery, 200).readEntity(JsonNode.class);
        JsonNode path = yamlNode.get("paths").get(0);
        assertBetween("distance wasn't correct", path.get("distance").asDouble(), 1300, 1400);

        // now prefer primary roads via special yaml-map notation
        yamlQuery = "points: [[1.5274,42.506211], [1.54006,42.511178]]\n" +
                "model:\n" +
                "  base: car\n" +
                "  priority:\n" +
                "    road_class: { residential: 1.2, primary: 1.5 }";
        yamlNode = queryYaml(yamlQuery, 200).readEntity(JsonNode.class);
        path = yamlNode.get("paths").get(0);
        assertBetween("distance wasn't correct", path.get("distance").asDouble(), 1650, 1750);
    }

    @Test
    public void testAvoidTunnels() {
        String yamlQuery = "points: [[1.533365, 42.506211], [1.523924, 42.520605]]\n" +
                "model:\n" +
                "  base: car\n" +
                "  priority:\n" +
                "    road_environment:\n" +
                "      tunnel: 0.1\n";
        JsonNode yamlNode = queryYaml(yamlQuery, 200).readEntity(JsonNode.class);
        JsonNode path = yamlNode.get("paths").get(0);
        assertBetween("distance wasn't correct", path.get("distance").asDouble(), 3000, 3100);
    }

    @Test
    public void testSimplisticWheelchair() {
        String yamlQuery = "points: [[1.540875,42.510672], [1.54212,42.511131]]\n" +
                "model:\n" +
                "  base: foot\n" +
                "  min_priority: 0\n"+
                "  priority:\n" +
                "    road_class:\n" +
                "      steps: 0\n";
        JsonNode yamlNode = queryYaml(yamlQuery, 200).readEntity(JsonNode.class);
        JsonNode path = yamlNode.get("paths").get(0);
        assertBetween("distance wasn't correct", path.get("distance").asDouble(), 500, 600);
    }

    static void assertBetween(String msg, double val, double from, double to) {
        assertTrue(msg + " :" + val, val > from);
        assertTrue(msg + " :" + val, val < to);
    }

    Response queryYaml(String yamlStr, int code) {
        Response response = app.client().target("http://localhost:8080/route").request().post(Entity.entity(yamlStr,
                new MediaType("application", "yaml")));
        assertEquals(code, response.getStatus());
        return response;
    }
}