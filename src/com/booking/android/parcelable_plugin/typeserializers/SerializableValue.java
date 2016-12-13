package com.booking.android.parcelable_plugin.typeserializers;

import com.intellij.psi.PsiField;
import com.intellij.psi.PsiType;

public abstract class SerializableValue {
    public abstract PsiType getType();

    public abstract String getName();

    public static SerializableValue member(PsiField field) {
        return new MemberSerializableValue(field);
    }

    public abstract String getSimpleName();

    private static class MemberSerializableValue extends SerializableValue {
        private final PsiField field;

        public MemberSerializableValue(PsiField field) {
            this.field = field;
        }

        @Override
        public PsiType getType() {
            return field.getType();
        }

        @Override
        public String getName() {
            return "this." + field.getName();
        }

        @Override
        public String getSimpleName() {
            return field.getName();
        }
    }
}
