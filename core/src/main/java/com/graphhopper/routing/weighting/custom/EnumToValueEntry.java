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

import com.graphhopper.routing.profiles.EnumEncodedValue;
import com.graphhopper.routing.profiles.IntEncodedValue;
import com.graphhopper.util.EdgeIteratorState;

import java.util.Arrays;

final class EnumToValueEntry implements EdgeToValueEntry {
    private final IntEncodedValue eev;
    private final double[] values;

    EnumToValueEntry(EnumEncodedValue eev, double[] values) {
        this.eev = eev;
        this.values = values;
    }

    @Override
    public double getValue(EdgeIteratorState iter, boolean reverse) {
        int enumOrdinal = iter.get(eev);
        return values[enumOrdinal];
    }

    @Override
    public String toString() {
        return eev.getName() + ": " + Arrays.toString(values);
    }
}
