package com.example.no_name.utils.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.relational.core.sql.In;
import org.springframework.security.core.parameters.P;

public class NoNameUtil {
    private static final Logger logger = LoggerFactory.getLogger(NoNameUtil.class);

    /**
     * 입력한 Object 가 null 이면 true 반환
     * @param obj
     * @return boolean(입력한 Object 가 null 이면 true 반환)
     */
    public static boolean isEmpty(Object obj) {
        if (obj instanceof String && isEmpty(obj.toString())) return true;
        else if (obj == null) return true;
        else return false;
    }

    /**
     * 입력한 String의 값이 없으면 true 반환
     * @param str
     * @return boolean(입력한 String 의 값이 없으면 true 반환)
     */
    public static boolean isEmpty(String str) {
        if (str == null) return true;
        if ("".equals(str.trim()) || "null".equals(str.trim()) || (str.trim()).length() == 0) return true;
        return false;
    }

    /**
     * 값이 없으면 null 대신 공백 반환
     * @param str
     * @return String(값이 없으면 null 대신 공백 반환)
     */
    public static String nvl(String str) {
        return nvl(str, "");
    }

    /**
     * 값이 없으면 replaceStr의 값으로 치완하여 반환
     * @param str
     * @param replaceStr
     * @return String(값이 없으면 replaceStr 의 값으로 차완하여 반환)
     */
    public static String nvl(String str, String replaceStr) {
        if(isEmpty(str)) return replaceStr;
        else return str;
    }

    public static boolean isNumberTypeClass(Object object) {
        if (object instanceof Integer) return true;
        else if (object instanceof Float) return true;
        else if (object instanceof Double) return true;
        else if (object instanceof Long) return true;
        else return false;
    }
}