/*
 * Copyright (c) 2008-2013 Haulmont. All rights reserved.
 * Use is subject to license terms, see http://www.cuba-platform.com/license for details.
 */

package com.haulmont.reports.entity;

import com.haulmont.chile.core.annotations.NamePattern;
import com.haulmont.cuba.core.entity.annotation.SystemLevel;
import com.haulmont.yarg.formatters.CustomReport;
import org.apache.commons.lang.StringUtils;

import javax.persistence.*;
import java.io.ByteArrayInputStream;
import java.io.InputStream;

/**
 * Template for {@link Report}
 *
 * @author artamonov
 * @version $Id$
 */
@Entity(name = "report$ReportTemplate")
@Table(name = "REPORT_TEMPLATE")
@SystemLevel
@NamePattern("#getCaption|code,name,customClass")
@SuppressWarnings("unused")
public class ReportTemplate extends BaseReportEntity implements com.haulmont.yarg.structure.ReportTemplate {
    public static final String DEFAULT_TEMPLATE_CODE = "DEFAULT";

    private static final long serialVersionUID = 3692751073234357754L;

    public static final String NAME_FORMAT = "(%s) %s";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "REPORT_ID", nullable = false)
    protected Report report;

    @Column(name = "OUTPUT_TYPE")
    protected Integer reportOutputType;

    @Column(name = "CODE")
    protected String code;

    @Column(name = "IS_CUSTOM")
    protected Boolean custom = false;

    @Column(name = "CUSTOM_CLASS")
    protected String customClass;

    @Column(name = "CUSTOM_DEFINED_BY")
    protected Integer customDefinedBy = CustomTemplateDefinedBy.CLASS.getId();

    @Column(name = "OUTPUT_NAME_PATTERN")
    protected String outputNamePattern;

    @Column(name = "NAME", length = 500)
    protected String name;

    @Basic(fetch = FetchType.LAZY)
    @Column(name = "CONTENT")
    protected byte[] content;

    @Transient
    protected transient CustomReport customReport;

    public ReportOutputType getReportOutputType() {
        return reportOutputType != null ? ReportOutputType.fromId(reportOutputType) : null;
    }

    public void setReportOutputType(ReportOutputType reportOutputType) {
        this.reportOutputType = reportOutputType != null ? reportOutputType.getId() : null;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public Report getReport() {
        return report;
    }

    public void setReport(Report report) {
        this.report = report;
    }

    public Boolean getCustom() {
        return custom;
    }

    public void setCustom(Boolean custom) {
        this.custom = custom;
    }

    public String getCustomClass() {
        return customClass;
    }

    public void setCustomClass(String customClass) {
        this.customClass = customClass;
    }

    public byte[] getContent() {
        return content;
    }

    public void setContent(byte[] content) {
        this.content = content;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getExt() {
        return StringUtils.substringAfterLast(name, ".");
    }

    public CustomTemplateDefinedBy getCustomDefinedBy() {
        return CustomTemplateDefinedBy.fromId(customDefinedBy);
    }

    public void setCustomDefinedBy(CustomTemplateDefinedBy customDefinedBy) {
        this.customDefinedBy = CustomTemplateDefinedBy.getId(customDefinedBy);
    }

    @Override
    public String getDocumentName() {
        return name;
    }

    @Override
    public String getDocumentPath() {
        return name;
    }

    @Override
    public InputStream getDocumentContent() {
        return new ByteArrayInputStream(content);
    }

    @Override
    public com.haulmont.yarg.structure.ReportOutputType getOutputType() {
        return getReportOutputType() != null ? getReportOutputType().getOutputType() : null;
    }

    public void setOutputNamePattern(String outputNamePattern) {
        this.outputNamePattern = outputNamePattern;
    }

    @Override
    public String getOutputNamePattern() {
        return outputNamePattern;
    }

    @Override
    public boolean isCustom() {
        return Boolean.TRUE.equals(custom);
    }

    @Override
    public CustomReport getCustomReport() {
        return customReport;
    }

    public void setCustomReport(CustomReport customReport) {
        this.customReport = customReport;
    }

    public String getCaption() {
        if (isCustom()) {
            return String.format(NAME_FORMAT, code, customClass);
        } else {
            return String.format(NAME_FORMAT, code, name);
        }
    }
}
