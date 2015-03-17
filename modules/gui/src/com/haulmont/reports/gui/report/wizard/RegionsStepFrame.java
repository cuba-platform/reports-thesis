/*
 * Copyright (c) 2008-2015 Haulmont. All rights reserved.
 * Use is subject to license terms, see http://www.cuba-platform.com/license for details.
 */

package com.haulmont.reports.gui.report.wizard;

import com.haulmont.cuba.gui.WindowManager;
import com.haulmont.cuba.gui.components.*;
import com.haulmont.reports.entity.wizard.EntityTreeNode;
import com.haulmont.reports.entity.wizard.RegionProperty;
import com.haulmont.reports.entity.wizard.ReportData;
import com.haulmont.reports.entity.wizard.ReportRegion;
import com.haulmont.reports.gui.components.actions.OrderableItemMoveAction;
import com.haulmont.reports.gui.report.wizard.step.StepFrame;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.Transformer;
import org.apache.commons.lang.StringUtils;

import java.util.*;

/**
 * @author degtyarjov
 * @version $Id$
 */
class RegionsStepFrame extends StepFrame {
    protected static final String ADD_TABULATED_REGION_ACTION_ID = "tabulatedRegion";
    protected static final String ADD_SIMPLE_REGION_ACTION_ID = "simpleRegion";
    protected AddSimpleRegionAction addSimpleRegionAction;
    protected AddTabulatedRegionAction addTabulatedRegionAction;
    protected EditRegionAction editRegionAction;
    protected RemoveRegionAction removeRegionAction;

    public RegionsStepFrame(ReportWizardCreator wizard) {
        super(wizard, wizard.getMessage("reportRegions"), "regionsStep");
        initFrameHandler = new InitRegionsStepFrameHandler();

        beforeShowFrameHandler = new BeforeShowRegionsStepFrameHandler();

        beforeHideFrameHandler = new BeforeHideRegionsStepFrameHandler();
    }

    protected abstract class AddRegionAction extends AbstractAction {

        protected AddRegionAction(String id) {
            super(id);
        }

        protected ReportRegion createReportRegion(boolean tabulated) {
            ReportRegion reportRegion = wizard.metadata.create(ReportRegion.class);
            reportRegion.setReportData(wizard.getItem());
            reportRegion.setIsTabulatedRegion(tabulated);
            reportRegion.setOrderNum((long) wizard.getItem().getReportRegions().size() + 1L);
            return reportRegion;
        }

        protected void openTabulatedRegionEditor(final ReportRegion item) {
            if (ReportData.ReportType.SINGLE_ENTITY == wizard.reportTypeOptionGroup.getValue()) {
                openRegionEditorOnlyWithNestedCollections(item);

            } else {
                openRegionEditor(item);
            }
        }

        private void openRegionEditorOnlyWithNestedCollections(final ReportRegion item) {//show lookup for choosing parent collection for tabulated region
            final Map<String, Object> lookupParams = new HashMap<>();
            lookupParams.put("rootEntity", wizard.getItem().getEntityTreeRootNode());
            lookupParams.put("collectionsOnly", Boolean.TRUE);
            lookupParams.put("persistentOnly", ReportData.ReportType.LIST_OF_ENTITIES_WITH_QUERY == wizard.reportTypeOptionGroup.getValue());
            wizard.openLookup("report$ReportEntityTree.lookup", new Window.Lookup.Handler() {
                @Override
                public void handleLookup(Collection items) {
                    if (items.size() == 1) {
                        EntityTreeNode regionPropertiesRootNode = (EntityTreeNode) CollectionUtils.get(items, 0);

                        Map<String, Object> editorParams = new HashMap<>();
                        editorParams.put("scalarOnly", Boolean.TRUE);
                        editorParams.put("persistentOnly", ReportData.ReportType.LIST_OF_ENTITIES_WITH_QUERY == wizard.reportTypeOptionGroup.getValue());
                        editorParams.put("rootEntity", regionPropertiesRootNode);
                        item.setRegionPropertiesRootNode(regionPropertiesRootNode);
                        Window.Editor regionEditor = wizard.openEditor("report$Report.regionEditor", item, WindowManager.OpenType.DIALOG, editorParams, wizard.reportRegionsDs);
                        regionEditor.addListener(new RegionEditorCloseListener());
                    }
                }
            }, WindowManager.OpenType.DIALOG, lookupParams);
        }

        protected void openRegionEditor(ReportRegion item) {
            item.setRegionPropertiesRootNode(wizard.getItem().getEntityTreeRootNode());

            Map<String, Object> editorParams = new HashMap<>();
            editorParams.put("rootEntity", wizard.getItem().getEntityTreeRootNode());
            editorParams.put("scalarOnly", Boolean.TRUE);
            editorParams.put("persistentOnly", ReportData.ReportType.LIST_OF_ENTITIES_WITH_QUERY == wizard.reportTypeOptionGroup.getValue());

            Window.Editor regionEditor = wizard.openEditor("report$Report.regionEditor", item, WindowManager.OpenType.DIALOG, editorParams, wizard.reportRegionsDs);
            regionEditor.addListener(new AddRegionAction.RegionEditorCloseListener());
        }


        protected class RegionEditorCloseListener implements Window.CloseListener {
            @Override
            public void windowClosed(String actionId) {
                if (Window.COMMIT_ACTION_ID.equals(actionId)) {
                    wizard.regionsTable.refresh();
                    wizard.setupButtonsVisibility();
                }
            }
        }

    }

    protected class AddSimpleRegionAction extends AddRegionAction {
        public AddSimpleRegionAction() {
            super(ADD_SIMPLE_REGION_ACTION_ID);
        }

        @Override
        public void actionPerform(Component component) {
            openRegionEditor(createReportRegion(false));
        }
    }

    protected class AddTabulatedRegionAction extends AddRegionAction {
        public AddTabulatedRegionAction() {
            super(ADD_TABULATED_REGION_ACTION_ID);
        }

        @Override
        public void actionPerform(Component component) {
            openTabulatedRegionEditor(createReportRegion(true));
        }
    }

    protected class ReportRegionTableColumnGenerator implements Table.ColumnGenerator<ReportRegion> {
        protected static final String WIDTH_PERCENT_100 = "100%";
        protected static final int MAX_ATTRS_BTN_CAPTION_WIDTH = 95;
        protected static final String BOLD_LABEL_STYLE = "semi-bold-label";
        private ReportRegion currentReportRegionGeneratedColumn;

        @Override
        public Component generateCell(ReportRegion entity) {
            currentReportRegionGeneratedColumn = entity;
            BoxLayout mainLayout = wizard.componentsFactory.createComponent(BoxLayout.VBOX);
            mainLayout.setWidth(WIDTH_PERCENT_100);
            mainLayout.add(createFirstTwoRowsLayout());
            mainLayout.add(createThirdRowAttrsLayout());
            return mainLayout;
        }

        private BoxLayout createFirstTwoRowsLayout() {
            BoxLayout firstTwoRowsLayout = wizard.componentsFactory.createComponent(BoxLayout.HBOX);
            BoxLayout expandedAttrsLayout = createExpandedAttrsLayout();
            firstTwoRowsLayout.setWidth(WIDTH_PERCENT_100);
            firstTwoRowsLayout.add(expandedAttrsLayout);
            firstTwoRowsLayout.add(createBtnsLayout());
            firstTwoRowsLayout.expand(expandedAttrsLayout);
            return firstTwoRowsLayout;
        }

        private BoxLayout createExpandedAttrsLayout() {
            BoxLayout expandedAttrsLayout = wizard.componentsFactory.createComponent(BoxLayout.VBOX);
            expandedAttrsLayout.setWidth(WIDTH_PERCENT_100);
            expandedAttrsLayout.add(createFirstRowAttrsLayout());
            expandedAttrsLayout.add(createSecondRowAttrsLayout());
            return expandedAttrsLayout;
        }

        private BoxLayout createFirstRowAttrsLayout() {
            BoxLayout firstRowAttrsLayout = wizard.componentsFactory.createComponent(BoxLayout.HBOX);
            firstRowAttrsLayout.setSpacing(true);
            Label regionLbl = wizard.componentsFactory.createComponent(Label.NAME);
            regionLbl.setStyleName(BOLD_LABEL_STYLE);
            regionLbl.setValue(wizard.getMessage("region"));
            Label regionValueLbl = wizard.componentsFactory.createComponent(Label.NAME);
            regionValueLbl.setValue(currentReportRegionGeneratedColumn.getName());
            regionValueLbl.setWidth(WIDTH_PERCENT_100);
            firstRowAttrsLayout.add(regionLbl);
            firstRowAttrsLayout.add(regionValueLbl);
            return firstRowAttrsLayout;
        }

        private BoxLayout createSecondRowAttrsLayout() {
            BoxLayout secondRowAttrsLayout = wizard.componentsFactory.createComponent(BoxLayout.HBOX);
            secondRowAttrsLayout.setSpacing(true);
            Label entityLbl = wizard.componentsFactory.createComponent(Label.NAME);
            entityLbl.setStyleName(BOLD_LABEL_STYLE);
            entityLbl.setValue(wizard.getMessage("entity"));
            Label entityValueLbl = wizard.componentsFactory.createComponent(Label.NAME);
            entityValueLbl.setValue(wizard.messageTools.getEntityCaption(currentReportRegionGeneratedColumn.getRegionPropertiesRootNode().getWrappedMetaClass()));
            entityValueLbl.setWidth(WIDTH_PERCENT_100);
            secondRowAttrsLayout.add(entityLbl);
            secondRowAttrsLayout.add(entityValueLbl);
            return secondRowAttrsLayout;
        }

        private BoxLayout createBtnsLayout() {
            BoxLayout btnsLayout = wizard.componentsFactory.createComponent(BoxLayout.HBOX);
            btnsLayout.setSpacing(true);
            btnsLayout.setStyleName("on-hover-visible-layout");
            return btnsLayout;
        }

        private BoxLayout createThirdRowAttrsLayout() {
            BoxLayout thirdRowAttrsLayout = wizard.componentsFactory.createComponent(BoxLayout.HBOX);
            thirdRowAttrsLayout.setSpacing(true);
            Label entityLbl = wizard.componentsFactory.createComponent(Label.NAME);
            entityLbl.setStyleName(BOLD_LABEL_STYLE);
            entityLbl.setValue(wizard.getMessage("attributes"));
            Button editBtn = wizard.componentsFactory.createComponent(Button.NAME);
            editBtn.setCaption(generateAttrsBtnCaption());
            editBtn.setStyleName("link");
            editBtn.setWidth(WIDTH_PERCENT_100);
            editBtn.setAction(editRegionAction);
            thirdRowAttrsLayout.add(entityLbl);
            thirdRowAttrsLayout.add(editBtn);
            return thirdRowAttrsLayout;
        }

        private String generateAttrsBtnCaption() {

            return StringUtils.abbreviate(StringUtils.join(CollectionUtils.collect(currentReportRegionGeneratedColumn.getRegionProperties(), new Transformer() {
                @Override
                public Object transform(Object input) {
                    return ((RegionProperty) input).getHierarchicalLocalizedNameExceptRoot();
                }
            }), ", "), MAX_ATTRS_BTN_CAPTION_WIDTH);
        }
    }

    protected class RemoveRegionAction extends AbstractAction {
        public RemoveRegionAction() {
            super("removeRegion");
        }

        @Override
        public void actionPerform(Component component) {
            if (wizard.regionsTable.getSingleSelected() != null) {
                wizard.showOptionDialog(
                        wizard.getMessage("dialogs.Confirmation"),
                        wizard.formatMessage("deleteRegion", ((ReportRegion) wizard.regionsTable.getSingleSelected()).getName()),
                        IFrame.MessageType.CONFIRMATION, new Action[]{
                                new DialogAction(DialogAction.Type.YES) {
                                    @Override
                                    public void actionPerform(Component component) {
                                        wizard.reportRegionsDs.removeItem((ReportRegion) wizard.regionsTable.getSingleSelected());
                                        normalizeRegionPropertiesOrderNum();
                                        wizard.regionsTable.refresh();
                                        wizard.setupButtonsVisibility();
                                    }
                                },
                                new DialogAction(DialogAction.Type.NO) {
                                }
                        });
            }
        }

        @Override
        public String getCaption() {
            return "";
        }

        protected void normalizeRegionPropertiesOrderNum() {
            long normalizedIdx = 0;
            List<ReportRegion> allItems = new ArrayList<>(wizard.reportRegionsDs.getItems());
            for (ReportRegion item : allItems) {
                item.setOrderNum(++normalizedIdx); //first must to be 1
            }
        }
    }

    protected class EditRegionAction extends AddRegionAction {
        public EditRegionAction() {
            super("removeRegion");
        }

        @Override
        public void actionPerform(Component component) {
            if (wizard.regionsTable.getSingleSelected() != null) {
                Map<String, Object> editorParams = new HashMap<>();
                editorParams.put("rootEntity", ((ReportRegion) wizard.regionsTable.getSingleSelected()).getRegionPropertiesRootNode());
                editorParams.put("scalarOnly", Boolean.TRUE);
                editorParams.put("persistentOnly", ReportData.ReportType.LIST_OF_ENTITIES_WITH_QUERY == wizard.reportTypeOptionGroup.getValue());
                Window.Editor regionEditor = wizard.openEditor("report$Report.regionEditor",
                        ((ReportRegion) wizard.regionsTable.getSingleSelected()),
                        WindowManager.OpenType.DIALOG,
                        editorParams,
                        wizard.reportRegionsDs);
                regionEditor.addListener(new RegionEditorCloseListener());
            }
        }

        @Override
        public String getCaption() {
            return "";
        }

    }

    protected class InitRegionsStepFrameHandler implements InitStepFrameHandler {
        @Override
        public void initFrame() {
            addSimpleRegionAction = new AddSimpleRegionAction();
            addTabulatedRegionAction = new AddTabulatedRegionAction();
            wizard.addSimpleRegionBtn.setAction(addSimpleRegionAction);
            wizard.addTabulatedRegionBtn.setAction(addTabulatedRegionAction);
            wizard.addRegionPopupBtn.addAction(addSimpleRegionAction);
            wizard.addRegionPopupBtn.addAction(addTabulatedRegionAction);
            wizard.regionsTable.addGeneratedColumn("regionsGeneratedColumn", new ReportRegionTableColumnGenerator());
            editRegionAction = new EditRegionAction();
            removeRegionAction = new RemoveRegionAction();

            wizard.moveDownBtn.setAction(new OrderableItemMoveAction<>("downItem", OrderableItemMoveAction.Direction.DOWN, wizard.regionsTable));
            wizard.moveUpBtn.setAction(new OrderableItemMoveAction<>("upItem", OrderableItemMoveAction.Direction.UP, wizard.regionsTable));
            wizard.removeBtn.setAction(removeRegionAction);
        }
    }

    protected class BeforeShowRegionsStepFrameHandler implements BeforeShowStepFrameHandler {
        @Override
        public void beforeShowFrame() {
            wizard.setupButtonsVisibility();
            wizard.runBtn.setAction(new AbstractAction("runReport") {
                @Override
                public void actionPerform(Component component) {
                    if (wizard.getItem().getReportRegions().isEmpty()) {
                        wizard.showNotification(wizard.getMessage("addRegionsWarn"), IFrame.NotificationType.TRAY);
                        return;
                    }
                    wizard.lastGeneratedTmpReport = wizard.buildReport(true);

                    if (wizard.lastGeneratedTmpReport != null) {
                        wizard.reportGuiManager.runReport(wizard.lastGeneratedTmpReport, wizard.stepFrameManager.getCurrentIFrame());
                    }
                }
            });
            showAddRegion();
            wizard.setCorrectReportOutputType();
        }

        private void showAddRegion() {
            if (wizard.reportRegionsDs.getItems().isEmpty()) {
                if (((ReportData.ReportType) wizard.reportTypeOptionGroup.getValue()).isList()) {
                    if (wizard.entityTreeHasSimpleAttrs) {
                        addTabulatedRegionAction.actionPerform(wizard.regionsStepFrame.getFrame());
                    }
                } else {
                    if (wizard.entityTreeHasSimpleAttrs && wizard.entityTreeHasCollections) {
                        addSimpleRegionAction.actionPerform(wizard.regionsStepFrame.getFrame());
                    } else if (wizard.entityTreeHasSimpleAttrs) {
                        addSimpleRegionAction.actionPerform(wizard.regionsStepFrame.getFrame());
                    } else if (wizard.entityTreeHasCollections) {
                        addTabulatedRegionAction.actionPerform(wizard.regionsStepFrame.getFrame());
                    }
                }
            }
        }
    }

    protected class BeforeHideRegionsStepFrameHandler implements BeforeHideStepFrameHandler {
        @Override
        public void beforeHideFrame() {
        }
    }
}
