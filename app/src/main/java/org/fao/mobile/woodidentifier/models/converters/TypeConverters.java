package org.fao.mobile.woodidentifier.models.converters;

import androidx.room.TypeConverter;

import org.fao.mobile.woodidentifier.utils.StringUtils;
import org.json.JSONException;

import java.util.Arrays;
import java.util.stream.Collectors;

public class TypeConverters {
    @TypeConverter
    public static String[] stringToArray(String str)  {
        try {
            return StringUtils.split(str).toArray(new String[0]);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return new String[0];
    }

    @TypeConverter
    public static String stringArrayToString(String[] strs) {
        return StringUtils.join(Arrays.asList(strs.clone()));
    }

    @TypeConverter
    public static Float[] stringToFloatArray(String str)  {
        try {
            return StringUtils.split(str).toArray(new Float[0]);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return new Float[0];
    }

    @TypeConverter
    public static String arrayToString(Float[] f) {
        return StringUtils.join(Arrays.asList(f.clone()));
    }
}
