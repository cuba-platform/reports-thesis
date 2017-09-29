/*
 * Copyright (c) 2008-2016 Haulmont. All rights reserved.
 * Use is subject to license terms, see http://www.cuba-platform.com/commercial-software-license for details.
 */

package com.haulmont.reports.gui.report.run;

import com.google.common.collect.ImmutableMap;
import com.haulmont.bali.util.ParamsMap;
import com.haulmont.chile.core.datatypes.Datatypes;
import com.haulmont.chile.core.model.MetaClass;
import com.haulmont.cuba.core.global.*;
import com.haulmont.cuba.gui.AppConfig;
import com.haulmont.cuba.gui.components.*;
import com.haulmont.cuba.gui.components.Component.Alignment;
import com.haulmont.cuba.gui.components.validators.DoubleValidator;
import com.haulmont.cuba.gui.data.CollectionDatasource;
import com.haulmont.cuba.gui.data.CollectionDatasource.RefreshMode;
import com.haulmont.cuba.gui.data.DsBuilder;
import com.haulmont.cuba.gui.xml.layout.ComponentsFactory;
import com.haulmont.reports.app.service.ReportService;
import com.haulmont.reports.entity.ParameterType;
import com.haulmont.reports.entity.ReportInputParameter;
import org.apache.commons.lang.BooleanUtils;
import org.apache.commons.lang.StringUtils;

import java.util.*;

import static com.haulmont.cuba.gui.commonlookup.CommonLookupController.CLASS_PARAMETER;

public class ParameterFieldCreator {

    public static final String COMMON_LOOKUP_SCREEN_ID = "commonLookup";

    protected ComponentsFactory componentsFactory = AppConfig.getFactory();

    protected Messages messages = AppBeans.get(Messages.class);
    protected Metadata metadata = AppBeans.get(Metadata.class);
    protected Scripting scripting = AppBeans.get(Scripting.class);
    protected ReportService reportService = AppBeans.get(ReportService.class);

    protected AbstractFrame frame;

    protected Map<ParameterType, FieldCreator> fieldCreationMapping = new ImmutableMap.Builder<ParameterType, FieldCreator>()
            .put(ParameterType.BOOLEAN, new CheckBoxCreator())
            .put(ParameterType.DATE, new DateFieldCreator())
            .put(ParameterType.ENTITY, new SingleFieldCreator())
            .put(ParameterType.ENUMERATION, new EnumFieldCreator())
            .put(ParameterType.TEXT, new TextFieldCreator())
            .put(ParameterType.NUMERIC, new NumericFieldCreator())
            .put(ParameterType.ENTITY_LIST, new MultiFieldCreator())
            .put(ParameterType.DATETIME, new DateTimeFieldCreator())
            .put(ParameterType.TIME, new TimeFieldCreator())
            .build();

    public ParameterFieldCreator(AbstractFrame frame) {
        this.frame = frame;
    }

    public Label createLabel(ReportInputParameter parameter, Field field) {
        Label label = componentsFactory.createComponent(Label.class);
        label.setAlignment(field instanceof TokenList ? Alignment.TOP_LEFT : Alignment.MIDDLE_LEFT);
        label.setWidth(Component.AUTO_SIZE);
        label.setValue(parameter.getLocName());
        return label;
    }

    public Field createField(ReportInputParameter parameter) {
        Field field = fieldCreationMapping.get(parameter.getType()).createField(parameter);
        field.setRequiredMessage(messages.formatMessage(this.getClass(), "error.paramIsRequiredButEmpty", parameter.getLocName()));

        field.setId("param_" + parameter.getAlias());
        field.setWidth("100%");
        field.setFrame(frame);
        field.setEditable(true);

        field.setRequired(parameter.getRequired());
        return field;
    }

    protected void setCurrentDateAsNow(ReportInputParameter parameter, Field dateField) {
        Date now = reportService.currentDateOrTime(parameter.getType());
        dateField.setValue(now);
        parameter.setDefaultValue(reportService.convertToString(now.getClass(), now));
    }

    protected interface FieldCreator {
        Field createField(ReportInputParameter parameter);
    }

    protected class DateFieldCreator implements FieldCreator {
        @Override
        public Field createField(ReportInputParameter parameter) {
            DateField dateField = componentsFactory.createComponent(DateField.class);
            dateField.setResolution(DateField.Resolution.DAY);
            dateField.setDateFormat(messages.getMainMessage("dateFormat"));
            if (BooleanUtils.isTrue(parameter.getDefaultDateIsCurrent())) {
                setCurrentDateAsNow(parameter, dateField);
            }
            return dateField;
        }
    }

    protected class DateTimeFieldCreator implements FieldCreator {
        @Override
        public Field createField(ReportInputParameter parameter) {
            DateField dateField = componentsFactory.createComponent(DateField.class);
            dateField.setResolution(DateField.Resolution.MIN);
            dateField.setDateFormat(messages.getMainMessage("dateTimeFormat"));
            if (BooleanUtils.isTrue(parameter.getDefaultDateIsCurrent())) {
                setCurrentDateAsNow(parameter, dateField);
            }
            return dateField;
        }
    }

    protected class TimeFieldCreator implements FieldCreator {

        @Override
        public Field createField(ReportInputParameter parameter) {
            Field timeField = componentsFactory.createComponent(TimeField.class);
            if (BooleanUtils.isTrue(parameter.getDefaultDateIsCurrent())) {
                setCurrentDateAsNow(parameter, timeField);
            }
            return componentsFactory.createComponent(TimeField.class);
        }
    }

    protected class CheckBoxCreator implements FieldCreator {

        @Override
        public Field createField(ReportInputParameter parameter) {
            CheckBox checkBox = componentsFactory.createComponent(CheckBox.class);
            checkBox.setAlignment(Alignment.MIDDLE_LEFT);
            return checkBox;
        }
    }

    protected class TextFieldCreator implements FieldCreator {

        @Override
        public Field createField(ReportInputParameter parameter) {
            return componentsFactory.createComponent(TextField.class);
        }
    }

    protected class NumericFieldCreator implements FieldCreator {

        @Override
        public Field createField(ReportInputParameter parameter) {
            TextField textField = componentsFactory.createComponent(TextField.class);
            textField.addValidator(new DoubleValidator());
            textField.setDatatype(Datatypes.getNN(Double.class));
            return textField;
        }
    }

    protected class EnumFieldCreator implements FieldCreator {

        @Override
        public Field createField(ReportInputParameter parameter) {
            LookupField lookupField = componentsFactory.createComponent(LookupField.class);
            String enumClassName = parameter.getEnumerationClass();
            if (StringUtils.isNotBlank(enumClassName)) {
                Class enumClass = scripting.loadClass(enumClassName);

                if (enumClass != null) {
                    Object[] constants = enumClass.getEnumConstants();
                    List<Object> optionsList = new ArrayList<>();
                    Collections.addAll(optionsList, constants);

                    lookupField.setOptionsList(optionsList);
                    lookupField.setCaptionMode(CaptionMode.ITEM);

                    if (optionsList.size() < 10) {
                        lookupField.setTextInputAllowed(false);
                    }
                }
            }
            return lookupField;
        }
    }

    protected class SingleFieldCreator implements FieldCreator {
        @Override
        public Field createField(ReportInputParameter parameter) {
            PickerField pickerField = componentsFactory.createComponent(PickerField.class);
            MetaClass entityMetaClass = metadata.getClassNN(parameter.getEntityMetaClass());
            pickerField.setMetaClass(entityMetaClass);

            PickerField.LookupAction pickerLookupAction = pickerField.addLookupAction();
            pickerField.addAction(pickerLookupAction);
            pickerField.addClearAction();

            String parameterScreen = parameter.getScreen();

            if (StringUtils.isNotEmpty(parameterScreen)) {
                pickerLookupAction.setLookupScreen(parameterScreen);
                pickerLookupAction.setLookupScreenParams(Collections.emptyMap());
            } else {
                pickerLookupAction.setLookupScreen(COMMON_LOOKUP_SCREEN_ID);
                pickerLookupAction.setLookupScreenParams(ParamsMap.of(CLASS_PARAMETER, entityMetaClass));
            }

            return pickerField;
        }
    }

    protected class MultiFieldCreator implements FieldCreator {

        @Override
        public Field createField(final ReportInputParameter parameter) {
            TokenList tokenList = componentsFactory.createComponent(TokenList.class);
            MetaClass entityMetaClass = metadata.getClassNN(parameter.getEntityMetaClass());

            DsBuilder builder = DsBuilder.create(frame.getDsContext());
            CollectionDatasource cds = builder
                    .setRefreshMode(RefreshMode.NEVER)
                    .setId("entities_" + parameter.getAlias())
                    .setMetaClass(entityMetaClass)
                    .setViewName(View.LOCAL)
                    .setAllowCommit(false)
                    .buildCollectionDatasource();

            cds.refresh();

            tokenList.setDatasource(cds);
            tokenList.setEditable(true);
            tokenList.setLookup(true);
            tokenList.setHeight("120px");

            String screen = parameter.getScreen();

            if (StringUtils.isNotEmpty(screen)) {
                tokenList.setLookupScreen(screen);
                tokenList.setLookupScreenParams(Collections.emptyMap());
            } else {
                tokenList.setLookupScreen("commonLookup");
                tokenList.setLookupScreenParams(ParamsMap.of(CLASS_PARAMETER, entityMetaClass));
            }

            tokenList.setAddButtonCaption(messages.getMessage(TokenList.class, "actions.Select"));
            tokenList.setInline(true);
            tokenList.setSimple(true);

            return tokenList;
        }
    }
}