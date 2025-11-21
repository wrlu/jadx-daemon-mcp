package com.wrlus.jadx;

import jadx.core.utils.Utils;
import org.objectweb.asm.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class SignatureConverter {
    private static final Logger logger = LoggerFactory.getLogger(SignatureConverter.class);

    private static final Pattern CLASS_DESCRIPTOR_PATTERN =
            Pattern.compile("^(L[^;]+;)");

    private static final Pattern METHOD_SIGNATURE_PATTERN =
            Pattern.compile("(L[^;]+;)->([^(]+)(\\(([^)]*)\\)(.+))");

    private static final Pattern FIELD_SIGNATURE_PATTERN =
            Pattern.compile("(L[^;]+;)->([^:]+):(.+)");


    public static String extractJavaClassFQN(String jvmSignature) {
        if (jvmSignature == null || jvmSignature.isEmpty()) {
            logger.error("Invalid JVM signature format: {}", jvmSignature);
            return null;
        }

        Matcher matcher = CLASS_DESCRIPTOR_PATTERN.matcher(jvmSignature);

        if (matcher.find()) {
            String classDescriptor = matcher.group(1);
            return toJavaClassSignature(classDescriptor);
        }

        logger.error("Invalid JVM signature format: {}", jvmSignature);
        return null;
    }

    /**
     * 将 JVM 类描述符转换为 Java FQN。
     * * @param jvmClassDescriptor JVM 类描述符字符串。
     * @return Java FQN 字符串，如果格式无效则返回 null。
     */
    public static String toJavaClassSignature(String jvmClassDescriptor) {
        try {
            Type classType = Type.getType(jvmClassDescriptor);

            return getReadableClassName(classType);
        } catch (IllegalArgumentException e) {
            System.err.println("Error parsing class descriptor: " + jvmClassDescriptor + " - " + e.getMessage());
            return null;
        }
    }

    /**
     * 将 JVM 完全方法签名转换为 Java 方法签名格式。
     * e.g., Lcom/example/abc;->testMethod(Ljava/lang/String;I)V
     * -> com.example.abc.testMethod(java.lang.String, int):void
     *
     * @param jvmMethodSignature JVM 方法引用字符串。
     * @return Java 方法签名字符串，如果格式无效则返回 null。
     */
    public static String toJavaMethodSignature(String jvmMethodSignature) {
        Matcher matcher = METHOD_SIGNATURE_PATTERN.matcher(jvmMethodSignature);

        if (!matcher.matches()) {
            logger.error("Invalid JVM method signature format: {}", jvmMethodSignature);
            return null;
        }

        // 类名
        String classDescriptor = matcher.group(1);
        String className = toJavaClassSignature(classDescriptor);

        if (className == null) {
            return null;
        }

        // 方法名
        String methodName = matcher.group(2);

        // 方法描述符
        String methodDescriptor = matcher.group(3);

        // 返回值类型
        Type returnType = Type.getReturnType(methodDescriptor);
        String javaReturnType = getReadableClassName(returnType);

        // 参数类型列表
        Type[] argumentTypes = Type.getArgumentTypes(methodDescriptor);

        List<String> argumentTypeList = Arrays.stream(argumentTypes)
                .map(SignatureConverter::getReadableClassName).toList();
        String javaParameters = Utils.listToString(argumentTypeList);

        return String.format("%s.%s(%s):%s",
                className,
                methodName,
                javaParameters,
                javaReturnType);
    }

    /**
     * 将 JVM 完全字段签名转换为 Java 字段签名格式。
     * * @param jvmFieldSignature JVM 字段引用字符串。
     * @return Java 字段简洁签名字符串，如果格式无效则返回 null。
     */
    public static String toJavaFieldSignature(String jvmFieldSignature) {
        Matcher matcher = FIELD_SIGNATURE_PATTERN.matcher(jvmFieldSignature);

        if (!matcher.matches()) {
            System.err.println("Invalid JVM field signature format: " + jvmFieldSignature);
            return null;
        }

        // 类名
        String classDescriptor = matcher.group(1);
        String className = toJavaClassSignature(classDescriptor);

        if (className == null) {
            return null;
        }

        // 字段名
        String fieldName = matcher.group(2);

        return String.format("%s.%s", className, fieldName);
    }

    private static String getReadableClassName(Type type) {
        return switch (type.getSort()) {
            case Type.VOID -> "void";
            case Type.BOOLEAN -> "boolean";
            case Type.CHAR -> "char";
            case Type.BYTE -> "byte";
            case Type.SHORT -> "short";
            case Type.INT -> "int";
            case Type.FLOAT -> "float";
            case Type.LONG -> "long";
            case Type.DOUBLE -> "double";
            case Type.OBJECT -> type.getClassName();
            case Type.ARRAY -> {
                String baseType = getReadableClassName(type.getElementType());
                yield baseType + "[]".repeat(Math.max(0, type.getDimensions()));
            }
            default -> "UNKNOWN_TYPE";
        };
    }
}