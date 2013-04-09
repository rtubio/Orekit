/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.orekit.propagation.events;

import java.util.Arrays;

import org.orekit.errors.OrekitException;
import org.orekit.propagation.SpacecraftState;
import org.orekit.time.AbsoluteDate;

/** Wrapper used to detect only increasing or decreasing events.
 *
 * <p>This class is heavily based on the class with the same name from the
 * Apache Commons Math library. The changes performed consist in replacing
 * raw types (double and double arrays) with space dynamics types
 * ({@link AbsoluteDate}, {@link SpacecraftState}).</p>
 *
 * <p>General {@link EventHandler events} are defined implicitely
 * by a {@link EventHandler#g(double, double[]) g function} crossing
 * zero. This function needs to be continuous in the event neighborhood,
 * and its sign must remain consistent between events. This implies that
 * during an ODE integration, events triggered are alternately events
 * for which the function increases from negative to positive values,
 * and events for which the function decreases from positive to
 * negative values.
 * </p>
 *
 * <p>Sometimes, users are only interested in one type of event (say
 * increasing events for example) and not in the other type. In these
 * cases, looking precisely for all events location and triggering
 * events that will later be ignored is a waste of computing time.</p>
 *
 * <p>Users can wrap a regular {@link EventDetector event detector} in
 * an instance of this class and provide this wrapping instance to
 * a {@link org.orekit.propagation.Propagator}
 * in order to avoid wasting time looking for uninteresting events.
 * The wrapper will intercept the calls to the {@link
 * EventDetector#g(SpacecraftState) g function} and to the {@link
 * EventDetector#eventOccurred(SpacecraftState, boolean)
 * eventOccurred} method in order to ignore uninteresting events. The
 * wrapped regular {@link EventDetector event detector} will the see only
 * the interesting events, i.e. either only {@code increasing} events or
 * {@code decreasing} events. the number of calls to the {@link
 * EventDetector#g(SpacecraftState) g function} will also be reduced.</p>
 *
 * @version $Id$
 * @since 3.2
 */

public class EventFilter implements EventDetector {

    /** Serializable UID. */
    private static final long serialVersionUID = 20130409L;

    /** Number of past transformers updates stored. */
    private static final int HISTORY_SIZE = 100;

    /** Wrapped event detector. */
    private final EventDetector rawDetector;

    /** Filter to use. */
    private final FilterType filter;

    /** Transformers of the g function. */
    private final Transformer[] transformers;

    /** Update time of the transformers. */
    private final AbsoluteDate[] updates;

    /** Indicator for forward integration. */
    private boolean forward;

    /** Extreme time encountered so far. */
    private AbsoluteDate extremeT;

    /** Wrap an {@link EventDetector event detector}.
     * @param rawDetector event detector to wrap
     * @param filter filter to use
     */
    public EventFilter(final EventDetector rawDetector, final FilterType filter) {
        this.rawDetector  = rawDetector;
        this.filter       = filter;
        this.transformers = new Transformer[HISTORY_SIZE];
        this.updates      = new AbsoluteDate[HISTORY_SIZE];
    }

    /**  {@inheritDoc} */
    public void init(final SpacecraftState s0, final AbsoluteDate t) {

        // delegate to raw handler
        rawDetector.init(s0, t);

        // initialize events triggering logic
        forward  = t.compareTo(s0.getDate()) >= 0;
        extremeT = forward ? AbsoluteDate.PAST_INFINITY : AbsoluteDate.FUTURE_INFINITY;
        Arrays.fill(transformers, Transformer.UNINITIALIZED);
        Arrays.fill(updates, extremeT);

    }

    /**  {@inheritDoc} */
    public double g(final SpacecraftState s) throws OrekitException {

        final double rawG = rawDetector.g(s);

        // search which transformer should be applied to g
        if (forward) {
            final int last = transformers.length - 1;
            if (extremeT.compareTo(s.getDate()) < 0) {
                // we are at the forward end of the history

                // check if a new rough root has been crossed
                final Transformer previous = transformers[last];
                final Transformer next     = filter.selectTransformer(previous, rawG, forward);
                if (next != previous) {
                    // there is a root somewhere between extremeT end t
                    // the new transformer, which is valid on both sides of the root,
                    // so it is valid for t (this is how we have just computed it above),
                    // but it was already valid before, so we store the switch at extremeT
                    // for safety, to ensure the previous transformer is not applied too
                    // close of the root
                    System.arraycopy(updates,      1, updates,      0, last);
                    System.arraycopy(transformers, 1, transformers, 0, last);
                    updates[last]      = extremeT;
                    transformers[last] = next;
                }

                extremeT = s.getDate();

                // apply the transform
                return next.transformed(rawG);

            } else {
                // we are in the middle of the history

                // select the transformer
                for (int i = last; i > 0; --i) {
                    if (updates[i].compareTo(s.getDate()) <= 0) {
                        // apply the transform
                        return transformers[i].transformed(rawG);
                    }
                }

                return transformers[0].transformed(rawG);

            }
        } else {
            if (s.getDate().compareTo(extremeT) < 0) {
                // we are at the backward end of the history

                // check if a new rough root has been crossed
                final Transformer previous = transformers[0];
                final Transformer next     = filter.selectTransformer(previous, rawG, forward);
                if (next != previous) {
                    // there is a root somewhere between extremeT end t
                    // the new transformer, which is valid on both sides of the root,
                    // so it is valid for t (this is how we have just computed it above),
                    // but it was already valid before, so we store the switch at extremeT
                    // for safety, to ensure the previous transformer is not applied too
                    // close of the root
                    System.arraycopy(updates,      0, updates,      1, updates.length - 1);
                    System.arraycopy(transformers, 0, transformers, 1, transformers.length - 1);
                    updates[0]      = extremeT;
                    transformers[0] = next;
                }

                extremeT = s.getDate();

                // apply the transform
                return next.transformed(rawG);

            } else {
                // we are in the middle of the history

                // select the transformer
                for (int i = 0; i < updates.length - 1; ++i) {
                    if (s.getDate().compareTo(updates[i]) <= 0) {
                        // apply the transform
                        return transformers[i].transformed(rawG);
                    }
                }

                return transformers[updates.length - 1].transformed(rawG);

            }
        }

    }

    /**  {@inheritDoc} */
    public Action eventOccurred(final SpacecraftState s, final boolean increasing)
        throws OrekitException {
        // delegate to raw handler, fixing increasing status on the fly
        return rawDetector.eventOccurred(s, filter.getTriggeredIncreasing());
    }

    /**  {@inheritDoc} */
    public SpacecraftState resetState(final SpacecraftState s)
        throws OrekitException {
        // delegate to raw handler
        return rawDetector.resetState(s);
    }

    /**  {@inheritDoc} */
    public double getThreshold() {
        return rawDetector.getThreshold();
    }

    /**  {@inheritDoc} */
    public double getMaxCheckInterval() {
        return rawDetector.getMaxCheckInterval();
    }

    /**  {@inheritDoc} */
    public int getMaxIterationCount() {
        return rawDetector.getMaxIterationCount();
    }

}