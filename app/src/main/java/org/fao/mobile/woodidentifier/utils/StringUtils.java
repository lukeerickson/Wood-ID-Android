package org.fao.mobile.woodidentifier.utils;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;

public class StringUtils {
    public static <T> String join(List<T> list) {
        JSONArray jsonArray = new JSONArray();
        list.stream().forEach(x -> jsonArray.put(x));
        return jsonArray.toString();
    }

    public static <T> List<T> split(String s) throws JSONException {
        ArrayList<T> stringArrayList = new ArrayList<T>();
        JSONArray jsonArray = new JSONArray(s);
        for(int i = 0; i < jsonArray.length(); i++) {
            stringArrayList.add((T)jsonArray.get(0));
        }
        return stringArrayList;
    }
}
