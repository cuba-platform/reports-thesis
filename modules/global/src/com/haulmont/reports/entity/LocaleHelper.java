/*
 * Copyright (c) 2011 Haulmont Technology Ltd. All Rights Reserved.
 * Haulmont Technology proprietary and confidential.
 * Use is subject to license terms.
 */

package com.haulmont.reports.entity;

import com.haulmont.cuba.core.global.AppBeans;
import com.haulmont.cuba.core.global.UserSessionSource;
import org.apache.commons.lang.StringUtils;

import java.io.IOException;
import java.io.StringReader;
import java.util.Locale;
import java.util.Properties;

/**
 * @author artamonov
 * @version $Id$
 */
public final class LocaleHelper {

    private LocaleHelper() {
    }

    public static String getLocalizedName(String localeBundle) {
        Locale locale = AppBeans.get(UserSessionSource.class).getLocale();
        String localeName = null;
        if (StringUtils.isNotEmpty(localeBundle)) {
            // find locale name
            StringReader reader = new StringReader(localeBundle);
            Properties localeProperties = new Properties();
            boolean localeLoaded = false;
            try {
                localeProperties.load(reader);
                localeLoaded = true;
            } catch (IOException ignored) {
            }
            if (localeLoaded) {
                String key = locale.getLanguage();
                if (StringUtils.isNotEmpty(locale.getCountry()))
                    key += "_" + locale.getCountry();
                if (localeProperties.containsKey(key))
                    localeName = (String) localeProperties.get(key);
            }
        }
        return localeName;
    }
}