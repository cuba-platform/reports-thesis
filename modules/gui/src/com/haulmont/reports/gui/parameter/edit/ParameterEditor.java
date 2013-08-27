/*
 * Copyright (c) 2008 Haulmont Technology Ltd. All Rights Reserved.
 * Haulmont Technology proprietary and confidential.
 * Use is subject to license terms.
 */
package com.haulmont.reports.gui.parameter.edit;

import com.haulmont.chile.core.model.MetaClass;
import com.haulmont.cuba.core.entity.Entity;
import com.haulmont.cuba.core.global.Metadata;
import com.haulmont.cuba.gui.components.AbstractEditor;
import com.haulmont.cuba.gui.components.LookupField;
import com.haulmont.cuba.gui.config.WindowConfig;
import com.haulmont.cuba.gui.config.WindowInfo;
import com.haulmont.cuba.gui.data.ValueListener;
import com.haulmont.reports.entity.ParameterType;
import com.haulmont.reports.entity.ReportInputParameter;

import javax.inject.Inject;
import java.util.*;

/**
 * @author degtyarjov
 * @version $Id$
 */
public class ParameterEditor extends AbstractEditor {

    private ReportInputParameter parameter;
    private LookupField metaClass;
    private LookupField screen;
    private LookupField enumLookup;
    private Map<String, String> metaNamesToClassNames = new HashMap<>();
    private Map<String, String> classNamesToMetaNames = new HashMap<>();

    private Map<String, Class> enumNamesToEnumClass = new HashMap<>();
    private Map<String, String> enumClassToEnumNames = new HashMap<>();

    @Inject
    private WindowConfig windowConfig;

    @Inject
    private Metadata metadata;

    @Override
    public void setItem(Entity item) {
        super.setItem(item);
        parameter = (ReportInputParameter) getItem();
        enableControlsByParamType(parameter.getType());

        metaClass.setValue(metaNamesToClassNames.get(parameter.getEntityMetaClass()));
        enumLookup.setValue(enumClassToEnumNames.get(parameter.getEnumerationClass()));
        screen.setValue(parameter.getScreen());
    }

    @Override
    @SuppressWarnings({"unchecked", "serial"})
    public void init(Map<String, Object> params) {
        super.init(params);

        LookupField type = getComponent("type");
        metaClass = getComponent("metaClass");
        enumLookup = getComponent("enumeration");
        screen = getComponent("screen");

        List metaClasses = new ArrayList();
        Collection<MetaClass> classes = metadata.getSession().getClasses();
        for (MetaClass clazz : classes) {
            metaNamesToClassNames.put(clazz.getName(), clazz.getJavaClass().getSimpleName());
            classNamesToMetaNames.put(clazz.getJavaClass().getSimpleName(), clazz.getName());
        }

        metaClasses.addAll(classNamesToMetaNames.keySet());
        metaClass.setOptionsList(metaClasses);
        metaClass.addListener(new ValueListener() {
            public void valueChanged(Object source, String property, Object prevValue, Object value) {
                String metaClassName = value != null ? classNamesToMetaNames.get(value.toString()) : null;
                parameter.setEntityMetaClass(metaClassName);
            }
        });

        List enums = new ArrayList();
        for (Class enumClass : metadata.getTools().getAllEnums()) {
            enums.add(enumClass.getSimpleName());
            enumNamesToEnumClass.put(enumClass.getSimpleName(), enumClass);
            enumClassToEnumNames.put(enumClass.getCanonicalName(), enumClass.getSimpleName());
        }
        enumLookup.setOptionsList(enums);
        enumLookup.addListener(new ValueListener() {
            @Override
            public void valueChanged(Object source, String property, Object prevValue, Object value) {
                if (value != null) {
                    Class enumClass = enumNamesToEnumClass.get(value.toString());
                    parameter.setEnumerationClass(enumClass.getCanonicalName());
                } else
                    parameter.setEnumerationClass(null);
            }
        });

        Collection<WindowInfo> windowInfoCollection = windowConfig.getWindows();
        List screensList = new ArrayList();
        for (WindowInfo windowInfo : windowInfoCollection) {
            screensList.add(windowInfo.getId());
        }
        screen.setOptionsList(screensList);
        screen.addListener(new ValueListener() {
            @Override
            public void valueChanged(Object source, String property, Object prevValue, Object value) {
                parameter.setScreen(value != null ? value.toString() : null);
            }
        });

        type.addListener(new ValueListener() {
            @Override
            public void valueChanged(Object source, String property, Object prevValue, Object value) {
                enableControlsByParamType(value);
            }
        });

        getDialogParams().setWidth(450);
    }

    private void enableControlsByParamType(Object value) {
        boolean isEntity = ParameterType.ENTITY.equals(value) || ParameterType.ENTITY_LIST.equals(value);
        boolean isEnum = ParameterType.ENUMERATION.equals(value);
        metaClass.setEnabled(isEntity);
        enumLookup.setEnabled(isEnum);
        screen.setEnabled(isEntity);

        metaClass.setRequired(isEntity);
        enumLookup.setRequired(isEnum);
    }
}
