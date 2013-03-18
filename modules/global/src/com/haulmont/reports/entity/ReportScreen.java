/*
 * Copyright (c) 2010 Haulmont Technology Ltd. All Rights Reserved.
 * Haulmont Technology proprietary and confidential.
 * Use is subject to license terms.
 */
package com.haulmont.reports.entity;

import com.haulmont.cuba.core.entity.annotation.SystemLevel;

import javax.persistence.*;

/**
 * @author fontanenko
 * @version $Id$
 */
@Entity(name = "report$ReportScreen")
@Table(name = "REPORT_REPORT_SCREEN")
@SystemLevel
public class ReportScreen extends BaseReportEntity {

    private static final long serialVersionUID = -7416940515333599470L;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "REPORT_ID")
    private Report report;

    @Column(name="SCREEN_ID")
    private String screenId;

    public Report getReport() {
        return report;
    }

    public void setReport(Report report) {
        this.report = report;
    }

    public String getScreenId() {
        return screenId;
    }

    public void setScreenId(String screenId) {
        this.screenId = screenId;
    }
}
