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

import com.graphhopper.graphsupport.GraphSupport;
import com.graphhopper.routing.profiles.IntEncodedValue;
import com.graphhopper.routing.profiles.UnsignedIntEncodedValue;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.storage.TurnCostStorage;
import com.graphhopper.tardur.TimeDependentRestrictionsDAO;
import com.graphhopper.tardur.view.ConditionalRestrictionView;
import com.graphhopper.tardur.view.TimeDependentRestrictionsView;
import com.graphhopper.timezone.core.TimeZones;
import org.locationtech.jts.geom.Coordinate;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Path("restrictions")
public class ConditionalTurnRestrictionsResource {

    private final TimeDependentRestrictionsDAO timeDependentRestrictionsDAO;
    private final TimeZones timeZones;
    private final GraphHopperStorage storage;

    @Inject
    public ConditionalTurnRestrictionsResource(GraphHopperStorage storage, TimeZones timeZones) {
        this.storage = storage;
        this.timeZones = timeZones;
        timeDependentRestrictionsDAO = new TimeDependentRestrictionsDAO(storage, timeZones);
    }

    @GET
    @Produces("text/html")
    public TimeDependentRestrictionsView timeDependentTurnRestrictions() {
        return new TimeDependentRestrictionsView(timeDependentRestrictionsDAO, () -> {
            IntEncodedValue tagPointerEnc = storage.getEncodingManager().getIntEncodedValue("turnrestrictiontagpointer");
            Stream<ConditionalRestrictionView> conditionalRestrictionViewStream = GraphSupport.allTurnRelations(storage.getTurnCostStorage())
                    .flatMap(turnRelation -> {
                        int tagPointer = turnRelation.get(tagPointerEnc);
                        if (tagPointer != 0) {
                            Map<String, String> tags = storage.getTagStore().getAll(tagPointer);
                            List<TimeDependentRestrictionsDAO.ConditionalTagData> restrictionData = TimeDependentRestrictionsDAO.getConditionalTagDataWithTimeDependentConditions(tags).stream().filter(c -> !c.restrictionData.isEmpty())
                                    .collect(Collectors.toList());
                            if (!restrictionData.isEmpty()) {
                                ConditionalRestrictionView view = new ConditionalRestrictionView(timeDependentRestrictionsDAO, timeZones);
                                view.tags = tags;
                                int fromNode = storage.getEdgeIteratorState(turnRelation.getFromEdge(), turnRelation.getViaNode()).getBaseNode();
                                int toNode = storage.getEdgeIteratorState(turnRelation.getToEdge(), turnRelation.getViaNode()).getBaseNode();
                                view.from = new Coordinate(storage.getNodeAccess().getLon(fromNode), storage.getNodeAccess().getLat(fromNode));
                                view.to = new Coordinate(storage.getNodeAccess().getLon(toNode), storage.getNodeAccess().getLat(toNode));
                                view.restrictionData = restrictionData;
                                return Stream.of(view);
                            }
                        }
                        return Stream.empty();
                    });
            return conditionalRestrictionViewStream.iterator();
        });
    }

}
