package com.booking.android.parcelable_plugin.typeserializers;

import java.util.HashMap;
import java.util.Map;

public class MbSerializer implements TypeSerializer {
    private BundleKeys bundleKeys;
    private final Map<String, String> methods = new HashMap<>();

    public MbSerializer(BundleKeys bundleKeys) {
        methods.put("byte", "getByte");
        methods.put("double", "getDouble");
        methods.put("float", "getFloat");
        methods.put("short", "getShort");
        methods.put("int", "getInt");
        methods.put("long", "getLong");
        methods.put("boolean", "getBoolean");
        methods.put("char", "getChar");

        this.bundleKeys = bundleKeys;
    }

    @Override
    public String writeValue(SerializableValue field, String parcel, String flags) {
        String key = bundleKeys.getKeyForField(field.getSimpleName());
        return parcel + ".put(" + key + ", " + field.getName() + ");";
    }

    @Override
    public String readValue(SerializableValue field, String parcel) {
        String fieldClass = field.getType().getCanonicalText();
        String method = methods.get(fieldClass);
        String key = bundleKeys.getKeyForField(field.getSimpleName());

        String out = field.getName() + " = ";

        if (method == null) {
            out += parcel + ".get(" + key + ", " + fieldClass + ".class);";
        } else {
            out += parcel + "." + method + "(" + key + ");";
        }

        return out;
    }
}
