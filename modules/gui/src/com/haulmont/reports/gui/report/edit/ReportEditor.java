/*
 * Copyright (c) 2008-2013 Haulmont. All rights reserved.
 * Use is subject to license terms, see http://www.cuba-platform.com/license for details.
 */
package com.haulmont.reports.gui.report.edit;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.haulmont.chile.core.model.MetaClass;
import com.haulmont.chile.core.model.MetaPropertyPath;
import com.haulmont.cuba.core.app.FileStorageService;
import com.haulmont.cuba.core.entity.Entity;
import com.haulmont.cuba.core.global.*;
import com.haulmont.cuba.gui.AppConfig;
import com.haulmont.cuba.gui.ScreensHelper;
import com.haulmont.cuba.gui.WindowManager.OpenType;
import com.haulmont.cuba.gui.app.core.file.FileUploadDialog;
import com.haulmont.cuba.gui.components.*;
import com.haulmont.cuba.gui.components.actions.*;
import com.haulmont.cuba.gui.config.WindowConfig;
import com.haulmont.cuba.gui.config.WindowInfo;
import com.haulmont.cuba.gui.data.CollectionDatasource;
import com.haulmont.cuba.gui.data.Datasource;
import com.haulmont.cuba.gui.data.DsContext;
import com.haulmont.cuba.gui.data.HierarchicalDatasource;
import com.haulmont.cuba.gui.data.impl.*;
import com.haulmont.cuba.gui.export.ByteArrayDataProvider;
import com.haulmont.cuba.gui.export.ExportDisplay;
import com.haulmont.cuba.gui.export.ExportFormat;
import com.haulmont.cuba.gui.upload.FileUploadingAPI;
import com.haulmont.cuba.gui.xml.layout.ComponentsFactory;
import com.haulmont.cuba.security.entity.Role;
import com.haulmont.reports.app.service.ReportService;
import com.haulmont.reports.entity.*;
import com.haulmont.reports.gui.ReportPrintHelper;
import com.haulmont.reports.gui.definition.edit.BandDefinitionEditor;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.ObjectUtils;
import org.apache.commons.lang.StringUtils;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * @author degtyarjov
 * @version $Id$
 */
public class ReportEditor extends AbstractEditor<Report> {

    @Named("generalFrame.propertiesFieldGroup")
    protected FieldGroup propertiesFieldGroup;

    @Named("generalFrame.bandEditor")
    protected BandDefinitionEditor bandEditor;

    @Named("generalFrame.bandEditor.parentBand")
    protected LookupField parentBand;

    @Named("securityFrame.screenIdLookup")
    protected LookupField screenIdLookup;

    @Named("securityFrame.screenTable")
    protected Table screenTable;

    @Named("templatesFrame.templatesTable")
    protected Table templatesTable;

    @Named("run")
    protected Button run;

    @Named("generalFrame.createBandDefinition")
    protected Button createBandDefinitionButton;

    @Named("generalFrame.removeBandDefinition")
    protected Button removeBandDefinitionButton;

    @Named("generalFrame.up")
    protected Button bandUpButton;

    @Named("generalFrame.down")
    protected Button bandDownButton;

    @Named("securityFrame.addReportScreenBtn")
    protected Button addReportScreenBtn;

    @Named("securityFrame.addRoleBtn")
    protected Button addRoleBtn;

    @Named("securityFrame.rolesTable")
    protected Table rolesTable;

    @Named("parametersFrame.inputParametersTable")
    protected Table parametersTable;

    @Named("formatsFrame.valuesFormatsTable")
    protected Table formatsTable;

    @Named("parametersFrame.up")
    protected Button paramUpButton;

    @Named("parametersFrame.down")
    protected Button paramDownButton;

    @Named("generalFrame.serviceTree")
    protected Tree bandTree;

    @Inject
    protected WindowConfig windowConfig;

    @Inject
    protected Datasource<Report> reportDs;

    @Inject
    protected CollectionDatasource<ReportGroup, UUID> groupsDs;

    @Inject
    protected CollectionDatasource.Sortable<ReportInputParameter, UUID> parametersDs;

    @Inject
    protected CollectionDatasource<ReportScreen, UUID> reportScreensDs;

    @Inject
    protected CollectionDatasource<Role, UUID> rolesDs;

    @Inject
    protected CollectionDatasource<Role, UUID> lookupRolesDs;

    @Inject
    protected CollectionDatasource<DataSet, UUID> dataSetsDs;

    @Inject
    protected HierarchicalDatasource<BandDefinition, UUID> treeDs;

    @Inject
    protected CollectionDatasource<ReportTemplate, UUID> templatesDs;

    @Inject
    protected FileStorageService fileStorageService;

    @Inject
    protected ComponentsFactory componentsFactory;

    @Inject
    protected FileUploadingAPI fileUpload;

    @Inject
    protected ReportService reportService;

    @Inject
    protected CollectionDatasource<BandDefinition, UUID> bandsDs;

    @Inject
    protected CollectionDatasource<BandDefinition, UUID> availableParentBandsDs;

    @Inject
    protected ScreensHelper screensHelper;

    @Override
    protected void initNewItem(Report report) {
        report.setReportType(ReportType.SIMPLE);

        BandDefinition rootDefinition = new BandDefinition();
        rootDefinition.setName("Root");
        rootDefinition.setPosition(0);
        report.setBands(new HashSet<BandDefinition>());
        report.getBands().add(rootDefinition);

        rootDefinition.setReport(report);

        groupsDs.refresh();
        if (groupsDs.getItemIds() != null) {
            UUID id = groupsDs.getItemIds().iterator().next();
            report.setGroup(groupsDs.getItem(id));
        }
    }

    @Override
    protected void postInit() {
        if (!StringUtils.isEmpty(getItem().getName())) {
            setCaption(AppBeans.get(Messages.class).formatMessage(getClass(), "reportEditor.format", getItem().getName()));
        }

        ((CollectionPropertyDatasourceImpl) treeDs).setModified(false);
        ((DatasourceImpl) reportDs).setModified(false);

        bandTree.getDatasource().refresh();
        bandTree.expandTree();
        bandTree.setSelected(reportDs.getItem().getRootBandDefinition());

        bandEditor.setBandDefinition(bandTree.<BandDefinition>getSingleSelected());
        if (bandTree.getSingleSelected() == null) {
            bandEditor.setEnabled(false);
        }
    }

    @Override
    public void init(Map<String, Object> params) {
        super.init(params);
        initGeneral();
        initTemplates();
        initParameters();
        initRoles();
        initScreens();
        initValuesFormats();
    }

    protected void initParameters() {
        parametersTable.addAction(
                new CreateAction(parametersTable, OpenType.DIALOG) {
                    @Override
                    public Map<String, Object> getInitialValues() {
                        Map<String, Object> params = new HashMap<>();
                        params.put("position", parametersDs.getItemIds().size());
                        params.put("report", getItem());
                        return params;
                    }

                    @Override
                    public void actionPerform(Component component) {
                        orderParameters();
                        super.actionPerform(component);
                    }
                }
        );

        parametersTable.addAction(new RemoveAction(parametersTable, false) {
            @Override
            protected void afterRemove(Set selected) {
                super.afterRemove(selected);
                orderParameters();
            }
        });
        parametersTable.addAction(new EditAction(parametersTable, OpenType.DIALOG));

        paramUpButton.setAction(new BaseAction("generalFrame.up") {
            @Override
            public void actionPerform(Component component) {
                ReportInputParameter parameter = target.getSingleSelected();
                if (parameter != null) {
                    List<ReportInputParameter> inputParameters = getItem().getInputParameters();
                    int index = parameter.getPosition();
                    if (index > 0) {
                        ReportInputParameter previousParameter = null;
                        for (ReportInputParameter _param : inputParameters) {
                            if (_param.getPosition() == index - 1) {
                                previousParameter = _param;
                                break;
                            }
                        }
                        if (previousParameter != null) {
                            parameter.setPosition(previousParameter.getPosition());
                            previousParameter.setPosition(index);

                            sortParametersByPosition();
                        }
                    }
                }
            }

            @Override
            protected boolean isApplicable() {
                if (target != null) {
                    ReportInputParameter item = target.getSingleSelected();
                    if (item != null && parametersDs.getItem() == item) {
                        return item.getPosition() > 0;
                    }
                }

                return false;
            }
        });

        paramDownButton.setAction(new BaseAction("generalFrame.down") {
            @Override
            public void actionPerform(Component component) {
                ReportInputParameter parameter = target.getSingleSelected();
                if (parameter != null) {
                    List<ReportInputParameter> inputParameters = getItem().getInputParameters();
                    int index = parameter.getPosition();
                    if (index < parametersDs.getItemIds().size() - 1) {
                        ReportInputParameter nextParameter = null;
                        for (ReportInputParameter _param : inputParameters) {
                            if (_param.getPosition() == index + 1) {
                                nextParameter = _param;
                                break;
                            }
                        }
                        if (nextParameter != null) {
                            parameter.setPosition(nextParameter.getPosition());
                            nextParameter.setPosition(index);

                            sortParametersByPosition();
                        }
                    }
                }
            }

            @Override
            protected boolean isApplicable() {
                if (target != null) {
                    ReportInputParameter item = target.getSingleSelected();
                    if (item != null && parametersDs.getItem() == item) {
                        return item.getPosition() < parametersDs.size() - 1;
                    }
                }

                return false;
            }
        });

        parametersTable.addAction(paramUpButton.getAction());
        parametersTable.addAction(paramDownButton.getAction());

        parametersDs.addListener(new DsListenerAdapter<ReportInputParameter>() {
            @Override
            public void valueChanged(ReportInputParameter source, String property, Object prevValue, Object value) {
                if ("position".equals(property)) {
                    ((DatasourceImplementation) parametersDs).modified(source);
                }
            }
        });
    }

    protected void sortParametersByPosition() {
        final MetaClass metaClass = AppBeans.get(Metadata.class).getSession().getClass(ReportInputParameter.class);
        final MetaPropertyPath mpp = new MetaPropertyPath(metaClass, metaClass.getProperty("position"));

        CollectionDatasource.Sortable.SortInfo sortInfo = new CollectionDatasource.Sortable.SortInfo();
        sortInfo.setOrder(CollectionDatasource.Sortable.Order.ASC);
        sortInfo.setPropertyPath(mpp);

        parametersDs.sort(new CollectionDatasource.Sortable.SortInfo[]{sortInfo});
    }

    protected void initValuesFormats() {
        formatsTable.addAction(
                new CreateAction(formatsTable, OpenType.DIALOG) {
                    @Override
                    public Map<String, Object> getInitialValues() {
                        return Collections.<String, Object>singletonMap("report", getItem());
                    }
                }
        );
        formatsTable.addAction(new RemoveAction(formatsTable, false));
        formatsTable.addAction(new EditAction(formatsTable, OpenType.DIALOG));
    }

    protected void initRoles() {
        rolesTable.addAction(new ExcludeAction(rolesTable, false, true));

        addRoleBtn.setAction(new AbstractAction("actions.Add") {
            @Override
            public void actionPerform(Component component) {
                if (lookupRolesDs.getItem() != null && !rolesDs.containsItem(lookupRolesDs.getItem().getId())) {
                    rolesDs.addItem(lookupRolesDs.getItem());
                }
            }
        });
    }

    protected void initScreens() {
        screenTable.addAction(new RemoveAction(screenTable, false));
        List<WindowInfo> windowInfoCollection = new ArrayList<>(windowConfig.getWindows());
        // sort by screenId
        screensHelper.sortWindowInfos(windowInfoCollection);

        Map<String, Object> screens = new LinkedHashMap<>();
        for (WindowInfo windowInfo : windowInfoCollection) {
            String id = windowInfo.getId();
            String menuId = "menu-config." + id;
            String localeMsg = AppBeans.get(Messages.class).getMessage(AppConfig.getMessagesPack(), menuId);
            String title = menuId.equals(localeMsg) ? id : id + " ( " + localeMsg + " )";
            screens.put(title, id);
        }
        screenIdLookup.setOptionsMap(screens);

        addReportScreenBtn.setAction(new AbstractAction("actions.Add") {
            @Override
            public void actionPerform(Component component) {
                if (screenIdLookup.getValue() != null) {
                    String screenId = screenIdLookup.getValue();

                    boolean exists = false;
                    for (UUID id : reportScreensDs.getItemIds()) {
                        ReportScreen item = reportScreensDs.getItem(id);
                        if (screenId.equalsIgnoreCase(item.getScreenId())) {
                            exists = true;
                            break;
                        }
                    }

                    if (!exists) {
                        ReportScreen reportScreen = new ReportScreen();
                        reportScreen.setReport(getItem());
                        reportScreen.setScreenId(screenId);
                        reportScreensDs.addItem(reportScreen);
                    }
                }
            }
        });
    }

    private boolean isChildOrEqual(BandDefinition definition, BandDefinition child) {
        if (definition.equals(child)) {
            return true;
        } else if (child != null) {
            return isChildOrEqual(definition, child.getParentBandDefinition());
        } else {
            return false;
        }
    }

    protected void initGeneral() {
        treeDs.addListener(new DsListenerAdapter<BandDefinition>() {
            @Override
            public void itemChanged(Datasource<BandDefinition> ds, BandDefinition prevItem, BandDefinition item) {
                bandEditor.setBandDefinition(item);
                bandEditor.setEnabled(item != null);
                availableParentBandsDs.clear();
                if (item != null) {
                    for (BandDefinition bandDefinition : bandsDs.getItems()) {
                        if (!isChildOrEqual(item, bandDefinition) ||
                                ObjectUtils.equals(item.getParentBandDefinition(), bandDefinition)) {
                            availableParentBandsDs.addItem(bandDefinition);
                        }
                    }
                }
            }
        });

        bandEditor.getBandDefinitionDs().addListener(new DsListenerAdapter<BandDefinition>() {
            @Override
            public void valueChanged(BandDefinition source, String property, Object prevValue, Object value) {
                if ("parentBandDefinition".equals(property)) {
                    BandDefinition previousParent = (BandDefinition) prevValue;
                    BandDefinition parent = (BandDefinition) value;

                    if (value == source) {
                        source.setParentBandDefinition(previousParent);
                    } else {
                        treeDs.refresh();
                        previousParent.getChildrenBandDefinitions().remove(source);
                        parent.getChildrenBandDefinitions().add(source);
                    }

                    if (prevValue != null) {
                        orderBandDefinitions(previousParent);
                    }

                    if (value != null) {
                        orderBandDefinitions(parent);
                    }
                }
                treeDs.modifyItem(source);
            }
        });

        propertiesFieldGroup.addCustomField("defaultTemplate", new FieldGroup.CustomFieldGenerator() {
            @Override
            public Component generateField(Datasource datasource, String propertyId) {
                final LookupPickerField lookupPickerField = componentsFactory.createComponent(LookupPickerField.NAME);

                lookupPickerField.setOptionsDatasource(templatesDs);
                lookupPickerField.setDatasource(datasource, propertyId);

                lookupPickerField.addAction(new AbstractAction("download") {

                    @Override
                    public String getDescription() {
                        return getMessage("description.downloadTemplate");
                    }

                    @Override
                    public String getCaption() {
                        return null;
                    }

                    @Override
                    public String getIcon() {
                        return "icons/reports-template-download.png";
                    }

                    @Override
                    public void actionPerform(Component component) {
                        ReportTemplate defaultTemplate = getItem().getDefaultTemplate();
                        if (defaultTemplate != null) {
                            if (defaultTemplate.isCustom()) {
                                showNotification(getMessage("unableToSaveTemplateWhichDefinedWithClass"), NotificationType.WARNING);
                            } else {
                                ExportDisplay exportDisplay = AppConfig.createExportDisplay(ReportEditor.this);
                                byte[] reportTemplate = defaultTemplate.getContent();
                                exportDisplay.show(new ByteArrayDataProvider(reportTemplate),
                                        defaultTemplate.getName(), ExportFormat.getByExtension(defaultTemplate.getExt()));
                            }
                        } else {
                            showNotification(getMessage("notification.defaultTemplateIsEmpty"), NotificationType.HUMANIZED);
                        }

                        lookupPickerField.requestFocus();
                    }
                });

                lookupPickerField.addAction(new AbstractAction("upload") {
                    @Override
                    public String getDescription() {
                        return getMessage("description.uploadTemplate");
                    }

                    @Override
                    public String getCaption() {
                        return null;
                    }

                    @Override
                    public String getIcon() {
                        return "icons/reports-template-upload.png";
                    }

                    @Override
                    public void actionPerform(Component component) {
                        final ReportTemplate defaultTemplate = getItem().getDefaultTemplate();
                        if (defaultTemplate != null) {
                            final FileUploadDialog dialog = openWindow("fileUploadDialog", OpenType.DIALOG);
                            dialog.addListener(new CloseListener() {
                                @Override
                                public void windowClosed(String actionId) {
                                    if (Window.COMMIT_ACTION_ID.equals(actionId)) {
                                        File file = fileUpload.getFile(dialog.getFileId());
                                        try {
                                            byte[] data = FileUtils.readFileToByteArray(file);
                                            defaultTemplate.setContent(data);
                                            defaultTemplate.setName(dialog.getFileName());
                                            templatesDs.modifyItem(defaultTemplate);
                                        } catch (IOException e) {
                                            throw new RuntimeException(String.format(
                                                    "An error occurred while uploading file for template [%s]",
                                                    defaultTemplate.getCode()));
                                        }
                                    }
                                    lookupPickerField.requestFocus();
                                }
                            });
                        } else {
                            showNotification(getMessage("notification.defaultTemplateIsEmpty"), NotificationType.HUMANIZED);
                        }
                    }
                });

                lookupPickerField.addAction(new AbstractAction("create") {

                    @Override
                    public String getDescription() {
                        return getMessage("description.createTemplate");
                    }

                    @Override
                    public String getIcon() {
                        return "icons/plus-btn.png";
                    }

                    @Override
                    public void actionPerform(Component component) {
                        ReportTemplate template = new ReportTemplate();
                        template.setReport(getItem());
                        final Editor editor = openEditor("report$ReportTemplate.edit",
                                template, OpenType.DIALOG, templatesDs);
                        editor.addListener(new CloseListener() {
                            @Override
                            public void windowClosed(String actionId) {
                                if (Window.COMMIT_ACTION_ID.equals(actionId)) {
                                    ReportTemplate item = (ReportTemplate) editor.getItem();
                                    templatesDs.addItem(item);
                                    getItem().setDefaultTemplate(item);
                                    //Workaround to disable button after default template setting
                                    Action defaultTemplate = templatesTable.getAction("defaultTemplate");
                                    defaultTemplate.refreshState();
                                }
                                lookupPickerField.requestFocus();
                            }
                        });
                    }
                });

                lookupPickerField.addAction(new AbstractAction("edit") {
                    @Override
                    public String getDescription() {
                        return getMessage("description.editTemplate");
                    }

                    @Override
                    public String getIcon() {
                        return "icons/reports-template-view.png";
                    }

                    @Override
                    public void actionPerform(Component component) {
                        ReportTemplate defaultTemplate = getItem().getDefaultTemplate();
                        if (defaultTemplate != null) {
                            final Editor editor = openEditor("report$ReportTemplate.edit",
                                    defaultTemplate, OpenType.DIALOG, templatesDs);
                            editor.addListener(new CloseListener() {
                                @Override
                                public void windowClosed(String actionId) {
                                    if (Window.COMMIT_ACTION_ID.equals(actionId)) {
                                        ReportTemplate item = (ReportTemplate) editor.getItem();
                                        getItem().setDefaultTemplate(item);
                                        templatesDs.modifyItem(item);
                                    }
                                    lookupPickerField.requestFocus();
                                }
                            });
                        } else {
                            showNotification(getMessage("notification.defaultTemplateIsEmpty"), NotificationType.HUMANIZED);
                        }
                    }
                });

                return lookupPickerField;
            }
        });


        ((HierarchicalPropertyDatasourceImpl) treeDs).setSortPropertyName("position");

        createBandDefinitionButton.setAction(new AbstractAction("create") {
            @Override
            public String getDescription() {
                return getMessage("description.createBand");
            }

            @Override
            public String getCaption() {
                return "";
            }

            @Override
            public void actionPerform(Component component) {
                BandDefinition parentDefinition = treeDs.getItem();
                Report report = getItem();
                // Use root band as parent if no items selected
                if (parentDefinition == null) {
                    parentDefinition = report.getRootBandDefinition();
                }
                if (parentDefinition.getChildrenBandDefinitions() == null) {
                    parentDefinition.setChildrenBandDefinitions(new ArrayList<BandDefinition>());
                }

                //
                orderBandDefinitions(parentDefinition);

                BandDefinition newBandDefinition = new BandDefinition();
                newBandDefinition.setName("newBand" + (parentDefinition.getChildrenBandDefinitions().size() + 1));
                newBandDefinition.setOrientation(Orientation.HORIZONTAL);
                newBandDefinition.setParentBandDefinition(parentDefinition);
                if (parentDefinition.getChildrenBandDefinitions() != null) {
                    newBandDefinition.setPosition(parentDefinition.getChildrenBandDefinitions().size());
                } else {
                    newBandDefinition.setPosition(0);
                }
                newBandDefinition.setReport(report);
                parentDefinition.getChildrenBandDefinitions().add(newBandDefinition);

                treeDs.addItem(newBandDefinition);

                treeDs.refresh();
                bandTree.expandTree();
                bandTree.setSelected(newBandDefinition);//let's try and see if it increases usability

                bandTree.requestFocus();
            }
        });

        removeBandDefinitionButton.setAction(new RemoveAction(bandTree, false, "generalFrame.removeBandDefinition") {
            @Override
            public String getDescription() {
                return getMessage("description.removeBand");
            }

            @Override
            public String getCaption() {
                return "";
            }

            @Override
            protected boolean isApplicable() {
                if (target != null) {
                    Entity selectedItem = target.getSingleSelected();
                    if (selectedItem != null) {
                        return !ObjectUtils.equals(getItem().getRootBandDefinition(), selectedItem);
                    }
                }

                return false;
            }

            @Override
            protected void doRemove(Set selected, boolean autocommit) {
                if (selected != null) {
                    removeChildrenCascade(selected);
                    for (Object object : selected) {
                        BandDefinition definition = (BandDefinition) object;
                        if (definition.getParentBandDefinition() != null) {
                            orderBandDefinitions(((BandDefinition) object).getParentBandDefinition());
                        }
                    }
                }
                bandTree.requestFocus();
            }

            private void removeChildrenCascade(Collection selected) {
                for (Object o : selected) {
                    BandDefinition definition = (BandDefinition) o;
                    BandDefinition parentDefinition = definition.getParentBandDefinition();
                    if (parentDefinition != null) {
                        definition.getParentBandDefinition().getChildrenBandDefinitions().remove(definition);
                    }

                    if (definition.getChildrenBandDefinitions() != null) {
                        removeChildrenCascade(new ArrayList<>(definition.getChildrenBandDefinitions()));
                    }

                    if (definition.getDataSets() != null) {
                        treeDs.setItem(definition);
                        for (DataSet dataSet : new ArrayList<>(definition.getDataSets())) {
                            if (PersistenceHelper.isNew(dataSet)) {
                                dataSetsDs.removeItem(dataSet);
                            }
                        }
                    }
                    treeDs.removeItem(definition);
                }
            }
        });

        bandUpButton.setAction(new BaseAction("generalFrame.up") {
            @Override
            public String getDescription() {
                return getMessage("description.moveUp");
            }

            @Override
            public String getCaption() {
                return "";
            }

            @Override
            public void actionPerform(Component component) {
                BandDefinition definition = target.getSingleSelected();
                if (definition != null && definition.getParentBandDefinition() != null) {
                    BandDefinition parentDefinition = definition.getParentBandDefinition();
                    List<BandDefinition> definitionsList = parentDefinition.getChildrenBandDefinitions();
                    int index = definitionsList.indexOf(definition);
                    if (index > 0) {
                        BandDefinition previousDefinition = definitionsList.get(index - 1);
                        definition.setPosition(definition.getPosition() - 1);
                        previousDefinition.setPosition(previousDefinition.getPosition() + 1);

                        definitionsList.set(index, previousDefinition);
                        definitionsList.set(index - 1, definition);

                        treeDs.refresh();
                    }
                }
            }

            @Override
            protected boolean isApplicable() {
                if (target != null) {
                    BandDefinition selectedItem = target.getSingleSelected();
                    return selectedItem != null && selectedItem.getPosition() > 0;
                }

                return false;
            }
        });

        bandDownButton.setAction(new BaseAction("generalFrame.down") {
            @Override
            public String getDescription() {
                return getMessage("description.moveDown");
            }

            @Override
            public String getCaption() {
                return "";
            }

            @Override
            public void actionPerform(Component component) {
                BandDefinition definition = target.getSingleSelected();
                if (definition != null && definition.getParentBandDefinition() != null) {
                    BandDefinition parentDefinition = definition.getParentBandDefinition();
                    List<BandDefinition> definitionsList = parentDefinition.getChildrenBandDefinitions();
                    int index = definitionsList.indexOf(definition);
                    if (index < definitionsList.size() - 1) {
                        BandDefinition nextDefinition = definitionsList.get(index + 1);
                        definition.setPosition(definition.getPosition() + 1);
                        nextDefinition.setPosition(nextDefinition.getPosition() - 1);

                        definitionsList.set(index, nextDefinition);
                        definitionsList.set(index + 1, definition);

                        treeDs.refresh();
                    }
                }
            }

            @Override
            protected boolean isApplicable() {
                if (target != null) {
                    BandDefinition bandDefinition = target.getSingleSelected();
                    if (bandDefinition != null) {
                        BandDefinition parent = bandDefinition.getParentBandDefinition();
                        return parent != null &&
                                parent.getChildrenBandDefinitions() != null &&
                                bandDefinition.getPosition() < parent.getChildrenBandDefinitions().size() - 1;
                    }
                }
                return false;
            }
        });

        bandTree.addAction(createBandDefinitionButton.getAction());
        bandTree.addAction(removeBandDefinitionButton.getAction());
        bandTree.addAction(bandUpButton.getAction());
        bandTree.addAction(bandDownButton.getAction());

        run.setAction(new AbstractAction("button.run") {
            @Override
            public void actionPerform(Component component) {
                if (validateAll()) {
                    getItem().setIsTmp(true);
                    Window runWindow = openWindow("report$inputParameters", OpenType.DIALOG,
                            Collections.<String, Object>singletonMap("report", getItem()));
                    runWindow.addListener(new CloseListener() {
                        @Override
                        public void windowClosed(String actionId) {
                            bandTree.requestFocus();
                        }
                    });
                }
            }
        });
    }

    @Override
    public boolean validateAll() {
        return super.validateAll() && validateInputOutputFormats();
    }

    protected boolean validateInputOutputFormats() {
        ReportTemplate template = getItem().getDefaultTemplate();
        if (template != null && !template.isCustom() && template.getReportOutputType() != ReportOutputType.CHART) {
            String inputType = template.getExt();
            if (!ReportPrintHelper.getInputOutputTypesMapping().containsKey(inputType) ||
                    !ReportPrintHelper.getInputOutputTypesMapping().get(inputType).contains(template.getReportOutputType())) {
                showNotification(getMessage("inputOutputTypesError"), NotificationType.TRAY);
                return false;
            }
        }
        return true;
    }

    protected void initTemplates() {
        templatesTable.addAction(new CreateAction(templatesTable, OpenType.DIALOG) {
            @Override
            public Map<String, Object> getInitialValues() {
                return Collections.<String, Object>singletonMap("report", getItem());
            }
        });

        templatesTable.addAction(new EditAction(templatesTable, OpenType.DIALOG){
            @Override
            protected void afterCommit(Entity entity) {
                ReportTemplate reportTemplate = (ReportTemplate) entity;
                ReportTemplate defaultTemplate = getItem().getDefaultTemplate();
                if (defaultTemplate != null && defaultTemplate.equals(reportTemplate)) {
                    getItem().setDefaultTemplate(reportTemplate);
                }
            }
        });

        templatesTable.addAction(new RemoveAction(templatesTable, false) {
            @Override
            protected void afterRemove(Set selected) {
                super.afterRemove(selected);

                Report report = getItem();
                ReportTemplate defaultTemplate = report.getDefaultTemplate();
                if (defaultTemplate != null && selected.contains(defaultTemplate)) {
                    report.setDefaultTemplate(null);
                }
            }
        });

        templatesTable.addAction(new BaseAction("defaultTemplate") {
            @Override
            public String getCaption() {
                return getMessage("report.defaultTemplate");
            }

            @Override
            public void actionPerform(Component component) {
                ReportTemplate template = target.getSingleSelected();
                if (template != null) {
                    getItem().setDefaultTemplate(template);
                }

                refreshState();
                templatesTable.requestFocus();
            }

            @Override
            protected boolean isApplicable() {
                if (target != null) {
                    Entity selectedItem = target.getSingleSelected();
                    if (selectedItem != null) {
                        return !ObjectUtils.equals(getItem().getDefaultTemplate(), selectedItem);
                    }
                }

                return false;
            }
        });
    }

    protected void orderParameters() {
        if (getItem().getInputParameters() == null) {
            getItem().setInputParameters(new ArrayList<ReportInputParameter>());
        }

        for (int i = 0; i < getItem().getInputParameters().size(); i++) {
            getItem().getInputParameters().get(i).setPosition(i);
        }
    }

    protected void orderBandDefinitions(BandDefinition parent) {
        if (parent.getChildrenBandDefinitions() != null) {
            List<BandDefinition> childrenBandDefinitions = parent.getChildrenBandDefinitions();
            for (int i = 0, childrenBandDefinitionsSize = childrenBandDefinitions.size(); i < childrenBandDefinitionsSize; i++) {
                BandDefinition bandDefinition = childrenBandDefinitions.get(i);
                bandDefinition.setPosition(i);
            }
        }
    }

    @Override
    protected boolean preCommit() {
        addCommitListeners();

        if (PersistenceHelper.isNew(getItem())) {
            ((CollectionPropertyDatasourceImpl) treeDs).setModified(true);
        }

        return true;
    }

    protected void addCommitListeners() {
        String xml = reportService.convertToXml(getItem());
        getItem().setXml(xml);

        reportDs.getDsContext().addListener(new DsContext.CommitListener() {
            @Override
            public void beforeCommit(CommitContext context) {
                for (Iterator<Entity> iterator = context.getCommitInstances().iterator(); iterator.hasNext(); ) {
                    Entity entity = iterator.next();
                    if (!(entity instanceof Report || entity instanceof ReportTemplate)) {
                        iterator.remove();
                    }
                }
            }

            @Override
            public void afterCommit(CommitContext context, Set<Entity> result) {
            }
        });
    }

    @Override
    protected void postValidate(ValidationErrors errors) {
        if (getItem().getRootBand() == null) {
            errors.add(getMessage("error.rootBandNull"));
        }

        if (CollectionUtils.isNotEmpty(getItem().getRootBandDefinition().getChildrenBandDefinitions())) {
            Multimap<String, BandDefinition> names = ArrayListMultimap.create();
            names.put(getItem().getRootBand().getName(), getItem().getRootBandDefinition());

            for (BandDefinition band : getItem().getRootBandDefinition().getChildrenBandDefinitions()) {
                validateBand(errors, band, names);
            }

            checkForNameDuplication(errors, names);
        }
    }

    protected void checkForNameDuplication(ValidationErrors errors, Multimap<String, BandDefinition> names) {
        for (String name : names.keySet()) {
            Collection<BandDefinition> bandDefinitionsWithsSameNames = names.get(name);
            if (bandDefinitionsWithsSameNames != null && bandDefinitionsWithsSameNames.size() > 1) {
                errors.add(formatMessage("error.bandNamesDuplicated", name));
            }
        }
    }

    protected void validateBand(ValidationErrors errors, BandDefinition band, Multimap<String, BandDefinition> names) {
        names.put(band.getName(), band);

        if (StringUtils.isBlank(band.getName())) {
            errors.add(getMessage("error.bandNameNull"));
        }

        if (band.getBandOrientation() == null) {
            errors.add(formatMessage("error.bandOrientationNull", band.getName()));
        }

        if (CollectionUtils.isNotEmpty(band.getDataSets())) {
            for (DataSet dataSet : band.getDataSets()) {
                if (StringUtils.isBlank(dataSet.getName())) {
                    errors.add(getMessage("error.dataSetNameNull"));
                }

                if (dataSet.getType() == null) {
                    errors.add(formatMessage("error.dataSetTypeNull", dataSet.getName()));
                }

                if (dataSet.getType() == DataSetType.GROOVY
                        || dataSet.getType() == DataSetType.SQL
                        || dataSet.getType() == DataSetType.JPQL) {
                    if (StringUtils.isBlank(dataSet.getScript())) {
                        errors.add(formatMessage("error.dataSetScriptNull", dataSet.getName()));
                    }
                }
            }
        }

        if (CollectionUtils.isNotEmpty(band.getChildrenBandDefinitions())) {
            for (BandDefinition child : band.getChildrenBandDefinitions()) {
                validateBand(errors, child, names);
            }
        }
    }
}