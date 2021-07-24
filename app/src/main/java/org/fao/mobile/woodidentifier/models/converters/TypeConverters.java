package org.fao.mobile.woodidentifier.models.converters;

import androidx.room.TypeConverter;

import org.fao.mobile.woodidentifier.utils.StringUtils;
import org.json.JSONException;

import java.util.Arrays;

public class TypeConverters {
    @TypeConverter
    public static String[] stringToArray(String str)  {
        try {
            return StringUtils.split(str, new String[0]);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return new String[0];
    }

    @TypeConverter
    public static String stringArrayToString(String[] strs) {
        return StringUtils.joinAsJson(Arrays.asList(strs.clone()));
    }

    @TypeConverter
    public static Double[] stringToDoubleArray(String str)  {
        try {
            return StringUtils.split(str, new Double[0]);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return new Double[0];
    }

    @TypeConverter
    public static Integer[] stringToIntArray(String str)  {
        try {
            return StringUtils.split(str, new Integer[0]);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return new Integer[0];
    }

    @TypeConverter
    public static String arrayToInt(Integer[] f) {
        return StringUtils.joinAsJson(Arrays.asList(f.clone()));
    }

    @TypeConverter
    public static String arrayToString(Double[] f) {
        return StringUtils.joinAsJson(Arrays.asList(f.clone()));
    }

}