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

package com.graphhopper.tardur.resources;

import com.conveyal.osmlib.Node;
import com.conveyal.osmlib.OSM;
import com.conveyal.osmlib.Way;
import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.profiles.BooleanEncodedValue;
import com.graphhopper.routing.util.parsers.OSMIDParser;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.tardur.TimeDependentRestrictionsDAO;
import com.graphhopper.tardur.view.ConditionalRestrictionView;
import com.graphhopper.tardur.view.TimeDependentRestrictionsView;
import com.graphhopper.timezone.core.TimeZones;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.EdgeIteratorState;
import org.locationtech.jts.geom.Coordinate;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import java.util.List;
import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

@Path("conditional-access")
public class ConditionalAccessRestrictionsResource {

    private final GraphHopperStorage storage;
    private final OSM osm;
    private final TimeZones timeZones;

    @Inject
    public ConditionalAccessRestrictionsResource(GraphHopperStorage storage, OSM osm, TimeZones timeZones) {
        this.storage = storage;
        this.osm = osm;
        this.timeZones = timeZones;
    }

    @GET
    @Produces("text/html")
    public TimeDependentRestrictionsView timeDependentAccessRestrictions() {
        TimeDependentRestrictionsDAO timeDependentRestrictionsDAO = new TimeDependentRestrictionsDAO(storage, timeZones);
        return new TimeDependentRestrictionsView(new TimeDependentRestrictionsDAO(storage, timeZones), () -> {
            final OSMIDParser osmidParser = OSMIDParser.fromEncodingManager(storage.getEncodingManager());
            final BooleanEncodedValue property = storage.getEncodingManager().getBooleanEncodedValue("conditional");
            return allEdges()
                    .filter(edge -> edge.get(property))
                    .map(edge -> osmidParser.getOSMID(edge.getFlags()))
                    .distinct()
                    .flatMap(osmid -> {
                Way way = osm.ways.get(osmid);
                ReaderWay readerWay = timeDependentRestrictionsDAO.readerWay(osmid, way);
                List<TimeDependentRestrictionsDAO.ConditionalTagData> timeDependentAccessConditions = TimeDependentRestrictionsDAO.getTimeDependentAccessConditions(readerWay);
                if (!timeDependentAccessConditions.isEmpty()) {
                    ConditionalRestrictionView view = new ConditionalRestrictionView(timeDependentRestrictionsDAO, timeZones);
                    view.osmid = osmid;
                    view.tags = TimeDependentRestrictionsDAO.sanitize(readerWay.getTags());
                    Node from = osm.nodes.get(way.nodes[0]);
                    Node to = osm.nodes.get(way.nodes[way.nodes.length-1]);
                    view.from = new Coordinate(from.getLon(), from.getLat());
                    view.to = new Coordinate(to.getLon(), to.getLat());
                    view.restrictionData = timeDependentAccessConditions;
                    return Stream.of(view);
                } else {
                    return Stream.empty();
                }
            }).iterator();
        });
    }

    private Stream<EdgeIteratorState> allEdges() {
        return StreamSupport.stream(new Spliterators.AbstractSpliterator<EdgeIteratorState>(0, 0) {
            EdgeIterator edgeIterator = storage.getAllEdges();

            @Override
            public boolean tryAdvance(Consumer<? super EdgeIteratorState> action) {
                if (edgeIterator.next()) {
                    action.accept(edgeIterator);
                    return true;
                } else {
                    return false;
                }
            }
        }, false);
    }

}
