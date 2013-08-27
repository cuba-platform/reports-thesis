/*
 * Copyright (c) 2008 Haulmont Technology Ltd. All Rights Reserved.
 * Haulmont Technology proprietary and confidential.
 * Use is subject to license terms.
 */
package com.haulmont.reports.entity;

import com.haulmont.chile.core.annotations.Composition;
import com.haulmont.chile.core.annotations.MetaProperty;
import com.haulmont.chile.core.annotations.NamePattern;
import com.haulmont.cuba.security.entity.Role;
import com.haulmont.yarg.structure.ReportBand;
import com.haulmont.yarg.structure.ReportFieldFormat;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.Predicate;
import org.apache.commons.lang.StringUtils;

import javax.persistence.*;
import java.util.*;

/**
 * @author degtyarjov
 * @version $Id$
 */
@Entity(name = "report$Report")
@Table(name = "REPORT_REPORT")
@NamePattern("%s|locName")
@SuppressWarnings("unused")
public class Report extends BaseReportEntity implements com.haulmont.yarg.structure.Report {
    private static final long serialVersionUID = -2817764915661205093L;

    @Column(name = "NAME")
    private String name;

    @Column(name = "LOCALE_NAMES")
    private String localeNames;

    @Column(name = "CODE")
    private String code;

    @ManyToOne
    @JoinColumn(name = "GROUP_ID")
    private ReportGroup group;

    @OneToOne
    @JoinColumn(name = "DEFAULT_TEMPLATE_ID")
    private ReportTemplate defaultTemplate;

    @Column(name = "REPORT_TYPE")
    private Integer reportType;

    @Transient
    private String localeName;

    @Transient
    private BandDefinition rootBandDefinition;

    @OneToMany(fetch = FetchType.LAZY, mappedBy = "report")
    @Composition
    private Set<BandDefinition> bands;

    @OneToMany(fetch = FetchType.LAZY, mappedBy = "report")
    @Composition
    private List<ReportTemplate> templates;

    @OneToMany(fetch = FetchType.LAZY, mappedBy = "report")
    @Composition
    @OrderBy("position")
    private List<ReportInputParameter> inputParameters;

    @OneToMany(fetch = FetchType.LAZY, mappedBy = "report")
    @Composition
    private List<ReportValueFormat> valuesFormats;

    @OneToMany(fetch = FetchType.LAZY, mappedBy = "report")
    @Composition
    private List<ReportScreen> reportScreens;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "REPORT_REPORTS_ROLES",
            inverseJoinColumns = @JoinColumn(name = "ROLE_ID", referencedColumnName = "ID"),
            joinColumns = @JoinColumn(name = "REPORT_ID", referencedColumnName = "ID")
    )
    private Set<Role> roles;

    @MetaProperty
    public BandDefinition getRootBandDefinition() {
        if (rootBandDefinition == null && bands != null && bands.size() > 0) {
            rootBandDefinition = (BandDefinition) CollectionUtils.find(bands, new Predicate() {
                @Override
                public boolean evaluate(Object object) {
                    BandDefinition band = (BandDefinition) object;
                    return band.getParentBandDefinition() == null;
                }
            });
        }
        return rootBandDefinition;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<ReportInputParameter> getInputParameters() {
        return inputParameters;
    }

    public void setInputParameters(List<ReportInputParameter> inputParameters) {
        this.inputParameters = inputParameters;
    }

    public List<ReportValueFormat> getValuesFormats() {
        return valuesFormats;
    }

    public void setValuesFormats(List<ReportValueFormat> valuesFormats) {
        this.valuesFormats = valuesFormats;
    }

    public ReportType getReportType() {
        return reportType != null ? ReportType.fromId(reportType) : null;
    }

    public void setReportType(ReportType reportType) {
        this.reportType = reportType != null ? reportType.getId() : null;
    }

    public Set<Role> getRoles() {
        return roles;
    }

    public void setRoles(Set<Role> roles) {
        this.roles = roles;
    }

    public List<ReportScreen> getReportScreens() {
        return reportScreens;
    }

    public void setReportScreens(List<ReportScreen> reportScreens) {
        this.reportScreens = reportScreens;
    }

    public List<ReportTemplate> getTemplates() {
        return templates;
    }

    public void setTemplates(List<ReportTemplate> templates) {
        this.templates = templates;
    }

//    /**
//     * Get default template for report
//     *
//     * @return Template
//     */
//    @MetaProperty
//    public ReportTemplate getDefaultTemplate() {
//        ReportTemplate template = null;
//        if (templates != null) {
//            if (templates.size() == 1)
//                template = templates.get(0);
//            else {
//                Iterator<ReportTemplate> iter = templates.iterator();
//                while (iter.hasNext() && template == null) {
//                    ReportTemplate temp = iter.next();
//                    if (temp.getDefaultFlag())
//                        template = temp;
//                }
//            }
//        }
//        return template;
//    }


    public ReportTemplate getDefaultTemplate() {
        return defaultTemplate;
    }

    public void setDefaultTemplate(ReportTemplate defaultTemplate) {
        this.defaultTemplate = defaultTemplate;
    }

    public ReportTemplate getTemplateByCode(String templateCode) {
        ReportTemplate template = null;
        if (templates != null) {
            Iterator<ReportTemplate> iter = templates.iterator();
            while (iter.hasNext() && template == null) {
                ReportTemplate temp = iter.next();
                if (StringUtils.equalsIgnoreCase(temp.getCode(), templateCode)) {
                    template = temp;
                }
            }
        }
        return template;
    }

    public ReportGroup getGroup() {
        return group;
    }

    public void setGroup(ReportGroup group) {
        this.group = group;
    }

    public Set<BandDefinition> getBands() {
        return bands;
    }

    public void setBands(Set<BandDefinition> bands) {
        this.bands = bands;
    }

    public String getLocaleNames() {
        return localeNames;
    }

    public void setLocaleNames(String localeNames) {
        this.localeNames = localeNames;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
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


    @Override
    public Map<String, com.haulmont.yarg.structure.ReportTemplate> getReportTemplates() {
        Map<String, com.haulmont.yarg.structure.ReportTemplate> templateMap = new HashMap<>();
        for (ReportTemplate template : templates) {
            templateMap.put(template.getCode(), template);
        }

        return templateMap;
    }

    @Override
    public List<com.haulmont.yarg.structure.ReportParameter> getReportParameters() {
        return (List) inputParameters;
    }

    @Override
    public List<ReportFieldFormat> getReportFieldFormats() {
        return (List) valuesFormats;
    }

    @Override
    public ReportBand getRootBand() {
        return getRootBandDefinition();
    }
}