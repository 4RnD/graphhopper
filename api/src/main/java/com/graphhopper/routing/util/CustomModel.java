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
package com.graphhopper.routing.util;

import com.graphhopper.config.ProfileConfig;
import com.graphhopper.json.geo.JsonFeature;

import java.util.HashMap;
import java.util.Map;

public class CustomModel {

    // required!
    private String profile;
    // optional:
    private Double maxSpeedFallback, vehicleWeight, vehicleWidth, vehicleHeight, vehicleLength;
    // default value derived from the cost for time e.g. 25€/hour and for distance 0.5€/km, for trucks this is usually larger
    private double distanceInfluence = 70;
    private Map<String, Object> speedFactor = new HashMap<>();
    private Map<String, Object> maxSpeed = new HashMap<>();
    private Map<String, Object> priorityMap = new HashMap<>();
    private Map<String, JsonFeature> areas = new HashMap<>();

    public CustomModel() {
    }

    public CustomModel(String profile) {
        setProfile(profile);
    }

    public void setProfile(String profile) {
        this.profile = profile;
    }

    public String getProfile() {
        return profile;
    }

    public void setVehicleWeight(Double vehicleWeight) {
        this.vehicleWeight = vehicleWeight;
    }

    public Double getVehicleWeight() {
        return vehicleWeight;
    }

    public void setVehicleHeight(Double vehicleHeight) {
        this.vehicleHeight = vehicleHeight;
    }

    public Double getVehicleHeight() {
        return vehicleHeight;
    }

    public void setVehicleLength(Double vehicleLength) {
        this.vehicleLength = vehicleLength;
    }

    public Double getVehicleLength() {
        return vehicleLength;
    }

    public void setVehicleWidth(Double vehicleWidth) {
        this.vehicleWidth = vehicleWidth;
    }

    public Double getVehicleWidth() {
        return vehicleWidth;
    }

    public Map<String, Object> getSpeedFactor() {
        return speedFactor;
    }

    public Map<String, Object> getMaxSpeed() {
        return maxSpeed;
    }

    public void setMaxSpeedFallback(Double maxSpeedFallback) {
        this.maxSpeedFallback = maxSpeedFallback;
    }

    public Double getMaxSpeedFallback() {
        return maxSpeedFallback;
    }

    public Map<String, Object> getPriority() {
        return priorityMap;
    }

    public void setDistanceInfluence(double distanceFactor) {
        this.distanceInfluence = distanceFactor;
    }

    public double getDistanceInfluence() {
        return distanceInfluence;
    }

    public void setAreas(Map<String, JsonFeature> areas) {
        this.areas = areas;
    }

    public Map<String, JsonFeature> getAreas() {
        return areas;
    }

    @Override
    public String toString() {
        return "CustomModel{" +
                "profile='" + profile + '\'' +
                ", maxSpeedFallback=" + maxSpeedFallback +
                ", vehicleWeight=" + vehicleWeight +
                ", vehicleWidth=" + vehicleWidth +
                ", vehicleHeight=" + vehicleHeight +
                ", vehicleLength=" + vehicleLength +
                ", distanceInfluence=" + distanceInfluence +
                ", speedFactor=" + speedFactor +
                ", maxSpeed=" + maxSpeed +
                ", priorityMap=" + priorityMap +
                ", #areas=" + areas.size() +
                '}';
    }
}