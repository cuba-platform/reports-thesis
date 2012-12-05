/*
 * Copyright (c) 2011 Haulmont Technology Ltd. All Rights Reserved.
 * Haulmont Technology proprietary and confidential.
 * Use is subject to license terms.
 */

package com.haulmont.reports;

import com.haulmont.cuba.core.EntityManager;
import com.haulmont.cuba.core.Persistence;
import com.haulmont.cuba.core.Transaction;
import com.haulmont.cuba.core.app.FileStorageAPI;
import com.haulmont.cuba.core.entity.Entity;
import com.haulmont.cuba.core.entity.FileDescriptor;
import com.haulmont.cuba.core.global.FileStorageException;
import com.haulmont.cuba.core.global.Metadata;
import com.haulmont.cuba.core.global.TimeSource;
import com.haulmont.cuba.core.global.View;
import com.haulmont.reports.app.ParameterPrototype;
import com.haulmont.reports.app.ReportOutputDocument;
import com.haulmont.reports.entity.*;
import com.haulmont.reports.exception.ReportingException;
import com.haulmont.reports.exception.UnsupportedFormatException;
import com.haulmont.reports.formatters.*;
import com.haulmont.reports.loaders.*;
import org.apache.commons.lang.StringUtils;
import org.perf4j.StopWatch;
import org.perf4j.log4j.Log4JStopWatch;

import javax.annotation.ManagedBean;
import javax.annotation.Nullable;
import javax.inject.Inject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;

/**
 * @author artamonov
 * @version $Id$
 */
@ManagedBean(ReportingApi.NAME)
public class ReportingBean implements ReportingApi {

    public static final String REPORT_FILE_NAME_KEY = "__REPORT_FILE_NAME";
    public static final String REPORT_EDIT_VIEW_NAME = "report.edit";

    // todo remove thread locals
    private ThreadLocal<Map<String, Object>> params = new ThreadLocal<>();
    private ThreadLocal<Set<String>> bandDefinitionNames = new ThreadLocal<>();

    private PrototypesLoader prototypesLoader = new PrototypesLoader();

    @Inject
    private Persistence persistence;

    @Inject
    private Metadata metadata;

    @Inject
    private FileStorageAPI fileStorageAPI;

    @Inject
    private TimeSource timeSource;

    @Override
    public ReportOutputDocument createReport(Report report, Map<String, Object> params) throws IOException {
        report = reloadEntity(report, REPORT_EDIT_VIEW_NAME);
        ReportTemplate reportTemplate = report.getDefaultTemplate();
        return createReportDocument(report, reportTemplate, params);
    }

    @Override
    public ReportOutputDocument createReport(Report report, String templateCode, Map<String, Object> params)
            throws IOException {
        report = reloadEntity(report, REPORT_EDIT_VIEW_NAME);
        ReportTemplate template = report.getTemplateByCode(templateCode);
        return createReportDocument(report, template, params);
    }

    @Override
    public ReportOutputDocument createReport(Report report, ReportTemplate template, Map<String, Object> params)
            throws IOException {
        report = reloadEntity(report, REPORT_EDIT_VIEW_NAME);
        return createReportDocument(report, template, params);
    }

    private ReportOutputDocument createReportDocument(Report report, ReportTemplate template, Map<String, Object> params)
            throws IOException {

        if (template == null)
            throw new NullPointerException("Report template is null");

        String watchName = "Report.";
        Band rootBand;
        if (StringUtils.isNotEmpty(report.getCode()))
            watchName += report.getCode();
        else
            watchName += report.getId();

        StopWatch loadDataWatch = new Log4JStopWatch(watchName + "#data");
        try {
            // Preprocess prototypes
            List<String> prototypes = new LinkedList<>();
            for (Map.Entry<String, Object> param : params.entrySet()) {
                if (param.getValue() instanceof ParameterPrototype)
                    prototypes.add(param.getKey());
            }
            Map<String, Object> paramsMap = new HashMap<>(params);

            for (String paramName : prototypes) {
                ParameterPrototype prototype = (ParameterPrototype) params.get(paramName);
                List data = prototypesLoader.loadData(prototype);
                paramsMap.put(paramName, data);
            }

            this.params.set(paramsMap);
            this.bandDefinitionNames.set(new HashSet<String>());

            if (template.getCustomFlag()) {
                byte[] content = new CustomFormatter(report, template, params).createDocument(null);
                return new ReportOutputDocument(report, template.getReportOutputType(), content);
            }
            BandDefinition rootBandDefinition = report.getRootBandDefinition();

            List<BandDefinition> childrenBandDefinitions = rootBandDefinition.getChildrenBandDefinitions();
            rootBand = createRootBand(rootBandDefinition);

            HashMap<String, ReportValueFormat> valuesFormats = new HashMap<>();
            for (ReportValueFormat valueFormat : report.getValuesFormats()) {
                valuesFormats.put(valueFormat.getValueName(), valueFormat);
            }
            rootBand.setValuesFormats(valuesFormats);

            for (BandDefinition definition : childrenBandDefinitions) {
                bandDefinitionNames.get().add(definition.getName());
                List<Band> bands = createBands(definition, rootBand);
                rootBand.addChildren(bands);
            }
            rootBand.setBandDefinitionNames(bandDefinitionNames.get());
        } catch (ReportingException ex) {
            throw ex;
        } catch (Exception e) {
            throw new ReportingException(e);
        } finally {
            loadDataWatch.stop();
        }

        StopWatch processWatch = new Log4JStopWatch(watchName + "#process");
        try {
            ByteArrayOutputStream resultStream = new ByteArrayOutputStream();

            ReportEngine reportEngine = getReportEngine(template);
            ReportOutputType format = template.getReportOutputType();

            if (reportEngine.hasSupportReport(
                    template.getTemplateFileDescriptor().getExtension(), format)) {
                reportEngine.setTemplateFile(template.getTemplateFileDescriptor());
                reportEngine.createDocument(rootBand, format, resultStream);
            } else
                throw new UnsupportedFormatException();

            byte[] result = resultStream.toByteArray();
            ReportOutputDocument reportOutputDocument = new ReportOutputDocument(report,
                    template.getReportOutputType(), result);
            setNameToOutputFile(rootBand, reportOutputDocument);

            return reportOutputDocument;
        } catch (ReportingException ex) {
            throw ex;
        } catch (Exception e) {
            throw new ReportingException(e);
        } finally {
            processWatch.stop();
        }
    }

    private void setNameToOutputFile(Band rootBand, ReportOutputDocument reportOutputDocument) {
        Object reportFileName = rootBand.getData().get(REPORT_FILE_NAME_KEY);
        if (reportFileName != null) {
            reportOutputDocument.setDocumentName(reportFileName.toString());
        }
    }

    @Override
    public Report reloadReport(Report report) {
        Transaction tx = persistence.createTransaction();
        try {
            EntityManager em = persistence.getEntityManager();
            View exportView = metadata.getViewRepository().getView(report.getClass(), "report.export");
            em.setView(exportView);
            report = em.find(Report.class, report.getId());
            if (report != null) {
                em.setView(null);
                em.fetch(report, exportView);
            }
            tx.commit();
            return report;
        } finally {
            tx.end();
        }
    }

    @Override
    public byte[] exportReports(Collection<Report> reports) throws IOException, FileStorageException {
        return ImportExportHelper.exportReports(reports);
    }

    @Override
    public FileDescriptor createAndSaveReport(Report report,
                                              Map<String, Object> params, String fileName) throws IOException {
        report = reloadEntity(report, REPORT_EDIT_VIEW_NAME);
        ReportTemplate template = report.getDefaultTemplate();
        return createAndSaveReport(report, template, params, fileName);
    }

    @Override
    public FileDescriptor createAndSaveReport(Report report, String templateCode,
                                              Map<String, Object> params, String fileName) throws IOException {
        report = reloadEntity(report, REPORT_EDIT_VIEW_NAME);
        ReportTemplate template = report.getTemplateByCode(templateCode);
        return createAndSaveReport(report, template, params, fileName);
    }

    @Override
    public FileDescriptor createAndSaveReport(Report report, ReportTemplate template,
                                              Map<String, Object> params, String fileName) throws IOException {
        report = reloadEntity(report, REPORT_EDIT_VIEW_NAME);
        return createAndSaveReportDocument(report, template, params, fileName);
    }

    private FileDescriptor createAndSaveReportDocument(Report report, ReportTemplate template, Map<String, Object> params, String fileName) throws IOException {
        byte[] reportData = createReportDocument(report, template, params).getContent();
        String ext = template.getReportOutputType().toString().toLowerCase();

        return saveReport(reportData, fileName, ext);
    }

    private FileDescriptor saveReport(byte[] reportData, String fileName, String ext) throws IOException {
        FileDescriptor file = new FileDescriptor();
        file.setCreateDate(timeSource.currentTimestamp());
        file.setName(fileName + "." + ext);
        file.setExtension(ext);
        file.setSize(reportData.length);

        try {
            fileStorageAPI.saveFile(file, reportData);
        } catch (FileStorageException e) {
            throw new IOException(e);
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
    public Collection<Report> importReports(byte[] zipBytes) throws IOException, FileStorageException {
        return ImportExportHelper.importReports(zipBytes);
    }

    private ReportEngine getReportEngine(ReportTemplate template) {
        ReportEngine reportEngine = null;
        FileDescriptor templateFileDescriptor = template.getTemplateFileDescriptor();
        if (templateFileDescriptor == null)
            throw new ReportingException("No file descriptor for template: " + template.getCode());

        String extension = templateFileDescriptor.getExtension();
        if (StringUtils.isNotEmpty(extension)) {
            ReportFileExtension reportExt = ReportFileExtension.fromId(extension.toLowerCase());
            if (reportExt != null) {
                switch (reportExt) {
                    case DOC:
                    case ODT:
                        reportEngine = new DocFormatter();
                        break;

                    case HTML:
                    case HTM:
                        reportEngine = new HtmlFormatter();
                        break;

                    case XLS:
                    case XLT:
                        reportEngine = new XLSFormatter();
                        break;
                }
            } else
                throw new UnsupportedFormatException();
        } else
            throw new UnsupportedFormatException();

        return reportEngine;
    }

    private Band createRootBand(BandDefinition rootBandDefinition) {
        Band rootBand = new Band(rootBandDefinition.getName(), 1, null, rootBandDefinition.getOrientation());
        List<Map<String, Object>> data = getBandData(rootBandDefinition, null);
        if (data.size() > 0)
            rootBand.setData(data.get(0));
        return rootBand;
    }

    private List<Map<String, Object>> getBandData(BandDefinition definition, @Nullable Band parentBand) {
        List<DataSet> dataSets = definition.getDataSets();
        //add input params to band
        if (dataSets == null || dataSets.size() == 0)
            return Collections.singletonList(params.get());

        DataSet firstDataSet = dataSets.get(0);

        Map<String, Object> paramsMap = params.get();

        List<Map<String, Object>> result;

        //gets data from first dataset
        result = getDataSetData(parentBand, firstDataSet, paramsMap);

        //adds data from second and following datasets to result
        for (int i = 1; i < dataSets.size(); i++) {
            List<Map<String, Object>> dataSetData = getDataSetData(parentBand, dataSets.get(i), paramsMap);
            for (int j = 0; (j < result.size()) && (j < dataSetData.size()); j++) {
                result.get(j).putAll(dataSetData.get(j));
            }
        }

        if (result != null)
            //add output params to band
            for (Map<String, Object> map : result) {
                map.putAll(params.get());
            }

        return result;
    }

    private List<Map<String, Object>> getDataSetData(Band parentBand, DataSet dataSet, Map<String, Object> paramsMap) {
        List<Map<String, Object>> result = null;
        DataSetType dataSetType = dataSet.getType();

        if (StringUtils.isBlank(dataSet.getText())
                && ((dataSet.getType() != DataSetType.SINGLE) || (dataSet.getType() == DataSetType.MULTI)))
            throw new ReportingException("Please specify code for dataset: " + dataSet.getName());

        DataLoader loader = null;
        if (DataSetType.SQL.equals(dataSetType)) {
            loader = new SqlDataDataLoader(paramsMap);
        } else if (DataSetType.GROOVY.equals(dataSetType)) {
            loader = new GroovyDataLoader(paramsMap);
        } else if (DataSetType.JPQL.equals(dataSetType)) {
            loader = new JpqlDataDataLoader(paramsMap);
        } else if (DataSetType.SINGLE.equals(dataSetType)) {
            loader = new SingleEntityDataLoader(paramsMap);
        } else if (DataSetType.MULTI.equals(dataSetType)) {
            loader = new MultiEntityDataLoader(paramsMap);
        }

        if (loader != null)
            result = loader.loadData(dataSet, parentBand);

        return result;
    }

    /**
     * Create band from band definition
     * Perform query from definition and create band from each result row. Do it recursive down
     *
     * @param definition Band definition
     * @param parentBand Parent band
     * @return Data bands
     */
    private List<Band> createBands(BandDefinition definition, Band parentBand) {
        definition = reloadEntity(definition, REPORT_EDIT_VIEW_NAME);
        List<Map<String, Object>> outputData = getBandData(definition, parentBand);
        return createBandsList(definition, parentBand, outputData);
    }

    private List<Band> createBandsList(BandDefinition definition, Band parentBand, List<Map<String, Object>> outputData) {
        List<Band> bandsList = new ArrayList<>();
        for (Map<String, Object> data : outputData) {
            Band band = new Band(definition.getName(), parentBand.getLevel() + 1, parentBand, definition.getOrientation());
            band.setData(data);
            List<BandDefinition> childrenBandDefinitions = definition.getChildrenBandDefinitions();
            for (BandDefinition childDefinition : childrenBandDefinitions) {
                bandDefinitionNames.get().add(definition.getName());
                List<Band> childBands = createBands(childDefinition, band);
                band.addChildren(childBands);
            }
            bandsList.add(band);
        }
        return bandsList;
    }

    private <T extends Entity> T reloadEntity(T entity, String viewName) {
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
}