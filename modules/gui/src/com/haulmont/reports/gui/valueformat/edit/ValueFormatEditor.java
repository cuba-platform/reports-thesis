/*
 * Copyright (c) 2008-2016 Haulmont. All rights reserved.
 * Use is subject to license terms, see http://www.cuba-platform.com/commercial-software-license for details.
 */
package com.haulmont.reports.gui.valueformat.edit;

import com.haulmont.cuba.core.entity.Entity;
import com.haulmont.cuba.core.global.Metadata;
import com.haulmont.cuba.gui.components.AbstractEditor;
import com.haulmont.cuba.gui.components.Component;
import com.haulmont.cuba.gui.components.FieldGroup;
import com.haulmont.cuba.gui.components.LookupField;
import com.haulmont.cuba.gui.data.Datasource;
import com.haulmont.cuba.gui.data.impl.DatasourceImplementation;
import com.haulmont.cuba.gui.xml.layout.ComponentsFactory;
import com.haulmont.reports.entity.ReportValueFormat;

import javax.inject.Inject;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ValueFormatEditor extends AbstractEditor<ReportValueFormat> {

    protected String[] defaultFormats = new String[]{
            "#,##0",
            "##,##0",
            "#,##0.###",
            "#,##0.##",
            "dd/MM/yyyy HH:mm",
            "${image:WxH}",
            "${bitmap:WxH}",
            "${imageFileId:WxH}",
            "${html}"
    };

    protected LookupField formatField = null;

    @Inject
    protected FieldGroup formatFields;

    @Inject
    protected ComponentsFactory componentsFactory;

    @Inject
    protected Datasource valuesFormatsDs;

    @Inject
    protected Metadata metadata;

    @Override
    public void init(Map<String, Object> params) {
        super.init(params);

        getDialogOptions().setWidthAuto();

        // Add default format strings to combobox
        formatFields.addCustomField("formatString", new FieldGroup.CustomFieldGenerator() {
            @Override
            public Component generateField(Datasource datasource, String propertyId) {
                formatField = componentsFactory.createComponent(LookupField.class);
                Map<String, Object> options = new HashMap<>();
                for (String format : defaultFormats) {
                    options.put(format, format);
                }

                formatField.setDatasource(datasource, propertyId);
                formatField.setOptionsMap(options);
                formatField.setNewOptionAllowed(true);
                formatField.setNewOptionHandler(new LookupField.NewOptionHandler() {
                    @Override
                    public void addNewOption(String caption) {
                        addFormatItem(caption);
                        formatField.setValue(caption);
                    }
                });
                return formatField;
            }
        });

        //noinspection unchecked
        valuesFormatsDs.addItemPropertyChangeListener(e ->
                ((DatasourceImplementation) valuesFormatsDs).modified(e.getItem()));
    }

    protected void addFormatItem(String caption) {
        Map<String, Object> optionsMap = formatField.getOptionsMap();
        optionsMap.put(caption, caption);
        formatField.setOptionsMap(optionsMap);
    }

    @Override
    protected void postInit() {
        Object value = formatField.getValue();
        if (value != null) {
            if (!formatField.getOptionsMap().containsValue(value)) {
                addFormatItem((String) value);
            }
            formatField.setValue(value);
        }
    }

    @Override
    public void setItem(Entity item) {
        Entity newItem = valuesFormatsDs.getDataSupplier().newInstance(valuesFormatsDs.getMetaClass());
        metadata.getTools().copy(item, newItem);
        ((ReportValueFormat) newItem).setId((UUID) item.getId());
        super.setItem(newItem);
    }
}