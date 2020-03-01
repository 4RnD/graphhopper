package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.OSMTurnRestriction;
import com.graphhopper.reader.ReaderRelation;
import com.graphhopper.routing.EdgeBasedRoutingAlgorithmTest;
import com.graphhopper.routing.profiles.DecimalEncodedValue;
import com.graphhopper.routing.profiles.TurnCost;
import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.GraphBuilder;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.storage.IntsRef;
import com.graphhopper.storage.TurnCostStorage;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;

public class OSMTurnRestrictionParserTest {

    @Test
    public void testGetRestrictionAsEntries() {
        CarFlagEncoder encoder = new CarFlagEncoder(5, 5, 1);
        final Map<Long, Integer> osmNodeToInternal = new HashMap<>();
        final Map<Integer, Long> internalToOSMEdge = new HashMap<>();

        osmNodeToInternal.put(3L, 3);
        // edge ids are only stored if they occurred before in an OSMRelation
        internalToOSMEdge.put(3, 3L);
        internalToOSMEdge.put(4, 4L);

        OSMTurnRestrictionParser parser = new OSMTurnRestrictionParser(encoder.toString(), 1);
        GraphHopperStorage ghStorage = new GraphBuilder(new EncodingManager.Builder().add(encoder).addTurnRestrictionParser(parser).build()).create();
        EdgeBasedRoutingAlgorithmTest.initGraph(ghStorage);
        TurnRestrictionParser.ExternalInternalMap map = new TurnRestrictionParser.ExternalInternalMap() {

            @Override
            public int getInternalNodeIdOfOsmNode(long nodeOsmId) {
                return osmNodeToInternal.get(nodeOsmId);
            }

            @Override
            public long getOsmIdOfEdge(int edgeId) {
                Long l = internalToOSMEdge.get(edgeId);
                if (l == null)
                    return -1;
                return l;
            }
        };

        // TYPE == ONLY
        ReaderRelation readerRelation = new ReaderRelation(-1);
        readerRelation.add(new ReaderRelation.Member(ReaderRelation.Member.WAY, 4, "from"));
        readerRelation.add(new ReaderRelation.Member(ReaderRelation.Member.NODE, 3, "via"));
        readerRelation.add(new ReaderRelation.Member(ReaderRelation.Member.WAY, 3, "to"));
        readerRelation.setTag("restriction", "only_left_turn");
        OSMTurnRestriction instance = new OSMTurnRestriction(readerRelation);
        IntsRef tcFlags = TurnCost.createFlags();
        parser.addRelationToTCStorage(instance, tcFlags, map, ghStorage);

        TurnCostStorage tcs = ghStorage.getTurnCostStorage();
        DecimalEncodedValue tce = parser.getTurnCostEnc();
        assertTrue(Double.isInfinite(tcs.get(tce, tcFlags, 4, 3, 6)));
        assertEquals(0, tcs.get(tce, tcFlags, 4, 3, 3), .1);
        assertTrue(Double.isInfinite(tcs.get(tce, tcFlags, 4, 3, 2)));

        // TYPE == NOT
        readerRelation = new ReaderRelation(-2);
        readerRelation.add(new ReaderRelation.Member(ReaderRelation.Member.WAY, 4, "from"));
        readerRelation.add(new ReaderRelation.Member(ReaderRelation.Member.NODE, 3, "via"));
        readerRelation.add(new ReaderRelation.Member(ReaderRelation.Member.WAY, 3, "to"));
        readerRelation.setTag("restriction", "no_left_turn");
        instance = new OSMTurnRestriction(readerRelation);
        parser.addRelationToTCStorage(instance, tcFlags, map, ghStorage);
        assertTrue(Double.isInfinite(tcs.get(tce, tcFlags, 4, 3, 3)));
    }

    @Test
    public void unknownShouldBehaveLikeMotorVehicle() {
        OSMTurnRestrictionParser parser = new OSMTurnRestrictionParser("fatcarsomething", 1);
        ReaderRelation readerRelation = new ReaderRelation(-1);
        readerRelation.add(new ReaderRelation.Member(ReaderRelation.Member.WAY, 4, "from"));
        readerRelation.add(new ReaderRelation.Member(ReaderRelation.Member.NODE, 3, "via"));
        readerRelation.add(new ReaderRelation.Member(ReaderRelation.Member.WAY, 3, "to"));
        readerRelation.setTag("restriction:space", "no_left_turn");
        OSMTurnRestriction turnRelation = new OSMTurnRestriction(readerRelation);
        parser.handleTurnRelationTags(TurnCost.createFlags(), turnRelation, null, null);
    }
}