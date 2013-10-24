/*
 * Copyright (c) 2012 Haulmont Technology Ltd. All Rights Reserved.
 * Haulmont Technology proprietary and confidential.
 * Use is subject to license terms.

 * Author: Degtyarjov
 * Created: 27.09.13 15:46
 *
 * $Id$
 */
package com.haulmont.reports.libintegration;

import com.haulmont.cuba.core.sys.AppContext;
import com.haulmont.cuba.core.sys.SecurityContext;
import com.haulmont.yarg.exception.OpenOfficeException;
import com.haulmont.yarg.formatters.impl.doc.connector.NoFreePortsException;
import com.haulmont.yarg.formatters.impl.doc.connector.OfficeConnection;
import com.haulmont.yarg.formatters.impl.doc.connector.OfficeIntegration;
import com.haulmont.yarg.formatters.impl.doc.connector.OfficeTask;
import com.sun.star.comp.helper.BootstrapException;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class CubaOfficeIntegration extends OfficeIntegration implements CubaOfficeIntegrationMBean {
    public CubaOfficeIntegration(String openOfficePath, Integer... ports) {
        super(openOfficePath, ports);
    }

    @Override
    public void runTaskWithTimeout(final OfficeTask officeTask, int timeoutInSeconds) throws NoFreePortsException {
        final SecurityContext securityContext = AppContext.getSecurityContext();
        final OfficeConnection connection = createConnection();
        Future future = null;
        try {
            Callable<Void> task = new Callable<Void>() {
                @Override
                public Void call() throws java.lang.Exception {
                    AppContext.setSecurityContext(securityContext);
                    officeTask.processTaskInOpenOffice(connection.getOOResourceProvider());
                    connection.close();
                    return null;
                }
            };
            future = executor.submit(task);
            future.get(timeoutInSeconds, TimeUnit.SECONDS);
        } catch (ExecutionException ex) {
            if (ex.getCause() instanceof BootstrapException) {
                throw new OpenOfficeException("Failed to connect to open office. Please check open office path " + openOfficePath, ex);
            }
            throw new RuntimeException(ex.getCause());
        } catch (java.lang.Exception ex) {
            throw new OpenOfficeException(ex);
        } finally {
            if (future != null) {
                future.cancel(true);
            }
            connection.releaseResources();
        }
    }
}
