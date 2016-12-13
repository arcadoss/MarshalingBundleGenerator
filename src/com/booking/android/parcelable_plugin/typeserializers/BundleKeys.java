package com.booking.android.parcelable_plugin.typeserializers;

import com.booking.android.parcelable_plugin.CodeGenerator;
import com.google.common.base.CaseFormat;
import com.intellij.psi.PsiClass;
import org.apache.commons.lang.WordUtils;

import java.util.HashMap;
import java.util.Map;

public final class BundleKeys {

    private Map<String, String> namesMap = new HashMap<>();

    public String getKeyForField(String field) {
        String key = namesMap.get(field);

        if (key == null) {
            key = generateKey(field);
            namesMap.put(field, key);
        }

        return CodeGenerator.INTERFACE_NAME + "." + key;
    }

    private String generateKey(String name) {
        return CaseFormat.LOWER_CAMEL.to(CaseFormat.UPPER_UNDERSCORE, name);
    }

    public static BundleKeys readInterfaceFromClass(PsiClass psiClass) {
        PsiClass[] classes = psiClass.getInnerClasses();

        for (PsiClass cl : classes) {
            if (cl.getName().equals(CodeGenerator.INTERFACE_NAME)) {
                return new BundleKeys();
            }
        }
        return new BundleKeys();
    }
    public Map<String, String> getNamesMap() {
        return namesMap;
    }
}
