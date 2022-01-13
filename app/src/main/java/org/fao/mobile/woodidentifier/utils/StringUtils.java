package org.fao.mobile.woodidentifier.utils;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

public class StringUtils {
    public static <T> String joinAsJson(List<T> list) {
        JSONArray jsonArray = new JSONArray();
        list.stream().forEach(x -> jsonArray.put(x));
        return jsonArray.toString();
    }

    public static <T> T[] split(String s, T[] arr) throws JSONException {
        ArrayList<T> stringArrayList = new ArrayList<T>();
        JSONArray jsonArray = new JSONArray(s);
        for(int i = 0; i < jsonArray.length(); i++) {
            stringArrayList.add((T)jsonArray.get(i));
        }
        return stringArrayList.toArray(arr);
    }

    public static float[] splitToFloatList(String string) {
        StringTokenizer stringTokenizer = new StringTokenizer(string,",");
        float[] values = new float[stringTokenizer.countTokens()];
        int i = 0;
        while(stringTokenizer.hasMoreTokens()) {
            values[i++] = Float.parseFloat(stringTokenizer.nextToken());
        }
        return values;
    }
}
