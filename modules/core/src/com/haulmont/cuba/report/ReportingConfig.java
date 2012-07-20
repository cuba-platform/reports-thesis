/*
 * Copyright (c) 2012 Haulmont Technology Ltd. All Rights Reserved.
 * Haulmont Technology proprietary and confidential.
 * Use is subject to license terms.
 */

package com.haulmont.cuba.report;

import com.haulmont.cuba.core.config.Config;
import com.haulmont.cuba.core.config.Property;
import com.haulmont.cuba.core.config.defaults.DefaultBoolean;
import com.haulmont.cuba.core.config.defaults.DefaultInteger;
import com.haulmont.cuba.core.config.defaults.DefaultString;

/**
 * @author krivopustov
 * @version $Id$
 */
public interface ReportingConfig extends Config {

    /**
     * @return Path to the installed OpenOffice
     */
    @Property("cuba.reporting.openoffice.path")
    String getOpenOfficePath();

    /**
     * @return The list of ports to start OpenOffice on.
     */
    @Property("cuba.reporting.openoffice.ports")
    @DefaultString("8100|8101|8102|8103")
    String getOpenOfficePorts();

    /**
     * @return Request to OpenOffice timeout in seconds.
     */
    @Property("cuba.reporting.openoffice.docFormatterTimeout")
    @DefaultInteger(20)
    Integer getDocFormatterTimeout();

    /**
     * @return Has to be true if using OpenOffice reporting formatter on a *nix server without X server running
     */
    @Property("cuba.reporting.displayDeviceUnavailable")
    @DefaultBoolean(false)
    boolean getDisplayDeviceUnavailable();
}
