/*
 * Copyright (c) 2008-2013 Haulmont. All rights reserved.
 * Use is subject to license terms, see http://www.cuba-platform.com/license for details.
 */

package com.haulmont.reports;

import com.haulmont.chile.core.model.MetaClass;
import com.haulmont.chile.core.model.utils.InstanceUtils;
import com.haulmont.cuba.core.EntityManager;
import com.haulmont.cuba.core.Persistence;
import com.haulmont.cuba.core.Query;
import com.haulmont.cuba.core.Transaction;
import com.haulmont.cuba.core.app.FileStorageAPI;
import com.haulmont.cuba.core.entity.Entity;
import com.haulmont.cuba.core.entity.FileDescriptor;
import com.haulmont.cuba.core.global.*;
import com.haulmont.reports.app.ParameterPrototype;
import com.haulmont.reports.entity.*;
import com.haulmont.reports.exception.*;
import com.haulmont.reports.libintegration.CustomFormatter;
import com.haulmont.yarg.formatters.impl.doc.connector.NoFreePortsException;
import com.haulmont.yarg.reporting.ReportOutputDocument;
import com.haulmont.yarg.reporting.ReportOutputDocumentImpl;
import com.haulmont.yarg.reporting.ReportingAPI;
import com.haulmont.yarg.reporting.RunParams;
import com.haulmont.yarg.structure.ReportOutputType;
import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.converters.basic.DateConverter;
import com.thoughtworks.xstream.converters.collections.CollectionConverter;
import com.thoughtworks.xstream.converters.reflection.ExternalizableConverter;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.Predicate;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.perf4j.StopWatch;
import org.perf4j.log4j.Log4JStopWatch;
import org.slf4j.MDC;

import javax.annotation.ManagedBean;
import javax.inject.Inject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.zip.CRC32;

/**
 * @author artamonov
 * @version $Id$
 */
@ManagedBean(ReportingApi.NAME)
public class ReportingBean implements ReportingApi {
    public static final String REPORT_EDIT_VIEW_NAME = "report.edit";

    protected static final int MAX_REPORT_NAME_LENGTH = 255;

    protected PrototypesLoader prototypesLoader = new PrototypesLoader();

    @Inject
    protected Persistence persistence;
    @Inject
    protected Metadata metadata;
    @Inject
    protected FileStorageAPI fileStorageAPI;
    @Inject
    protected TimeSource timeSource;
    @Inject
    protected ReportingAPI reportingApi;
    @Inject
    protected ReportImportExportAPI reportImportExport;
    @Inject
    protected UuidSource uuidSource;
    @Inject
    protected UserSessionSource userSessionSource;
    @Inject
    protected GlobalConfig globalConfig;

    protected XStream createXStream() {
        XStream xStream = new XStream();
        xStream.getConverterRegistry().removeConverter(ExternalizableConverter.class);
        xStream.registerConverter(new CollectionConverter(xStream.getMapper()) {
            @Override
            public boolean canConvert(Class type) {
                return ArrayList.class.isAssignableFrom(type) ||
                        HashSet.class.isAssignableFrom(type) ||
                        LinkedList.class.isAssignableFrom(type) ||
                        LinkedHashSet.class.isAssignableFrom(type);

            }
        }, XStream.PRIORITY_VERY_HIGH);

        xStream.registerConverter(new DateConverter() {
            @Override
            public boolean canConvert(Class type) {
                return Date.class.isAssignableFrom(type);
            }
        });

        xStream.alias("report", Report.class);
        xStream.alias("band", BandDefinition.class);
        xStream.alias("dataSet", DataSet.class);
        xStream.alias("parameter", ReportInputParameter.class);
        xStream.alias("template", ReportTemplate.class);
        xStream.alias("screen", ReportScreen.class);
        xStream.alias("format", ReportValueFormat.class);
        xStream.addDefaultImplementation(LinkedHashMap.class, Map.class);
        xStream.aliasSystemAttribute(null, "class");

        xStream.omitField(Report.class, "xml");
        xStream.omitField(Report.class, "deleteTs");
        xStream.omitField(Report.class, "deletedBy");
        xStream.omitField(ReportTemplate.class, "content");
        xStream.omitField(ReportTemplate.class, "defaultFlag");
        xStream.omitField(ReportTemplate.class, "templateFileDescriptor");
        xStream.omitField(ReportTemplate.class, "deleteTs");
        xStream.omitField(ReportTemplate.class, "deletedBy");
        xStream.omitField(ReportInputParameter.class, "localeName");

        xStream.aliasField("customFlag", ReportTemplate.class, "custom");
        xStream.aliasField("customClass", ReportTemplate.class, "customDefinition");
        xStream.aliasField("uuid", BandDefinition.class, "id");
        xStream.aliasField("uuid", DataSet.class, "id");
        xStream.aliasField("uuid", ReportInputParameter.class, "id");
        xStream.aliasField("uuid", ReportScreen.class, "id");
        xStream.aliasField("definedBy", ReportTemplate.class, "customDefinedBy");
        xStream.aliasField("uuid", ReportValueFormat.class, "id");

        return xStream;
    }


    @Override
    public ReportOutputDocument createReport(Report report, Map<String, Object> params) {
        report = reloadEntity(report, REPORT_EDIT_VIEW_NAME);
        ReportTemplate reportTemplate = getDefaultTemplate(report);
        return createReportDocument(report, reportTemplate, params);
    }

    @Override
    public ReportOutputDocument createReport(Report report, String templateCode, Map<String, Object> params) {
        report = reloadEntity(report, REPORT_EDIT_VIEW_NAME);
        ReportTemplate template = report.getTemplateByCode(templateCode);
        return createReportDocument(report, template, params);
    }

    @Override
    public ReportOutputDocument bulkPrint(Report report, List<Map<String, Object>> paramsList) {
        try {
            report = reloadEntity(report, REPORT_EDIT_VIEW_NAME);

            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

            ZipArchiveOutputStream zipOutputStream = new ZipArchiveOutputStream(byteArrayOutputStream);
            zipOutputStream.setMethod(ZipArchiveOutputStream.STORED);
            zipOutputStream.setEncoding(ReportImportExport.ENCODING);

            ReportTemplate reportTemplate = getDefaultTemplate(report);
            Map<String, Integer> alreadyUsedNames = new HashMap<>();

            for (Map<String, Object> params : paramsList) {
                ReportOutputDocument reportDocument = createReportDocument(report, reportTemplate, params);

                String documentName = reportDocument.getDocumentName();
                if (alreadyUsedNames.containsKey(documentName)) {
                    int newCount = alreadyUsedNames.get(documentName) + 1;
                    alreadyUsedNames.put(documentName, newCount);
                    documentName = StringUtils.substringBeforeLast(documentName, ".")
                            + newCount
                            + "."
                            + StringUtils.substringAfterLast(documentName, ".");
                    alreadyUsedNames.put(documentName, 1);
                } else {
                    alreadyUsedNames.put(documentName, 1);
                }

                ArchiveEntry singleReportEntry = newStoredEntry(documentName, reportDocument.getContent());
                zipOutputStream.putArchiveEntry(singleReportEntry);
                zipOutputStream.write(reportDocument.getContent());
            }

            zipOutputStream.closeArchiveEntry();
            zipOutputStream.close();

            //noinspection UnnecessaryLocalVariable
            ReportOutputDocument reportOutputDocument =
                    new ReportOutputDocumentImpl(report, byteArrayOutputStream.toByteArray(), "Reports.zip", ReportOutputType.custom);
            return reportOutputDocument;
        } catch (IOException e) {
            throw new ReportingException("An error occurred while zipping report contents", e);
        }
    }

    private ArchiveEntry newStoredEntry(String name, byte[] data) {
        ZipArchiveEntry zipEntry = new ZipArchiveEntry(name);
        zipEntry.setSize(data.length);
        zipEntry.setCompressedSize(zipEntry.getSize());
        CRC32 crc32 = new CRC32();
        crc32.update(data);
        zipEntry.setCrc(crc32.getValue());
        return zipEntry;
    }

    @Override
    public ReportOutputDocument createReport(Report report, ReportTemplate template, Map<String, Object> params) {
        report = reloadEntity(report, REPORT_EDIT_VIEW_NAME);
        return createReportDocument(report, template, params);
    }

    protected ReportOutputDocument createReportDocument(Report report, ReportTemplate template, Map<String, Object> params) {
        StopWatch stopWatch = null;
        MDC.put("user", userSessionSource.getUserSession().getUser().getLogin());
        MDC.put("webContextName", globalConfig.getWebContextName());
        try {
            stopWatch = new Log4JStopWatch("Reporting#" + report.getName());
            List<String> prototypes = new LinkedList<>();
            for (Map.Entry<String, Object> param : params.entrySet()) {
                if (param.getValue() instanceof ParameterPrototype)
                    prototypes.add(param.getKey());
            }
            Map<String, Object> resultParams = new HashMap<>(params);

            for (String paramName : prototypes) {
                ParameterPrototype prototype = (ParameterPrototype) params.get(paramName);
                List data = loadDataForParameterPrototype(prototype);
                resultParams.put(paramName, data);
            }

            if (template.isCustom()) {
                CustomFormatter customFormatter = new CustomFormatter(report, template);
                template.setCustomReport(customFormatter);
            }

            return reportingApi.runReport(new RunParams(report).template(template).params(resultParams));
        } catch (NoFreePortsException nfe) {
            throw new NoOpenOfficeFreePortsException(nfe.getMessage());
        } catch (com.haulmont.yarg.exception.OpenOfficeException ooe) {
            throw new FailedToConnectToOpenOfficeException(ooe.getMessage());
        } catch (com.haulmont.yarg.exception.UnsupportedFormatException fe) {
            throw new UnsupportedFormatException(fe.getMessage());
        } catch (com.haulmont.yarg.exception.ValidationException ve) {
            throw new ValidationException(ve.getMessage());
        } catch (com.haulmont.yarg.exception.ReportingException re) {
            //noinspection unchecked
            List<Throwable> list = ExceptionUtils.getThrowableList(re);
            StringBuilder sb = new StringBuilder();
            for (Iterator<Throwable> it = list.iterator(); it.hasNext(); ) {
                //noinspection ThrowableResultOfMethodCallIgnored
                sb.append(it.next().getMessage());
                if (it.hasNext())
                    sb.append("\n");
            }

            throw new ReportingException(sb.toString());
        } finally {
            MDC.remove("user");
            MDC.remove("webContextName");
            if (stopWatch != null) {
                stopWatch.stop();
            }
        }
    }

    public List loadDataForParameterPrototype(ParameterPrototype prototype) {
        return prototypesLoader.loadData(prototype);
    }

    @Override
    public Report copyReport(Report source) {
        source = reloadEntity(source, REPORT_EDIT_VIEW_NAME);
        Report copiedReport = (Report) InstanceUtils.copy(source);

        copiedReport.setId(uuidSource.createUuid());
        copiedReport.setTemplates(null);

        List<ReportTemplate> copiedTemplates = new ArrayList<>();
        ReportTemplate defaultCopiedTemplate = null;

        for (ReportTemplate reportTemplate : source.getTemplates()) {
            ReportTemplate copiedTemplate = (ReportTemplate) InstanceUtils.copy(reportTemplate);
            copiedTemplate.setId(uuidSource.createUuid());
            copiedTemplate.setReport(copiedReport);
            copiedTemplates.add(copiedTemplate);
            if (getDefaultTemplate(source).equals(reportTemplate)) {
                defaultCopiedTemplate = copiedTemplate;
            }
        }


        Transaction tx = persistence.createTransaction();
        try {
            copiedReport.setName(generateReportName(source.getName(), 2));
            EntityManager em = persistence.getEntityManager();
            em.persist(copiedReport);
            for (ReportTemplate copiedTemplate : copiedTemplates) {
                em.persist(copiedTemplate);
            }
            copiedReport.setTemplates(copiedTemplates);
            copiedReport.setDefaultTemplate(defaultCopiedTemplate);

            copiedReport.setXml(convertToXml(copiedReport));
            tx.commit();
        } finally {
            tx.end();
        }

        return copiedReport;
    }

    protected String generateReportName(String sourceName, int iteration) {
        if (iteration == 1) {
            iteration++; //like in win 7: duplicate of file 'a.txt' is 'a (2).txt', NOT 'a (1).txt'
        }
        EntityManager em = persistence.getEntityManager();
        String reportName = StringUtils.stripEnd(sourceName, null);
        if (iteration > 0) {
            String newReportName = String.format("%s (%s)", reportName, iteration);
            if (newReportName.length() > MAX_REPORT_NAME_LENGTH) {

                String abbreviatedReportName = StringUtils.abbreviate(reportName, MAX_REPORT_NAME_LENGTH -
                        String.valueOf(iteration).length() - 3);// 3 cause it us " ()".length

                reportName = String.format("%s (%s)", abbreviatedReportName, iteration);
            } else {
                reportName = newReportName;
            }

        }
        Query q = em.createQuery("select r from report$Report r where r.name = :name");
        q.setParameter("name", reportName);
        if (q.getResultList().size() > 0) {
            return generateReportName(sourceName, ++iteration);
        }

        return reportName;
    }

    @Override
    public byte[] exportReports(Collection<Report> reports) {
        return reportImportExport.exportReports(reports);
    }

    @Override
    public FileDescriptor createAndSaveReport(Report report, Map<String, Object> params, String fileName) {
        report = reloadEntity(report, REPORT_EDIT_VIEW_NAME);
        ReportTemplate template = getDefaultTemplate(report);
        return createAndSaveReport(report, template, params, fileName);
    }

    @Override
    public FileDescriptor createAndSaveReport(Report report, String templateCode,
                                              Map<String, Object> params, String fileName) {
        report = reloadEntity(report, REPORT_EDIT_VIEW_NAME);
        ReportTemplate template = report.getTemplateByCode(templateCode);
        return createAndSaveReport(report, template, params, fileName);
    }

    @Override
    public FileDescriptor createAndSaveReport(Report report, ReportTemplate template,
                                              Map<String, Object> params, String fileName) {
        report = reloadEntity(report, REPORT_EDIT_VIEW_NAME);
        return createAndSaveReportDocument(report, template, params, fileName);
    }

    protected FileDescriptor createAndSaveReportDocument(Report report, ReportTemplate template, Map<String, Object> params, String fileName) {
        byte[] reportData = createReportDocument(report, template, params).getContent();
        String ext = template.getReportOutputType().toString().toLowerCase();

        return saveReport(reportData, fileName, ext);
    }

    protected FileDescriptor saveReport(byte[] reportData, String fileName, String ext) {
        FileDescriptor file = new FileDescriptor();
        file.setCreateDate(timeSource.currentTimestamp());
        file.setName(fileName + "." + ext);
        file.setExtension(ext);
        file.setSize((long) reportData.length);

        try {
            fileStorageAPI.saveFile(file, reportData);
        } catch (FileStorageException e) {
            throw new ReportingException("An error occurred while saving the report to the file storage", e);
        }

        Transaction tx = persistence.createTransaction();
        try {
            EntityManager em = persistence.getEntityManager();
            em.persist(file);
            tx.commit();
        } finally {
            tx.end();
        }
        return file;
    }

    @Override
    public Collection<Report> importReports(byte[] zipBytes) {
        return reportImportExport.importReports(zipBytes);
    }

    @Override
    public Collection<Report> importReports(byte[] zipBytes, EnumSet<ReportImportOption> importOptions) {
        return reportImportExport.importReports(zipBytes, importOptions);
    }

    @Override
    public String convertToXml(Report report) {
        XStream xStream = createXStream();
        //noinspection UnnecessaryLocalVariable
        String xml = xStream.toXML(report);
        return xml;
    }

    @Override
    public Report convertToReport(String xml) {
        XStream xStream = createXStream();
        return (Report) xStream.fromXML(xml);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends Entity> T reloadEntity(T entity, View view) {
        if (entity instanceof Report && ((Report) entity).getIsTmp()) {
            return entity;
        }
        Transaction tx = persistence.createTransaction();
        try {
            EntityManager em = persistence.getEntityManager();
            em.setView(view);
            entity = (T) em.find(entity.getClass(), entity.getId());
            if (entity != null) {
                em.setView(null);
                em.fetch(entity, view);
            }
            tx.commit();
            return entity;
        } finally {
            tx.end();
        }
    }

    @Override
    public MetaClass findMetaClassByDataSetEntityAlias(final String alias, final DataSetType dataSetType, final List<ReportInputParameter> reportInputParameters) {
        if (reportInputParameters.isEmpty() || StringUtils.isBlank(alias)) {
            return null;
        }

        String realAlias;
        boolean isCollectionAlias;

        if (DataSetType.MULTI == dataSetType) {

            realAlias = StringUtils.substringBefore(alias, "#");
        } else {
            realAlias = alias;
        }
        isCollectionAlias = !alias.equals(realAlias);

        class ReportInputParameterAliasFilterPredicate implements Predicate {
            final DataSetType dataSetType;
            final String realAlias;
            final boolean isCollectionAlias;

            ReportInputParameterAliasFilterPredicate(DataSetType dataSetType, String realAlias, boolean isCollectionAlias) {
                this.dataSetType = dataSetType;
                this.realAlias = realAlias;
                this.isCollectionAlias = isCollectionAlias;
            }

            @Override
            public boolean evaluate(Object object) {
                ReportInputParameter filterCandidateParameter = null;
                if (object instanceof ReportInputParameter) {
                    filterCandidateParameter = (ReportInputParameter) object;
                }

                if (realAlias.equals(filterCandidateParameter.getAlias())) {
                    if (DataSetType.MULTI == dataSetType) {
                        //find param that is matched for a MULTI dataset
                        if (isCollectionAlias) {
                            if (ParameterType.ENTITY == filterCandidateParameter.getType()) {
                                return true;
                            }
                        } else {
                            if (ParameterType.ENTITY_LIST == filterCandidateParameter.getType()) {
                                return true;
                            }
                        }
                    } else if (DataSetType.SINGLE == dataSetType) {
                        //find param that is matched for a SINGLE dataset
                        if (ParameterType.ENTITY == filterCandidateParameter.getType()) {
                            return true;
                        }
                    }
                }
                return false;
            }
        }
        Predicate predicate = new ReportInputParameterAliasFilterPredicate(dataSetType, realAlias, isCollectionAlias);

        List<ReportInputParameter> filteredParams = new ArrayList<>(reportInputParameters);
        CollectionUtils.filter(filteredParams, predicate);
        if (filteredParams.size() == 1) {
            return metadata.getClass(filteredParams.get(0).getEntityMetaClass());
        } else {
            return null;
        }
    }

    @Override
    public String generateReportName(String sourceName) {
        return generateReportName(sourceName, 0);
    }

    @SuppressWarnings("unchecked")
    protected <T extends Entity> T reloadEntity(T entity, String viewName) {
        if (entity instanceof Report && ((Report) entity).getIsTmp()) {
            return entity;
        }
        Transaction tx = persistence.createTransaction();
        try {
            EntityManager em = persistence.getEntityManager();
            View targetView = metadata.getViewRepository().getView(entity.getClass(), viewName);
            em.setView(targetView);
            entity = (T) em.find(entity.getClass(), entity.getId());
            if (entity != null) {
                em.setView(null);
                em.fetch(entity, targetView);
            }
            tx.commit();
            return entity;
        } finally {
            tx.end();
        }
    }

    protected ReportTemplate getDefaultTemplate(Report report) {
        ReportTemplate defaultTemplate = report.getDefaultTemplate();
        if (defaultTemplate == null)
            throw new ReportingException(String.format("No default template specified for report [%s]", report.getName()));
        return defaultTemplate;
    }
}