/*
 * Copyright (c) 2008 Haulmont Technology Ltd. All Rights Reserved.
 * Haulmont Technology proprietary and confidential.
 * Use is subject to license terms.
 */
package com.haulmont.reports.loaders;

import com.haulmont.bali.db.QueryRunner;
import com.haulmont.bali.db.ResultSetHandler;
import com.haulmont.chile.core.datatypes.impl.EnumClass;
import com.haulmont.cuba.core.Persistence;
import com.haulmont.cuba.core.entity.Entity;
import com.haulmont.cuba.core.global.AppBeans;
import com.haulmont.cuba.core.sys.persistence.DbTypeConverter;
import com.haulmont.cuba.core.sys.persistence.DbmsType;
import com.haulmont.reports.entity.Band;
import com.haulmont.reports.entity.DataSet;
import com.haulmont.reports.exception.ReportDataLoaderException;
import org.apache.commons.lang.StringUtils;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author degtyarjov
 * @version $Id$
 */
public class SqlDataDataLoader extends QueryDataLoader {

    public SqlDataDataLoader(Map<String, Object> params) {
        super(params);
    }

    private static class ParamPosition {
        private Integer position;
        private Object value;
        private String paramRegexp;

        private ParamPosition(String paramRegexp, Integer position, Object value) {
            this.position = position;
            this.value = value;
            this.paramRegexp = paramRegexp;
        }

        public Integer getPosition() {
            return position;
        }

        public Object getValue() {
            return value;
        }

        public String getParamRegexp() {
            return paramRegexp;
        }
    }

    protected QueryPack prepareNativeQuery(String query, Band parentBand) throws SQLException {
        Map<String, Object> currentParams = new HashMap<>();
        if (params != null) currentParams.putAll(params);

        //adds parameters from parent bands hierarchy
        while (parentBand != null) {
            addParentBandParameters(parentBand, currentParams);
            parentBand = parentBand.getParentBand();
        }

        DbTypeConverter typeConverter = DbmsType.getCurrent().getTypeConverter();

        List<ParamPosition> paramPositions = new ArrayList<>();
        List<Object> values = new ArrayList<>();
        for (Map.Entry<String, Object> entry : currentParams.entrySet()) {
            // Remembers ${alias} positions
            String alias = "${" + entry.getKey() + "}";
            String regexp = "\\$\\{" + entry.getKey() + "\\}";

            // todo: another regexp to remove parameter
            String deleteRegexp = "(?i)(and)?(or)? ?[\\w|\\d|\\.|\\_]+ ?(=|>=|<=|like) ?\\$\\{" + entry.getKey() + "\\}";

            if (entry.getValue() == null) {
                // remove unused null parameter
                query = query.replaceAll(deleteRegexp, "");
            } else if (query.contains(alias)) {
                Pattern pattern = Pattern.compile(regexp);
                Matcher matcher = pattern.matcher(query);

                int subPosition = 0;
                // If value is Entity, replace it by their Id
                Object value = entry.getValue();
                if (value instanceof Entity)
                    value = ((Entity) entry.getValue()).getId();

                while (matcher.find(subPosition)) {
                    paramPositions.add(
                            new ParamPosition(regexp, matcher.start(),
                                    typeConverter.getSqlObject(value))
                    );
                    subPosition = matcher.end();
                }
            }
        }

        // Sort params by position
        Collections.sort(paramPositions, new Comparator<ParamPosition>() {
            @Override
            public int compare(ParamPosition o1, ParamPosition o2) {
                return o1.getPosition().compareTo(o2.getPosition());
            }
        });

        for (ParamPosition paramEntry : paramPositions) {
            // Replace all params by ?
            query = query.replaceAll(paramEntry.getParamRegexp(), "?");
            Object value = paramEntry.getValue();
            if (!(value instanceof EnumClass))
                values.add(value);
            else
                values.add(((EnumClass) value).getId());
        }

        query = query.trim();
        if (query.endsWith("where")) query = query.replace("where", "");

        return new QueryPack(query, values.toArray());
    }

    @Override
    public List<Map<String, Object>> loadData(DataSet dataSet, Band parentBand) {
        List resList;
        List<String> outputParameters;

        String query = dataSet.getText();
        if (StringUtils.isBlank(query)) return Collections.emptyList();

        Persistence persistence = AppBeans.get(Persistence.NAME);
        QueryRunner runner = new QueryRunner(persistence.getDataSource());

        try {
            QueryPack pack = prepareNativeQuery(query, parentBand);

            resList = runner.query(pack.getQuery(), pack.getParams(), new ResultSetHandler<List>() {
                @Override
                public List handle(ResultSet rs) throws SQLException {
                    List<Object[]> resList = new ArrayList<>();
                    DbTypeConverter typeConverter = DbmsType.getCurrent().getTypeConverter();

                    while (rs.next()) {
                        Object[] values = new Object[rs.getMetaData().getColumnCount()];
                        for (int columnIndex = 0; columnIndex < rs.getMetaData().getColumnCount(); columnIndex++) {
                            values[columnIndex] = typeConverter.getJavaObject(rs, columnIndex + 1);
                        }
                        resList.add(values);
                    }

                    return resList;
                }
            });
        } catch (SQLException e) {
            throw new ReportDataLoaderException(e);
        }

        outputParameters = parseQueryOutputParametersNames(query);
        return fillOutputData(resList, outputParameters);
    }
}