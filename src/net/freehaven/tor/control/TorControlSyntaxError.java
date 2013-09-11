// Copyright 2005 Nick Mathewson, Roger Dingledine
// See LICENSE file for copying information
package net.freehaven.tor.control;

/**
 * An exception raised when Tor behaves in an unexpected way.
 */
public class TorControlSyntaxError extends RuntimeException {
    static final long serialVersionUID = 2;

    public TorControlSyntaxError(String s) { super(s); }
}

