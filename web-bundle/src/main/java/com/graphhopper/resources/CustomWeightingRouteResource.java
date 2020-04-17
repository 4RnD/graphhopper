/*
 *  Licensed to GraphHopper GmbH under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  GraphHopper GmbH licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except in
 *  compliance with the License. You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.graphhopper.resources;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.graphhopper.GHResponse;
import com.graphhopper.GraphHopper;
import com.graphhopper.MultiException;
import com.graphhopper.config.ProfileConfig;
import com.graphhopper.http.WebHelper;
import com.graphhopper.jackson.CustomRequest;
import com.graphhopper.jackson.Jackson;
import com.graphhopper.routing.util.CustomModel;
import com.graphhopper.routing.weighting.custom.CustomProfileConfig;
import com.graphhopper.util.*;
import com.graphhopper.util.gpx.GpxFromInstructions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import static com.graphhopper.util.Parameters.Routing.*;

/**
 * Resource to use GraphHopper in a remote client application like mobile or browser. Note: If type
 * is json it returns the points in GeoJson array format [longitude,latitude] unlike the format "lat,lon"
 * used for the request. See the full API response format in docs/web/api-doc.md
 *
 * @author Peter Karich
 */
@Path("custom")
public class CustomWeightingRouteResource {

    private static final Logger logger = LoggerFactory.getLogger(CustomWeightingRouteResource.class);

    private final GraphHopper graphHopper;
    private final ObjectMapper yamlOM;

    @Inject
    public CustomWeightingRouteResource(GraphHopper graphHopper) {
        this.graphHopper = graphHopper;
        this.yamlOM = Jackson.initObjectMapper(new ObjectMapper(new YAMLFactory()));
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, "application/gpx+xml"})
    public Response doPost(CustomRequest request, @Context HttpServletRequest httpReq) {
        if (request == null)
            throw new IllegalArgumentException("Empty request");

        StopWatch sw = new StopWatch().start();
        GHResponse ghResponse = new GHResponse();
        CustomModel model = request.getModel();
        if (model == null)
            throw new IllegalArgumentException("No custom model properties found");
        if (request.getHints().has(BLOCK_AREA))
            throw new IllegalArgumentException("Instead of block_area define the geometry under 'areas' as GeoJSON and use 'area_<id>: 0' in e.g. priority");
        if (Helper.isEmpty(request.getProfile()))
            throw new IllegalArgumentException("The 'profile' parameter for CustomRequest is required");

        ProfileConfig profile = null;
        for (ProfileConfig profileConfig : graphHopper.getProfiles()) {
            if (profileConfig.getName().equals(request.getProfile())) {
                profile = profileConfig;
                break;
            }
        }
        if (!(profile instanceof CustomProfileConfig))
            throw new IllegalArgumentException("profile '" + request.getProfile() + "' cannot be used for a custom request");

        request.putHint(Parameters.CH.DISABLE, true);
        request.putHint(CustomModel.KEY, model);
        graphHopper.calcPaths(request, ghResponse);

        boolean instructions = request.getHints().getBool(INSTRUCTIONS, true);
        boolean writeGPX = "gpx".equalsIgnoreCase(request.getHints().getString("type", "json"));
        instructions = writeGPX || instructions;
        boolean enableElevation = request.getHints().getBool("elevation", false);
        boolean calcPoints = request.getHints().getBool(CALC_POINTS, true);
        boolean pointsEncoded = request.getHints().getBool("points_encoded", true);

        // default to false for the route part in next API version, see #437
        boolean withRoute = request.getHints().getBool("gpx.route", true);
        boolean withTrack = request.getHints().getBool("gpx.track", true);
        boolean withWayPoints = request.getHints().getBool("gpx.waypoints", false);
        String trackName = request.getHints().getString("gpx.trackname", "GraphHopper Track");
        String timeString = request.getHints().getString("gpx.millis", "");
        String weightingVehicleLogStr = "weighting: " + request.getHints().getString("weighting", "")
                + ", vehicle: " + request.getHints().getString("vehicle", "");
        long took = sw.stop().getNanos() / 1000;
        String infoStr = httpReq.getRemoteAddr() + " " + httpReq.getLocale() + " " + httpReq.getHeader("User-Agent");
        String queryString = httpReq.getQueryString() == null ? "" : (httpReq.getQueryString() + " ");
        String logStr = queryString + infoStr + " " + request.getPoints().size() + ", took: "
                + took + " micros, algo: " + request.getAlgorithm() + ", profile: " + request.getProfile()
                + ", " + weightingVehicleLogStr;

        if (ghResponse.hasErrors()) {
            logger.error(logStr + ", errors:" + ghResponse.getErrors());
            throw new MultiException(ghResponse.getErrors());
        } else {
            logger.info(logStr + ", alternatives: " + ghResponse.getAll().size()
                    + ", distance0: " + ghResponse.getBest().getDistance()
                    + ", weight0: " + ghResponse.getBest().getRouteWeight()
                    + ", time0: " + Math.round(ghResponse.getBest().getTime() / 60000f) + "min"
                    + ", points0: " + ghResponse.getBest().getPoints().getSize()
                    + ", debugInfo: " + ghResponse.getDebugInfo());
            return writeGPX ?
                    gpxSuccessResponseBuilder(ghResponse, timeString, trackName, enableElevation, withRoute, withTrack, withWayPoints, Constants.VERSION).
                            header("X-GH-Took", "" + Math.round(took * 1000)).
                            build()
                    :
                    Response.ok(WebHelper.jsonObject(ghResponse, instructions, calcPoints, enableElevation, pointsEncoded, took)).
                            header("X-GH-Took", "" + Math.round(took * 1000)).
                            build();
        }
    }

    @POST
    @Consumes({"text/x-yaml", "text/yaml", "application/x-yaml", "application/yaml"})
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, "application/gpx+xml"})
    public Response doPost(String yaml, @Context HttpServletRequest httpReq) {
        CustomRequest customRequest;
        try {
            customRequest = yamlOM.readValue(yaml, CustomRequest.class);
        } catch (Exception ex) {
            // TODO should we really provide this much details to API users?
            throw new IllegalArgumentException("Incorrect YAML: " + ex.getMessage(), ex);
        }
        return doPost(customRequest, httpReq);
    }

    private static Response.ResponseBuilder gpxSuccessResponseBuilder(GHResponse ghRsp, String timeString, String
            trackName, boolean enableElevation, boolean withRoute, boolean withTrack, boolean withWayPoints, String version) {
        if (ghRsp.getAll().size() > 1) {
            throw new IllegalArgumentException("Alternatives are currently not yet supported for GPX");
        }

        long time = timeString != null ? Long.parseLong(timeString) : System.currentTimeMillis();
        InstructionList instructions = ghRsp.getBest().getInstructions();
        return Response.ok(GpxFromInstructions.createGPX(instructions, trackName, time, enableElevation, withRoute, withTrack, withWayPoints, version, instructions.getTr()), "application/gpx+xml").
                header("Content-Disposition", "attachment;filename=" + "GraphHopper.gpx");
    }
}
