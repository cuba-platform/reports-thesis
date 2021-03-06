/*
 * Copyright (c) 2008-2013 Haulmont. All rights reserved.
 * Use is subject to license terms, see http://www.cuba-platform.com/license for details.
 */

package com.haulmont.reports.entity;

import com.haulmont.chile.core.annotations.MetaProperty;
import com.haulmont.chile.core.annotations.NamePattern;
import org.apache.commons.lang.StringUtils;

import javax.persistence.*;
import java.util.Set;

/**
 * @author artamonov
 * @version $Id$
 */
@Entity(name = "report$ReportGroup")
@Table(name = "REPORT_GROUP")
@NamePattern("#getLocName|title,localeNames")
@SuppressWarnings("unused")
public class ReportGroup extends BaseReportEntity {

    private static final long serialVersionUID = 5399528790289039413L;

    @Column(name = "TITLE")
    private String title;

    @Column(name = "CODE")
    private String code;

    @Column(name = "LOCALE_NAMES")
    private String localeNames;

    @OneToMany(mappedBy = "group", fetch = FetchType.LAZY)
    private Set<Report> reports;

    @Transient
    private String localeName;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public Set<Report> getReports() {
        return reports;
    }

    public void setReports(Set<Report> reports) {
        this.reports = reports;
    }

    public String getLocaleNames() {
        return localeNames;
    }

    public void setLocaleNames(String localeNames) {
        this.localeNames = localeNames;
    }

    @MetaProperty
    public String getLocName() {
        if (localeName == null) {
            localeName = LocaleHelper.getLocalizedName(localeNames);
            if (localeName == null)
                localeName = title;
        }
        return localeName;
    }

    @MetaProperty
    public Boolean getSystemFlag() {
        return StringUtils.isNotEmpty(code);
    }
}