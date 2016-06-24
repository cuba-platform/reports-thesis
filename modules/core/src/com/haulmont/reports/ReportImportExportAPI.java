/**
 *
 * @author degtyarjov
 * @version $Id$
 */
package com.haulmont.reports;

import com.haulmont.reports.entity.Report;
import com.haulmont.reports.entity.ReportImportOption;

import java.util.Collection;
import java.util.EnumSet;

/**
 * @author degtyarjov
 * @version $Id$
 */
public interface ReportImportExportAPI {
    final String NAME = "reporting_ReportImportExport";

    byte[] exportReports(Collection<Report> reports);
    Collection<Report> importReports(byte[] zipBytes);
    Collection<Report> importReports(byte[] zipBytes, EnumSet<ReportImportOption> importOptions);
}
