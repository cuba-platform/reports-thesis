/*
 * Copyright (c) 2008 Haulmont Technology Ltd. All Rights Reserved.
 * Haulmont Technology proprietary and confidential.
 * Use is subject to license terms.
 */
package com.haulmont.reports.app.service;

import com.haulmont.cuba.core.entity.FileDescriptor;
import com.haulmont.cuba.core.global.FileStorageException;
import com.haulmont.reports.entity.Report;
import com.haulmont.reports.entity.ReportTemplate;
import com.haulmont.yarg.reporting.ReportOutputDocument;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;

/**
 * @author degtyarjov
 * @version $id$
 */
public interface ReportService {
    String NAME = "report_ReportService";

    ReportOutputDocument createReport(Report report,
                                      Map<String, Object> params) throws IOException;

    ReportOutputDocument createReport(Report report, String templateCode,
                                      Map<String, Object> params) throws IOException;

    ReportOutputDocument createReport(Report report, ReportTemplate template,
                                      Map<String, Object> params) throws IOException;

    FileDescriptor createAndSaveReport(Report report,
                                       Map<String, Object> params, String fileName) throws IOException;

    FileDescriptor createAndSaveReport(Report report, String templateCode,
                                       Map<String, Object> params, String fileName) throws IOException;

    FileDescriptor createAndSaveReport(Report report, ReportTemplate template,
                                       Map<String, Object> params, String fileName) throws IOException;

    /**
     * Exports all reports and their templates into one zip archive. Each report is exported into a separete zip
     * archive with 2 files (report.xml and a template file (for example MyReport.doc)).
     * For example:
     * return byte[] (bytes of zip arhive)
     * -- MegaReport.zip
     * ---- report.xml
     * ---- Mega report.xls
     * -- Other report.zip
     * ---- report.xml
     * ---- other report.odt
     *
     * @param reports Collection of Report objects to be exported.
     * @return ZIP byte array with zip archives inside.
     * @throws com.haulmont.cuba.core.global.FileStorageException
     *                             Exception in file system
     * @throws java.io.IOException Exception in I/O streams
     */
    byte[] exportReports(Collection<Report> reports) throws IOException, FileStorageException;

    /**
     * Imports reports from ZIP archive. Archive file format is described in exportReports method.
     *
     * @param zipBytes ZIP archive as a byte array.
     * @return Collection of imported reports.
     * @throws IOException          Exception in I/O streams
     * @throws FileStorageException Exception in file system
     */
    Collection<Report> importReports(byte[] zipBytes) throws IOException, FileStorageException;

    String convertToXml(Report report);

    Report convertToReport(String xml);
}
