/*
 * Copyright (c) 2008-2016 Haulmont. All rights reserved.
 * Use is subject to license terms, see http://www.cuba-platform.com/commercial-software-license for details.
 */

package com.haulmont.reports.gui.report.validators;

import com.haulmont.cuba.core.global.AppBeans;
import com.haulmont.cuba.core.global.Messages;
import com.haulmont.cuba.gui.components.Field;
import com.haulmont.cuba.gui.components.ValidationException;
import com.haulmont.reports.gui.report.wizard.ReportWizardCreator;
import org.apache.commons.lang.StringUtils;

public class OutputFileNameValidator implements Field.Validator {
    protected Messages messages = AppBeans.get(Messages.class);

    @Override
    public void validate(Object value) throws ValidationException {
        if (StringUtils.isNotEmpty((String) value) && !((String) value).matches(".+\\.([A-z]{0,5})"))
            throw new ValidationException(String.format(
                    messages.getMessage(OutputFileNameValidator.class, "fillCorrectOutputFileNameMsg"),
                    messages.getMessage(ReportWizardCreator.class, "outputFileName")));

    }
}
