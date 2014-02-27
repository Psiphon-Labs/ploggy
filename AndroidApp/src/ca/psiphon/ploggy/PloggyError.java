/*
 * Copyright (c) 2014, Psiphon Inc.
 * All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package ca.psiphon.ploggy;

/**
 * The standard exception used for exception wrapping.
 *
 * Low-level components components -- Data, Engine, etc. -- throw this in recoverable error situations.
 * As a result, higher level components mainly have to handle only this exception.
 * A PloggyError will add its own log entry.
 *
 */
public class PloggyError extends Exception {
    private static final long serialVersionUID = -3656367025650685613L;

    public PloggyError(String tag, String message) {
        if (tag != null) {
            Log.addEntry(tag, message);
        }
    }

    public PloggyError(String tag, Exception e) {
        // TODO: require message param as well?
        // TODO: log stack trace?
        super(e);
        String message = e.getLocalizedMessage();
        if (message == null) {
            message = "(null)";
        }
        Log.addEntry(tag, String.format("%s: %s", e.getClass().toString(), message));
    }
}
