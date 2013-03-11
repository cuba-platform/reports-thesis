/*
 * Copyright (c) 2008 Haulmont Technology Ltd. All Rights Reserved.
 * Haulmont Technology proprietary and confidential.
 * Use is subject to license terms.
 */
package com.haulmont.reports.entity;

import com.haulmont.chile.core.annotations.MetaProperty;
import com.haulmont.chile.core.annotations.NamePattern;
import com.haulmont.cuba.core.entity.annotation.SystemLevel;

import javax.persistence.*;

/**
 * @author degtyarjov
 * @version $Id$
 */
@Entity(name = "report$ReportInputParameter")
@Table(name = "REPORT_INPUT_PARAMETER")
@SystemLevel
@NamePattern("%s|locName")
@SuppressWarnings("unused")
public class ReportInputParameter extends HardDeleteEntity {
    private static final long serialVersionUID = 6231014880104406246L;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "REPORT_ID")
    private Report report;

    @Column(name = "PARAMETER_TYPE")
    private Integer type;

    @Column(name = "NAME")
    private String name;

    @Column(name = "LOCALE_NAMES")
    private String localeNames;

    @Transient
    private String localeName;

    @Column(name = "ALIAS")
    private String alias;

    @Column(name = "POSITION_")
    private Integer position;

    @Column(name = "META_CLASS")
    private String entityMetaClass;

    @Column(name = "ENUM_CLASS")
    private String enumerationClass;

    @Column(name = "SCREEN")
    private String screen;

    @Column(name = "FROM_BROWSER")
    private Boolean getFromBrowser = false;

    @Column(name = "REQUIRED")
    private Boolean required = false;

    public Report getReport() {
        return report;
    }

    public void setReport(Report report) {
        this.report = report;
    }

    public ParameterType getType() {
        return type != null ? ParameterType.fromId(type) : null;
    }

    public void setType(ParameterType type) {
        this.type = type != null ? type.getId() : null;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAlias() {
        return alias;
    }

    public void setAlias(String alias) {
        this.alias = alias;
    }

    public String getEntityMetaClass() {
        return entityMetaClass;
    }

    public void setEntityMetaClass(String entityMetaClass) {
        this.entityMetaClass = entityMetaClass;
    }

    public String getEnumerationClass() {
        return enumerationClass;
    }

    public void setEnumerationClass(String enumerationClass) {
        this.enumerationClass = enumerationClass;
    }

    public Boolean getGetFromBrowser() {
        return getFromBrowser;
    }

    public void setGetFromBrowser(Boolean getFromBrowser) {
        this.getFromBrowser = getFromBrowser;
    }

    public Boolean getRequired() {
        return required;
    }

    public void setRequired(Boolean required) {
        this.required = required;
    }

    public Integer getPosition() {
        return position;
    }

    public void setPosition(Integer position) {
        this.position = position;
    }

    public String getScreen() {
        return screen;
    }

    public void setScreen(String screen) {
        this.screen = screen;
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
                localeName = name;
        }
        return localeName;
    }
}
