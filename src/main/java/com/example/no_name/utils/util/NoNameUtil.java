package com.example.no_name.utils.util;

public class NoNameUtil {

    /**
     * 입력한 Object 가 null 이면 true 반환
     * @param obj
     * @return boolean(입력한 Object 가 null 이면 true 반환)
     */
    public static boolean isEmpty(Object obj) {
        if (obj instanceof String) return isEmpty((String) obj);
        else return obj == null;
    }

    /**
     * 입력한 String의 값이 없으면 true 반환
     * @param str
     * @return boolean(입력한 String 의 값이 없으면 true 반환)
     */
    public static boolean isEmpty(String str) {
        if (str == null) return true;
        else {
            String trim = str.trim();
            return "".equals(trim) || "null".equals(trim);
        }
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

    public static boolean isNumberType(Object object) {
        return object instanceof Integer
                || object instanceof Float
                || object instanceof Double
                || object instanceof Long;
    }
}