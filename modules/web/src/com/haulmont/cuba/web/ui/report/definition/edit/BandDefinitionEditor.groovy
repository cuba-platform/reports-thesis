/*
 * Copyright (c) 2008 Haulmont Technology Ltd. All Rights Reserved.
 * Haulmont Technology proprietary and confidential.
 * Use is subject to license terms.

 * Author: Eugeniy Degtyarjov
 * Created: 07.05.2010 14:35:35
 *
 * $Id$
 */
package com.haulmont.cuba.web.ui.report.definition.edit

import com.haulmont.cuba.core.entity.Entity
import com.haulmont.cuba.core.global.MessageProvider
import com.haulmont.cuba.core.global.UserSessionProvider
import com.haulmont.cuba.gui.AppConfig
import com.haulmont.cuba.gui.autocomplete.AutoCompleteSupport
import com.haulmont.cuba.gui.autocomplete.JpqlSuggestionFactory
import com.haulmont.cuba.gui.autocomplete.Suggester
import com.haulmont.cuba.gui.autocomplete.Suggestion
import com.haulmont.cuba.gui.components.actions.RemoveAction
import com.haulmont.cuba.gui.data.CollectionDatasource
import com.haulmont.cuba.gui.data.Datasource
import com.haulmont.cuba.gui.data.ValueListener
import com.haulmont.cuba.gui.data.impl.DsListenerAdapter
import com.haulmont.cuba.report.BandDefinition
import com.haulmont.cuba.report.DataSet
import com.haulmont.cuba.report.DataSetType
import com.haulmont.cuba.report.Orientation
import com.haulmont.cuba.security.entity.EntityOp
import com.haulmont.cuba.gui.components.*
import com.haulmont.cuba.core.global.PersistenceHelper

public class BandDefinitionEditor extends AbstractEditor implements Suggester {

    private static volatile Collection<com.haulmont.chile.core.model.MetaClass> metaClasses;

    def BandDefinitionEditor(IFrame frame) {
        super(frame);
    }

    @Override
    def void setItem(Entity item) {
        BandDefinition definition = (BandDefinition) item
        if (PersistenceHelper.isNew(item))
            definition.orientation = Orientation.HORIZONTAL

        super.setItem(definition);
        selectFirstDataset()
    }

    @Override
    public void init(Map<String, Object> params) {
        super.init(params);

        Table table = getComponent('dataSets')
        table.addAction(new RemoveAction(table, false))

        table.addAction(new ActionAdapter('create', [
                actionPerform: {
                    Component component ->
                    DataSet dataset = new DataSet()
                    dataset.bandDefinition = (BandDefinition) item
                    dataset.name = dataset.bandDefinition.name ?: 'dataset'
                    dataset.type = DataSetType.GROOVY

                    dataset.entityParamName = 'entity'
                    dataset.listEntitiesParamName = 'entities'

                    table.datasource.addItem(dataset)
                },
                getCaption: {
                    MessageProvider.getMessage(AppConfig.messagesPack, 'actions.Create')
                },
                isEnabled: {
                    UserSessionProvider.userSession.isEntityOpPermitted(table.datasource.metaClass, EntityOp.CREATE)
                }
        ]))

        initDataSetControls()
    }

    @Override
    void commitAndClose() {
        if (commit()) {
            BandDefinition definition = (BandDefinition) getItem();
            BandDefinition parentDefinition = definition.parentBandDefinition
            if (parentDefinition) {
                if (PersistenceHelper.isNew(definition)) {
                    if (parentDefinition.childrenBandDefinitions == null)
                        parentDefinition.childrenBandDefinitions = new ArrayList<BandDefinition>()

                    parentDefinition.childrenBandDefinitions.add(definition)
                }
            }
            close(COMMIT_ACTION_ID)
        }
    }

    def initDataSetControls() {
        LookupField lookupField = getComponent('type')
        AutoCompleteTextField queryTextField = getComponent('text')
        TextField nameField = getComponent('datasetName')
        Label queryLabel = getComponent('dataSet_text')

        Label entityParamLabel = getComponent('entityParamLabel')
        TextField entityParamTextBox = getComponent('entityParamTextBox')

        Label entitiesParamLabel = getComponent('entitiesParamLabel')
        TextField entitiesParamTextBox = getComponent('entitiesParamTextBox')

        def queryEditors = [
                queryLabel, queryTextField
        ]

        def entityParamEditors = [
                entityParamLabel, entityParamTextBox
        ]

        def entitiesParamEditors = [
                entitiesParamLabel, entitiesParamTextBox
        ]

        def allParams = [
                queryLabel, queryTextField,
                entityParamLabel, entityParamTextBox,
                entitiesParamLabel, entitiesParamTextBox
        ]

        lookupField.addListener(
                [
                        valueChanged: { Object source, String property, Object prevValue, Object value ->

                            // Hide all editors for dataset
                            allParams.each { Component c -> c.visible = false }

                            DataSetType dsType = (DataSetType) value;
                            switch (dsType) {
                                case DataSetType.SQL:
                                case DataSetType.JPQL:
                                case DataSetType.GROOVY:
                                    queryEditors.each { Component c -> c.visible = true }
                                    queryTextField.setSuggester(DataSetType.JPQL.equals(value) ? this : null)
                                    break

                                case DataSetType.SINGLE:
                                    entityParamEditors.each { Component c -> c.visible = true }
                                    break

                                case DataSetType.MULTI:
                                    entitiesParamEditors.each { Component c -> c.visible = true }
                                    break
                            }
                        }
                ] as ValueListener
        )

        allParams.each { Component c -> c.visible = false }
        queryEditors.each { Component c -> c.visible = true }

        def enableDatasetControls = {
            boolean value ->
            [lookupField, queryTextField, nameField].each {it.enabled = value}
        }

        Table datasets = getComponent('dataSets')
        CollectionDatasource ds = datasets.datasource
        ds.addListener([
                itemChanged: {
                    Datasource ds1, Entity prevItem, Entity item1 ->
                    enableDatasetControls(item1 != null)
                }
        ] as DsListenerAdapter)

        enableDatasetControls(false)
    }

    def selectFirstDataset() {
        Table datasets = getComponent('dataSets')
        CollectionDatasource ds = datasets.datasource
        ds.refresh()
        if (!ds.itemIds.empty) {
            def item = ds.getItem(ds.itemIds.iterator().next())
            def set = new HashSet()
            set.add(item)
            datasets.setSelected(set)
        }
    }

    @Override
    java.util.List<Suggestion> getSuggestions(AutoCompleteSupport source, String text, int cursorPosition) {
        String query = (String) source.getValue()
        if (query == null || "".equals(query.trim())) {
            return Collections.emptyList()
        }
        int queryPosition = cursorPosition - 1
        return JpqlSuggestionFactory.requestHint(query, queryPosition, source, cursorPosition)
    }
}
