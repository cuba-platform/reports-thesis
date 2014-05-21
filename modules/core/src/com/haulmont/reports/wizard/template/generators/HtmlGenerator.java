/*
 * Copyright (c) 2008-2014 Haulmont. All rights reserved.
 * Use is subject to license terms, see http://www.cuba-platform.com/license for details.
 */

package com.haulmont.reports.wizard.template.generators;

import com.haulmont.cuba.core.global.AppBeans;
import com.haulmont.cuba.core.global.Messages;
import com.haulmont.reports.entity.wizard.RegionProperty;
import com.haulmont.reports.entity.wizard.ReportData;
import com.haulmont.reports.entity.wizard.ReportRegion;
import com.haulmont.reports.wizard.ReportingWizardBean;
import com.haulmont.reports.wizard.template.Generator;
import com.haulmont.reports.wizard.template.ReportTemplatePlaceholder;
import freemarker.cache.StringTemplateLoader;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

/**
 * @author fedorchenko
 * @version $Id$
 */
public class HtmlGenerator implements Generator {
    protected ReportTemplatePlaceholder reportTemplatePlaceholder = new ReportTemplatePlaceholder();
    protected static final String HTML_TEMPLATE_NAME = "defaultTemplate";
    protected static final String HTML_TEMPLATE_PLACEHOLDER_TITLE = "title";
    protected static final String HTML_TEMPLATE_PLACEHOLDER_STYLES = "styles";
    protected static final String HTML_TEMPLATE_PLACEHOLDER_BODY = "body";
    protected volatile static Configuration freeMarkerConfiguration;
    protected ReportData reportData;

    public byte[] generate(ReportData reportData) throws TemplateException, IOException {
        this.reportData = reportData;
        byte[] template;
        Configuration conf = getFreemarkerConfiguration();
        Template freeMarkerHtmlReportTemplate = conf.getTemplate(HTML_TEMPLATE_NAME);
        StringWriter out = new StringWriter(2048);
        Map<String, String> templateParameters = new HashMap<>();
        putTitleHtml(templateParameters);
        putStylesHtml(templateParameters);
        putBodyHtml(templateParameters);
        freeMarkerHtmlReportTemplate.process(templateParameters, out);
        template = out.toString().getBytes(Charset.forName("UTF-8"));
        return template;
    }

    protected void putTitleHtml(Map<String, String> templateParameters) {
        templateParameters.put(HTML_TEMPLATE_PLACEHOLDER_TITLE, reportData.getName());

    }

    protected void putStylesHtml(Map<String, String> templateParameters) {
        templateParameters.put(HTML_TEMPLATE_PLACEHOLDER_STYLES, " body  {font-family: 'Charis SIL', sans-serif;}\n tbody tr {height:20px; min-height:20px}\n");
    }

    protected void putBodyHtml(Map<String, String> templateParameters) {
        StringBuilder templateBody = new StringBuilder();
        //Add #assign statements:
        for (ReportRegion reportRegion : reportData.getReportRegions()) {
            //header of table is filled here, so the three lines of code below is unused:
            appendHtmlFreeMarkerAssignments(templateBody, reportRegion.getNameForBand());
        }

        appendFreeMarkerSettings(templateBody);

        for (ReportRegion reportRegion : reportData.getReportRegions()) {
            if (reportRegion.isTabulatedRegion()) {
                //Are U ready for a String porn?
                //table def
                templateBody.append("\n\n<table class=\"report-table\" border=\"1\" cellspacing=\"0\" >\n");
                //table header
                templateBody.append("<thead>\n<tr>\n");
                for (RegionProperty regionProperty : reportRegion.getRegionProperties()) {
                    templateBody.append("<th>").append(regionProperty.getHierarchicalLocalizedNameExceptRoot()).append("</th>\n");
                }
                //closing table header tags:
                templateBody.append("</tr>\n</thead>\n");
                //table body rows
                templateBody.append("<tbody>\n<#if ").append(reportRegion.getNameForBand()).append("?has_content>\n<#list ").
                        append(reportRegion.getNameForBand()).
                        append(" as row>\n<tr>");
                for (RegionProperty regionProperty : reportRegion.getRegionProperties()) {
                    templateBody.append("\n<td> ").append(reportTemplatePlaceholder.getHtmlPlaceholderValue(reportRegion, regionProperty)).append(" </td>");
                }
                //closing table and table body tags:
                templateBody.append("\n</tr>\n</#list>\n</#if>\n</tbody>\n</table>\n\n");
            } else {
                for (RegionProperty regionProperty : reportRegion.getRegionProperties()) {
                    templateBody.append("\n").
                            append("<p>").
                            append(regionProperty.getHierarchicalLocalizedNameExceptRoot()).
                            append(": ").
                            append(reportTemplatePlaceholder.getHtmlPlaceholderValue(reportRegion, regionProperty)).append("</p>");
                }
            }
        }
        templateParameters.put(HTML_TEMPLATE_PLACEHOLDER_BODY, templateBody.toString());
    }

    protected void appendFreeMarkerSettings(StringBuilder templateBody) {
        Messages messages = AppBeans.get(Messages.NAME);
        templateBody.append("\n<#setting boolean_format=\"").
                append(messages.getMessage(messages.getMainMessagePack(), "trueString")).
                append(",").
                append(messages.getMessage(messages.getMainMessagePack(), "falseString")).
                append("\">");
    }

    protected void appendHtmlFreeMarkerAssignments(StringBuilder stringBuilder, String bandName) {
        stringBuilder.append("\n<#assign ").
                append(bandName).
                append(" = ").
                append(ReportingWizardBean.ROOT_BAND_DEFINITION_NAME).
                append(".bands.").
                append(bandName).
                append("><br/>");
    }

    protected Configuration getFreemarkerConfiguration() {
        if (freeMarkerConfiguration == null) {
            synchronized (this) {
                if (freeMarkerConfiguration == null) {
                    freeMarkerConfiguration = new Configuration();
                    StringTemplateLoader stringTemplateLoader = new StringTemplateLoader();
                    stringTemplateLoader.putTemplate(HTML_TEMPLATE_NAME, getReportTemplateHtmlFreeMarkerTemplate());
                    freeMarkerConfiguration.setTemplateLoader(stringTemplateLoader);
                }
            }
        }
        return freeMarkerConfiguration;
    }

    protected String getReportTemplateHtmlFreeMarkerTemplate() {
        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.1//EN\" \"http://www.w3.org/TR/xhtml11/DTD/xhtml11.dtd\">\n" +
                "<html xmlns=\"http://www.w3.org/1999/xhtml\" xml:lang=\"ru\">\n" +
                "    <head>\n" +
                "        <title> ${" + HTML_TEMPLATE_PLACEHOLDER_TITLE + "!\"Html template\"} </title>\n" +
                "        <style type=\"text/css\">\n" +
                "            ${" + HTML_TEMPLATE_PLACEHOLDER_STYLES + "!\"<!--put Your styles here-->\"}\n" +
                "        </style>\n" +
                "    </head>\n" +
                "    <body>\n" +
                "        ${" + HTML_TEMPLATE_PLACEHOLDER_BODY + "!\"\"}\n" +
                "    </body>\n" +
                "</html>";
    }

}
