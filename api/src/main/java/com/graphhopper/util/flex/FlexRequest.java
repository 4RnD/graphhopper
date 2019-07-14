package com.graphhopper.util.flex;

import com.graphhopper.GHRequest;
import com.graphhopper.util.shapes.GHPoint;

import java.util.List;

public class FlexRequest {
    private FlexModel model;
    private TmpRequest request;

    public static class TmpRequest extends GHRequest {
        public void setPoints(List<GHPoint> list) {
            for (GHPoint p : list)
                addPoint(p);
        }
    }

    public void setModel(FlexModel model) {
        this.model = model;
    }

    public FlexModel getModel() {
        return model;
    }

    public void setRequest(TmpRequest request) {
        this.request = request;
    }

    public GHRequest getRequest() {
        return request;
    }
}
