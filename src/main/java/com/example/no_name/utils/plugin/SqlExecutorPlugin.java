package com.example.no_name.utils.plugin;

import com.example.no_name.utils.util.NoNameUtil;
import io.micrometer.core.instrument.Timer;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.mapping.ParameterMode;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.type.TypeHandlerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

import java.lang.reflect.Executable;
import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 *
 */
@Intercepts({
        @Signature(type = Executable.class, method = "update", args = {MappedStatement.class, Object.class}),
        @Signature(type = Executable.class, method = "query", args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class, CacheKey.class, BoundSql.class}),
        @Signature(type = Executable.class, method = "query", args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class}),
        @Signature(type = Executable.class, method = "queryCursor", args = {MappedStatement.class, Object.class, RowBounds.class})

})
public class SqlExecutorPlugin implements Interceptor {
    private static final Logger logger = LoggerFactory.getLogger(SqlExecutorPlugin.class);
    private Properties pluginPropteries = null;
    private boolean isLoggingParamSql = false;
    private PrometheusMeterRegistry prometheusMeterRegistry;

    /**
     *
     */
    @Deprecated
    public SqlExecutorPlugin() {
        initPlugin(null);
    }

    /**
     * @param ctx
     */
    public SqlExecutorPlugin(ApplicationContext ctx) {
        initPlugin(ctx);
    }

    /**
     * @param ctx
     */
    private void initPlugin(ApplicationContext ctx) {
        if (ctx != null) {
            prometheusMeterRegistry = ctx.getBean(PrometheusMeterRegistry.class);
        }
        if (logger.isDebugEnabled()) {
            this.isLoggingParamSql = true;
        }
    }

    /**
     * @param invocation
     * @return
     */
    @Override
    public Object intercept(Invocation invocation) {
        Object[] args = invocation.getArgs();
        MappedStatement ms = (MappedStatement) args[0];
        Object param = (Object) args[1];
        String msId = ms.getId();
        Timer sqlTimer = null;
        if (prometheusMeterRegistry != null) {
            sqlTimer = prometheusMeterRegistry.timer("sql_exec", "sqlId", msId, "type", ms.getSqlCommandType().toString());
        }
        long startTs = System.currentTimeMillis();
        Object processResult = null;
        try {
            BoundSql boundSql = ms.getBoundSql(param);
            TypeHandlerRegistry typeHandlerRegistry = ms.getConfiguration().getTypeHandlerRegistry();
            String boundsSql = getParameterBoundsSql(ms, typeHandlerRegistry, boundSql, param);

            logger.info("[START][SQL][{}][-]. [{}] \n\tquery=[{}]", msId, ms.getSqlCommandType().name(), boundsSql);

            if (isLoggingParamSql) {
                boolean sqlIdDebugEnable = LoggerFactory.getLogger(msId).isDebugEnabled();
                if (sqlIdDebugEnable) {
                    String msResource = ms.getResource();
                    logger.debug("Binding SQL : {} @ {}\n{}", msId, msResource, beautifyRawSql(msId, boundsSql));
                }
            }
            processResult = invocation.proceed();
        } catch (Exception e) {
            logger.error("[EXCEPION][SQL][{}][-]. {}", msId, e.getMessage());
        } finally {
            long finTs = System.currentTimeMillis();
            long elaspedMs = finTs - startTs;
            if (sqlTimer != null) {
                sqlTimer.record(elaspedMs, TimeUnit.MILLISECONDS);
            }
            logger.info("[FINISH][SQL][{}][{}]. [{}]", msId, elaspedMs, ms.getSqlCommandType().name());
        }
        return processResult;
    }

    /**
     * @param target
     * @return
     */
    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }

    /**
     * @param properties
     */
    @Override
    public void setProperties(Properties properties) {
        if (properties == null) {
            return;
        }
        pluginPropteries = new Properties();
        properties.forEach((i, v) -> {
            pluginPropteries.put(i, v);
        });
    }

    /**
     * @param enable
     */
    public void setParamSqlLoggingEnable(boolean enable) {
        this.isLoggingParamSql = enable;
    }

    /**
     * @param mappedStatement
     * @param typeHandlerRegistry
     * @param boundSql
     * @param paramObj
     * @return
     */
    private String getParameterBoundsSql(MappedStatement mappedStatement, TypeHandlerRegistry typeHandlerRegistry, BoundSql boundSql, Object paramObj) {
        String orgSql = boundSql.getSql();
        String bindSql = new String(orgSql);
        try {
            if (paramObj == null) {
                logger.debug("Query param is null");
                bindSql = bindSql.replaceFirst("\\?", "''");
            } else {
                logger.debug("Query param is {}", paramObj.getClass().getCanonicalName());
                List<ParameterMapping> mappings = boundSql.getParameterMappings();

                if (NoNameUtil.isNumberTypeClass(paramObj)) {
                    bindSql = bindSql.replaceFirst("\\?", paramObj.toString());
                } else if (paramObj instanceof String) {
                    bindSql = bindSql.replaceFirst("\\?", "'" + paramObj + "'");
                } else {
                    int fromIndex = 0;

                    for (ParameterMapping mapping : mappings) {
                        Object value = getParamValue(mapping, boundSql, typeHandlerRegistry, mappedStatement);
                        if (value == null) {
                            bindSql = bindSql.replaceFirst("\\?", "null");
                        } else {
                            int qidx = bindSql.indexOf("?", fromIndex);
                            if (qidx < 0) {
                                break;
                            }

                            if (NoNameUtil.isNumberTypeClass(value)) {
                                bindSql = replaceFirstQuestionMark(bindSql, qidx, value.toString());
                                fromIndex = qidx + (value.toString().length());
                            } else {
                                bindSql = replaceFirstQuestionMark(bindSql, qidx, "" + value + "");
                                fromIndex = qidx + (value.toString().length()) + 2;
                            }

                            if (fromIndex >= bindSql.length()) {
                                break;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            bindSql = "[SQL Query log Exception:" + e.toString() + " " + e.getMessage() + "]" + orgSql;
        }
        return bindSql;
    }

    /**
     * @param orgSql
     * @param markIndex
     * @param replaceValue
     * @return
     */
    private String replaceFirstQuestionMark(String orgSql, int markIndex, String replaceValue) {
        StringBuilder sqlBuilder = new StringBuilder();
        sqlBuilder.replace(markIndex, markIndex + 1, replaceValue);
        return sqlBuilder.toString();
    }

    /**
     * @param parameterMapping
     * @param boundSql
     * @param typeHandlerRegistry
     * @param mappedStatement
     * @return
     * @throws Exception
     */
    private Object getParamValue(ParameterMapping parameterMapping, BoundSql boundSql, TypeHandlerRegistry typeHandlerRegistry, MappedStatement mappedStatement) throws Exception {
        Object value = null;
        Object paramObject = boundSql.getParameterObject();
        if (parameterMapping.getMode() != ParameterMode.OUT) {
            String propertyName = parameterMapping.getProperty();
            if (boundSql.hasAdditionalParameter(propertyName)) {
                value = boundSql.getAdditionalParameter(propertyName);
            } else if (paramObject == null) {
                value = null;
            } else if (typeHandlerRegistry.hasTypeHandler(paramObject.getClass())) {
                value = paramObject;
            } else {
                MetaObject metaObject = mappedStatement.getConfiguration().newMetaObject(paramObject);
                value = metaObject.getValue(propertyName);
            }
        }
        return value;
    }

    /**
     * @param sqlId
     * @param rawSql
     * @return
     */
    private String beautifyRawSql(String sqlId, String rawSql) {
        StringBuilder builder = new StringBuilder();
        List<String> sqlTextList = new ArrayList<>();
        try {
            StringTokenizer tokenizer = new StringTokenizer(rawSql, "\n");
            String str = "";
            int minPrefixBlank = 99999;

            while (tokenizer.hasMoreTokens()) {
                str = tokenizer.nextToken();
                if (getStringTrimmingLength(str) < 1) {
                    continue;
                }
                String convertStr = convertTabToSpace(str);
                int blankSize = getPrifixBlankCount(convertStr);
                if (blankSize > 0 && blankSize < minPrefixBlank) {
                    minPrefixBlank = blankSize;
                }
                sqlTextList.add(convertStr);
            }

            builder.append(TAB_BLANK).append("/* ").append(sqlId).append(" */\n");

            for (int i = 0; i < sqlTextList.size(); i++) {
                builder.append(TAB_BLANK);
                String strSingle = sqlTextList.get(i);
                if (strSingle.charAt(0) == BLANK) {
                    builder.append(strSingle.substring(minPrefixBlank));
                } else {
                    builder.append(strSingle);
                }
                builder.append("\n");
            }
        } catch (Exception e) {
            logger.error(e.getMessage());
            builder = new StringBuilder(rawSql);
        }
        return builder.toString();
    }

    private final char BLANK = ' ';
    private final String TAB_BLANK = "    ";

    /**
     * @param arg
     * @return
     */
    private int getPrifixBlankCount(String arg) {
        int len = arg.length();
        int st = 0;
        while ((st < len) && (arg.charAt(st) == BLANK)) {
            st++;
        }
        return st;
    }

    /**
     * @param arg
     * @return
     */
    private int getStringTrimmingLength(String arg) {
        if (arg == null) {
            return 0;
        }
        return arg.trim().length();
    }


    private String convertTabToSpace(String string) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < string.length(); i++) {
            char ch = string.charAt(i);
            switch (ch) {
                case '\n':
                    break;
                case '\r':
                    break;
                case '\t':
                    builder.append(TAB_BLANK);
                    break;
                default:
                    builder.append(ch);
                    break;
            }
        }
        return builder.toString();
    }
}