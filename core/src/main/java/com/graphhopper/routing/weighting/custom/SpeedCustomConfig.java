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
package com.graphhopper.routing.weighting.custom;

import com.graphhopper.routing.profiles.DecimalEncodedValue;
import com.graphhopper.routing.profiles.EncodedValueFactory;
import com.graphhopper.routing.profiles.EncodedValueLookup;
import com.graphhopper.routing.profiles.EnumEncodedValue;
import com.graphhopper.routing.util.CustomModel;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.Helper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.graphhopper.routing.weighting.custom.PriorityCustomConfig.normalizeFactor;

public class SpeedCustomConfig {
    private List<ConfigMapEntry> speedFactorList = new ArrayList<>();
    private List<ConfigMapEntry> maxSpeedList = new ArrayList<>();
    private DecimalEncodedValue avgSpeedEnc;
    private final double maxSpeed;
    private final double maxSpeedFallback;

    public SpeedCustomConfig(final double maxSpeed, CustomModel customModel, EncodedValueLookup lookup, EncodedValueFactory factory) {
        this.maxSpeed = maxSpeed;
        this.maxSpeedFallback = customModel.getMaxSpeedFallback() == null ? maxSpeed : customModel.getMaxSpeedFallback();
        if (this.maxSpeedFallback > maxSpeed)
            throw new IllegalArgumentException("max_speed_fallback cannot be bigger than max_speed " + maxSpeed);

        this.avgSpeedEnc = lookup.getDecimalEncodedValue(EncodingManager.getKey(customModel.getBase(), "average_speed"));

        // use max_speed to lower speed for the specified conditions
        for (Map.Entry<String, Object> entry : customModel.getMaxSpeed().entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (value instanceof Map) {
                if (!lookup.hasEncodedValue(key))
                    throw new IllegalArgumentException("Cannot find '" + key + "' specified in 'max_speed'");

                EnumEncodedValue enumEncodedValue = lookup.getEnumEncodedValue(key, Enum.class);
                Class<? extends Enum> enumClass = factory.findValues(key);
                double[] values = Helper.createEnumToDoubleArray("max_speed", maxSpeed, 0, maxSpeed,
                        enumClass, (Map<String, Object>) value);
                maxSpeedList.add(new EnumToValue(enumEncodedValue, values));
            } else {
                throw new IllegalArgumentException("Type " + value.getClass() + " is not supported for 'max_speed'");
            }
        }

        // use speed_factor to reduce (or increase?) the estimated speed value under the specified conditions
        for (Map.Entry<String, Object> entry : customModel.getSpeedFactor().entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (value instanceof Map) {
                if (!lookup.hasEncodedValue(key))
                    throw new IllegalArgumentException("Cannot find '" + key + "' specified in 'speed_factor'");

                EnumEncodedValue enumEncodedValue = lookup.getEnumEncodedValue(key, Enum.class);
                Class<? extends Enum> enumClass = factory.findValues(key);
                double[] values = Helper.createEnumToDoubleArray("speed_factor", 1, 0, 100,
                        enumClass, (Map<String, Object>) value);
                normalizeFactor(values);
                speedFactorList.add(new EnumToValue(enumEncodedValue, values));
            } else {
                throw new IllegalArgumentException("Type " + value.getClass() + " is not supported for 'speed_factor'");
            }
        }
    }

    public double getMaxSpeed() {
        return maxSpeed;
    }

    /**
     * @return speed in km/h
     */
    public double calcSpeed(EdgeIteratorState edge, boolean reverse) {
        double speed = reverse ? edge.getReverse(avgSpeedEnc) : edge.get(avgSpeedEnc);
        if (Double.isInfinite(speed) || Double.isNaN(speed) || speed < 0)
            throw new IllegalStateException("Invalid estimated speed " + speed);

        boolean applied = false;
        for (int i = 0; i < maxSpeedList.size(); i++) {
            ConfigMapEntry entry = maxSpeedList.get(i);
            double maxValue = entry.getValue(edge, reverse);
            if (maxValue < speed) {
                applied = true;
                speed = maxValue;
            }
        }
        if (!applied && maxSpeedFallback < speed)
            speed = maxSpeedFallback;

        for (int i = 0; i < speedFactorList.size(); i++) {
            ConfigMapEntry entry = speedFactorList.get(i);
            double factorValue = entry.getValue(edge, reverse);
            if (factorValue < 1)
                speed *= factorValue;
        }

        return Math.min(speed, maxSpeed);
    }
}
