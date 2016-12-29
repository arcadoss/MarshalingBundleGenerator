package com.booking.android.parcelable_plugin;

import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.booking.android.parcelable_plugin.typeserializers.*;
import com.booking.android.parcelable_plugin.typeserializers.MbSerializer;
import com.intellij.psi.impl.source.tree.java.PsiJavaTokenImpl;
import com.intellij.psi.search.PsiShortNamesCache;
import org.apache.commons.lang.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CodeGenerator {
    public static final String CREATOR_NAME = "CREATOR";
    public static final String TYPE_PARCEL = "android.os.Parcel";
    private static String M_BUNDLE = "com.booking.common.util.MarshalingBundle";
    public static final String INTERFACE_NAME = "BundleKey";

    private final PsiClass mClass;
    private final List<PsiField> mFields;
    private final BundleKeys bundleKeys;
    private final MbSerializer serializer;

    public CodeGenerator(PsiClass psiClass, List<PsiField> fields) {
        mClass = psiClass;
        mFields = fields;

        bundleKeys = BundleKeys.readInterfaceFromClass(psiClass);
        serializer = new MbSerializer(bundleKeys);
    }

    private String generateStaticCreator(PsiClass psiClass) {
        StringBuilder sb = new StringBuilder("public static final android.os.Parcelable.Creator<");

        String className = psiClass.getName();

        sb.append(className).append("> CREATOR = new android.os.Parcelable.Creator<").append(className).append(">(){")
                .append("@Override ")
                .append("public ").append(className).append(" createFromParcel(android.os.Parcel source) {")
                .append("return new ").append(className).append("(source);}")
                .append("@Override ")
                .append("public ").append(className).append("[] newArray(int size) {")
                .append("return new ").append(className).append("[size];}")
                .append("};");
        return sb.toString();
    }

    private String generateConstructor(List<PsiField> fields, PsiClass psiClass) {
        String className = psiClass.getName();

        StringBuilder sb = new StringBuilder("protected ");

        // Create the Parcelable-required constructor
        sb.append(className).append("(android.os.Parcel in) {");

        if (hasParcelableSuperclass() && hasParcelableSuperConstructor()) {
            sb.append("super(in);");
        }

        String clLoader = className + ".class.getClassLoader()";
        sb.append(M_BUNDLE).append(" bundle = new ").append(M_BUNDLE).append("(in.readBundle(").append(clLoader).append("), ").append(clLoader).append(");");

        // Creates all of the deserialization methods for the given fields
        for (PsiField field : fields) {
            sb.append(serializer.readValue(SerializableValue.member(field), "bundle"));
        }

        sb.append("}");
        return sb.toString();
    }

    private boolean hasParcelableSuperConstructor() {
        PsiMethod[] constructors = mClass.getSuperClass() != null ? mClass.getSuperClass().getConstructors() : new PsiMethod[0];
        for (PsiMethod constructor : constructors) {
            PsiParameterList parameterList = constructor.getParameterList();
            if (parameterList.getParametersCount() == 1
                    && parameterList.getParameters()[0].getType().getCanonicalText().equals(TYPE_PARCEL)) {
                return true;
            }
        }
        return false;
    }

    private String generateWriteToParcel(List<PsiField> fields, PsiClass mClass) {
        StringBuilder sb = new StringBuilder("@Override public void writeToParcel(android.os.Parcel dest, int flags) {");
        if (hasParcelableSuperclass() && hasSuperMethod("writeToParcel")) {
            sb.append("super.writeToParcel(dest, flags);");
        }

        if (!fields.isEmpty()) {
            sb.append(M_BUNDLE).append(" bundle = new ").append(M_BUNDLE).append("(").append(mClass.getName())
                    .append(".class.getClassLoader());");

            for (PsiField field : fields) {
                sb.append(serializer.writeValue(SerializableValue.member(field), "bundle", ""));
            }

            sb.append("dest.writeBundle(bundle.toBundle());");
            sb.append("}");
        }

        return sb.toString();
    }

    private boolean hasSuperMethod(String methodName) {
        if (methodName == null) return false;

        PsiMethod[] superclassMethods = mClass.getSuperClass() != null ? mClass.getAllMethods() : new PsiMethod[0];
        for (PsiMethod superclassMethod : superclassMethods) {
            if (superclassMethod.getBody() == null) continue;

            String name = superclassMethod.getName();
            if (name != null && name.equals(methodName)) {
                return true;
            }
        }
        return false;
    }

    private String generateDescribeContents() {
        return "@Override public int describeContents() { return 0; }";
    }

    public void generate() {
        PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(mClass.getProject());

        removeExistingParcelableImplementation(mClass);

        // Describe contents method
        PsiMethod describeContentsMethod = elementFactory.createMethodFromText(generateDescribeContents(), mClass);
        // Method for writing to the parcel
        PsiMethod writeToParcelMethod = elementFactory.createMethodFromText(generateWriteToParcel(mFields, mClass), mClass);

        // Default constructor if needed
        String defaultConstructorString = generateDefaultConstructor(mClass);
        PsiMethod defaultConstructor = null;

        if (defaultConstructorString != null) {
            defaultConstructor = elementFactory.createMethodFromText(defaultConstructorString, mClass);
        }

        // Constructor
        PsiMethod constructor = elementFactory.createMethodFromText(generateConstructor(mFields, mClass), mClass);
        // CREATOR
        PsiField creatorField = elementFactory.createFieldFromText(generateStaticCreator(mClass), mClass);

        JavaCodeStyleManager styleManager = JavaCodeStyleManager.getInstance(mClass.getProject());

        // Shorten all class references
        styleManager.shortenClassReferences(mClass.addBefore(describeContentsMethod, mClass.getLastChild()));
        styleManager.shortenClassReferences(mClass.addBefore(writeToParcelMethod, mClass.getLastChild()));

        // Only adds if available
        if (defaultConstructor != null) {
            styleManager.shortenClassReferences(mClass.addBefore(defaultConstructor, mClass.getLastChild()));
        }

        updateKeysInterface(elementFactory, mClass);

        styleManager.shortenClassReferences(mClass.addBefore(constructor, mClass.getLastChild()));
        styleManager.shortenClassReferences(mClass.addBefore(creatorField, mClass.getLastChild()));

        makeClassImplementParcelable(elementFactory);
    }

    private void updateKeysInterface(PsiElementFactory elementFactory, PsiClass mClass) {

        PsiClass keysInterface = null;

        for (PsiClass psiClass : this.mClass.getInnerClasses()) {
            if (StringUtils.equals(psiClass.getName(), INTERFACE_NAME) && psiClass.isInterface()) {
                keysInterface = psiClass;
                break;
            }
        }

        boolean newInterface = false;
        if (keysInterface == null) {
            newInterface = true;
            keysInterface = elementFactory.createInterface(INTERFACE_NAME);
        }

        PsiField[] fields = keysInterface.getFields();
        Map<String, PsiField> existingKeys = new HashMap<>(fields.length);
        for (PsiField field : fields) {
            existingKeys.put(field.getName(), field);
        }

        Map<String, String> generatedKeys = bundleKeys.getNamesMap();

        for (Map.Entry<String, String> pair : generatedKeys.entrySet()) {
            String fieldName = pair.getKey();
            String key = pair.getValue();

            PsiField field = existingKeys.get(key);
            if (field == null) {
                keysInterface.add(elementFactory.createFieldFromText("String " + key + " = " + "\"" + fieldName + "\";", keysInterface));
            } else if (field.getInitializer() == null) {
                PsiExpression init = elementFactory.createExpressionFromText("\"" + fieldName + "\"", field);
                field.setInitializer(init);
            }
        }

        if (newInterface) {
            mClass.add(keysInterface);
        }
    }

    private boolean hasParcelableSuperclass() {
        PsiClassType[] superTypes = mClass.getSuperTypes();
        for (PsiClassType superType : superTypes) {
            if (PsiUtils.isOfType(superType, "android.os.Parcelable")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Strips the
     *
     * @param psiClass
     */
    private void removeExistingParcelableImplementation(PsiClass psiClass) {
        PsiField[] allFields = psiClass.getAllFields();

        // Look for an existing CREATOR and remove it
        for (PsiField field : allFields) {
            if (field.getName().equals(CREATOR_NAME)) {
                // Creator already exists, need to remove/replace it
                field.delete();
            }
        }

        findAndRemoveMethod(psiClass, psiClass.getName(), TYPE_PARCEL);
        findAndRemoveMethod(psiClass, "describeContents");
        findAndRemoveMethod(psiClass, "writeToParcel", TYPE_PARCEL, "int");
    }

    private String generateDefaultConstructor(PsiClass clazz) {
        // Check for any constructors; if none exist, we'll make a default one
        if (clazz.getConstructors().length == 0) {
            // No constructors exist, make a default one for convenience
            return "public " + clazz.getName() + "(){}" + '\n';
        } else {
            return null;
        }
    }

    private void makeClassImplementParcelable(PsiElementFactory elementFactory) {
        if (hasParcelableSuperclass()) return;

        final PsiClassType[] implementsListTypes = mClass.getImplementsListTypes();
        final String implementsType = "android.os.Parcelable";

        for (PsiClassType implementsListType : implementsListTypes) {
            PsiClass resolved = implementsListType.resolve();

            // Already implements Parcelable, no need to add it
            if (resolved != null && implementsType.equals(resolved.getQualifiedName())) {
                return;
            }
        }

        PsiJavaCodeReferenceElement implementsReference = elementFactory.createReferenceFromText(implementsType, mClass);
        PsiReferenceList implementsList = mClass.getImplementsList();

        if (implementsList != null) {
            implementsList.add(implementsReference);
        }
    }


    private static void findAndRemoveMethod(PsiClass clazz, String methodName, String... arguments) {
        // Maybe there's an easier way to do this with mClass.findMethodBySignature(), but I'm not an expert on Psi*
        PsiMethod[] methods = clazz.findMethodsByName(methodName, false);

        for (PsiMethod method : methods) {
            PsiParameterList parameterList = method.getParameterList();

            if (parameterList.getParametersCount() == arguments.length) {
                boolean shouldDelete = true;

                PsiParameter[] parameters = parameterList.getParameters();

                for (int i = 0; i < arguments.length; i++) {
                    if (!parameters[i].getType().getCanonicalText().equals(arguments[i])) {
                        shouldDelete = false;
                    }
                }

                if (shouldDelete) {
                    method.delete();
                }
            }
        }
    }
}
