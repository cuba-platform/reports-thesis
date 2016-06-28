/*
 * Copyright (c) 2008-2016 Haulmont. All rights reserved.
 * Use is subject to license terms, see http://www.cuba-platform.com/commercial-software-license for details.
 */

package com.haulmont.reports.gui.template.edit;

import com.haulmont.cuba.gui.components.*;
import com.haulmont.cuba.gui.components.actions.CreateAction;
import com.haulmont.cuba.gui.data.CollectionDatasource;
import com.haulmont.cuba.gui.data.Datasource;
import com.haulmont.cuba.gui.xml.layout.ComponentsFactory;
import com.haulmont.reports.entity.BandDefinition;
import com.haulmont.reports.entity.charts.*;
import com.haulmont.reports.gui.report.run.ShowChartController;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.util.*;
import java.util.stream.Collectors;

public class ChartEditFrameController extends AbstractFrame {
    @Inject
    protected ComponentsFactory componentsFactory;
    @Inject
    protected Datasource<PieChartDescription> pieChartDs;
    @Inject
    protected Datasource<SerialChartDescription> serialChartDs;
    @Inject
    protected CollectionDatasource<ChartSeries, UUID> seriesDs;
    @Inject
    protected LookupField type;
    @Inject
    protected Table<ChartSeries> seriesTable;
    @Inject
    protected FieldGroup pieChartFieldGroup;
    @Inject
    protected FieldGroup serialChartFieldGroup;

    @Override
    public void init(Map<String, Object> params) {
        super.init(params);
        pieChartDs.setItem(new PieChartDescription());
        serialChartDs.setItem(new SerialChartDescription());
        type.setOptionsList(Arrays.asList(ChartType.values()));

        type.addValueChangeListener(e -> {
            pieChartFieldGroup.setVisible(ChartType.PIE == e.getValue());
            serialChartFieldGroup.setVisible(ChartType.SERIAL == e.getValue());
            seriesTable.setVisible(ChartType.SERIAL == e.getValue());

            ((Window) getFrame()).getDialogOptions().center();

            showChartPreviewBox();
        });

        pieChartFieldGroup.setVisible(false);
        serialChartFieldGroup.setVisible(false);
        seriesTable.setVisible(false);

        seriesTable.addAction(new CreateAction(seriesTable) {
            @Override
            public void actionPerform(Component component) {
                ChartSeries chartSeries = new ChartSeries();
                seriesDs.addItem(chartSeries);
                seriesTable.refresh();
            }
        });

        pieChartDs.addItemPropertyChangeListener(e -> showChartPreviewBox());

        serialChartDs.addItemPropertyChangeListener(e -> showChartPreviewBox());

        seriesDs.addItemPropertyChangeListener(e -> showChartPreviewBox());
        seriesDs.addCollectionChangeListener(e -> showChartPreviewBox());

        FieldGroup.CustomFieldGenerator bandSelectorGenerator = (datasource, propertyId) -> {
            LookupField lookupField = componentsFactory.createComponent(LookupField.class);
            lookupField.setDatasource(datasource, propertyId);
            return lookupField;
        };
        pieChartFieldGroup.addCustomField("bandName", bandSelectorGenerator);
        serialChartFieldGroup.addCustomField("bandName", bandSelectorGenerator);
    }

    protected void previewChart(BoxLayout previewBox) {
        List<Map<String, Object>> data;
        String chartJson = null;
        if (ChartType.SERIAL == type.getValue()) {
            SerialChartDescription chartDescription = serialChartDs.getItem();
            data = new RandomChartDataGenerator().generateRandomChartData(chartDescription);
            ChartToJsonConverter chartToJsonConverter = new ChartToJsonConverter();
            chartJson = chartToJsonConverter.convertSerialChart(chartDescription, data);
        } else if (ChartType.PIE == type.getValue()) {
            PieChartDescription chartDescription = pieChartDs.getItem();
            data = new RandomChartDataGenerator().generateRandomChartData(chartDescription);
            ChartToJsonConverter chartToJsonConverter = new ChartToJsonConverter();
            chartJson = chartToJsonConverter.convertPieChart(chartDescription, data);
        }
        if (chartJson == null) {
            chartJson = "{}";
        }
        openFrame(previewBox, ShowChartController.JSON_CHART_SCREEN_ID,
                Collections.<String, Object>singletonMap(ShowChartController.CHART_JSON_PARAMETER, chartJson));
    }

    public BoxLayout showChartPreviewBox() {
        Window parent = (Window) getFrame();
        BoxLayout previewBox = (BoxLayout) parent.getComponentNN("chartPreviewBox");
        previewBox.setVisible(true);
        previewBox.setHeight("100%");
        previewBox.setWidth("100%");
        previewBox.removeAll();
        parent.getDialogOptions()
                .setWidth(1280)
                .setResizable(true);
        previewChart(previewBox);
        return previewBox;
    }

    public void hideChartPreviewBox() {
        Window parent = (Window) getFrame();
        BoxLayout previewBox = (BoxLayout) parent.getComponentNN("chartPreviewBox");
        previewBox.setVisible(false);
        previewBox.removeAll();
        parent.getDialogOptions()
                .setWidthAuto()
                .setHeightAuto()
                .setResizable(false);
    }

    @Nullable
    public AbstractChartDescription getChartDescription() {
        if (ChartType.SERIAL == type.getValue()) {
            return serialChartDs.getItem();
        } else if (ChartType.PIE == type.getValue()) {
            return pieChartDs.getItem();
        }

        return null;
    }

    public void setChartDescription(@Nullable AbstractChartDescription chartDescription) {
        if (chartDescription != null) {
            if (ChartType.SERIAL == chartDescription.getType()) {
                serialChartDs.setItem((SerialChartDescription) chartDescription);
            } else if (ChartType.PIE == chartDescription.getType()) {
                pieChartDs.setItem((PieChartDescription) chartDescription);
            }
            type.setValue(chartDescription.getType());
        }
    }

    public void setBands(Collection<BandDefinition> bands) {
        List<String> bandNames = bands.stream().map(BandDefinition::getName).collect(Collectors.toList());

        LookupField pieChartBandName = (LookupField) pieChartFieldGroup.getFieldComponent("bandName");
        LookupField serialChartBandName = (LookupField) serialChartFieldGroup.getFieldComponent("bandName");

        pieChartBandName.setOptionsList(bandNames);
        serialChartBandName.setOptionsList(bandNames);
    }
}
