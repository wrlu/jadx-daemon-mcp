package com.wrlus.jadx;

import com.wrlus.jadx.aidl.AidlClass;
import com.wrlus.jadx.aidl.ClassSearch;
import jadx.api.*;
import jadx.core.dex.instructions.args.ArgType;
import jadx.core.utils.android.AndroidManifestParser;
import jadx.core.xmlgen.ResContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class JadxInstance {
	private static final Logger logger = LoggerFactory.getLogger(JadxInstance.class);
	private JadxDecompiler decompiler;
    private final String filePath;
    private final Map<String, AidlClass> aidlCacheMap = new ConcurrentHashMap<>();

    public JadxInstance(String path) {
        this.filePath = path;
    }

    public void load() {
        File file = new File(filePath);
        if (!file.exists() || !file.isFile()) {
            logger.error("Invalid file path: {}", file.getAbsolutePath());
            return;
        }
        realLoad(Collections.singletonList(file));
    }

    public void loadDir() {
        File dir = new File(filePath);
        if (!dir.exists() || !dir.isDirectory()) {
            logger.error("Invalid directory path: {}", dir.getAbsolutePath());
            return;
        }

        File[] dirFiles = dir.listFiles();
        if (dirFiles == null) {
            logger.error("Permission denied or I/O error: {}", dir.getAbsolutePath());
            return;
        }

        List<File> dexFiles = new ArrayList<>();
        for (File f : dirFiles) {
            if (isAndroidFile(f.getPath())) {
                dexFiles.add(f);
            }
        }

        if (dexFiles.isEmpty()) {
            logger.warn("No valid Android files found in: {}", dir.getAbsolutePath());
            return;
        }
        realLoad(dexFiles);
    }


    private void realLoad(List<File> files) {
        if (files == null || files.isEmpty()) {
            return;
        }

        if (isLoaded()) {
            close();
        }

        JadxArgs jadxArgs = new JadxArgs();
        if (files.size() > 1) {
            jadxArgs.setInputFiles(files);
        } else {
            jadxArgs.setInputFile(files.getFirst());
        }

        // Disable dex checksum verify.
        Map<String, String> pluginOptions = new HashMap<>();
        pluginOptions.put("dex-input.verify-checksum", "false");
        jadxArgs.setPluginOptions(pluginOptions);

        decompiler = new JadxDecompiler(jadxArgs);
        try {
            decompiler.load();
            logger.info("Successfully loaded {} file(s).", files.size());
        } catch (Exception e) {
            logger.error("Failed to load files via JADX", e);
        }
    }

	public String getManifest() {
		if (!isLoaded()) return null;

		List<ResourceFile> resources = decompiler.getResources();
		ResourceFile manifest = AndroidManifestParser.getAndroidManifest(resources);

		if (manifest == null) {
			logger.error("AndroidManifest.xml not found.");
			return null;
		}

		ResContainer container = manifest.loadContent();
		return container.getText().getCodeStr();
	}

	public String getMethodDecompiledCode(String className, String methodName) {
		if (!isLoaded()) return null;

        JavaMethod method = findJavaMethod(className, methodName);

		return method != null ? method.getCodeStr() : null;
	}

	public String getSuperClass(String className) {
		if (!isLoaded()) return null;

        JavaClass cls = findJavaClass(className);

        if (cls != null) {
            ArgType superClassType = cls.getClassNode().getSuperClass();
            return superClassType != null ? superClassType.toString() : Object.class.getCanonicalName();
        }

		return null;
	}

	public List<String> getInterfaces(String className) {
		if (!isLoaded()) return null;

        JavaClass cls = findJavaClass(className);

        if (cls != null) {
            return Optional.ofNullable(cls.getClassNode().getInterfaces())
                    .orElseGet(Collections::emptyList)
                    .stream()
                    .map(ArgType::toString)
                    .toList();
        }

		return null;
	}

	public List<String> getClassMethods(String className) {
		if (!isLoaded()) return null;

        JavaClass cls = findJavaClass(className);

        if (cls != null) {
            return Optional.ofNullable(cls.getMethods())
                    .orElseGet(Collections::emptyList)
                    .stream()
                    .map(JavaMethod::toString)
                    .toList();
        }

		return null;
	}

	public List<String> getClassFields(String className) {
		if (!isLoaded()) return null;

        JavaClass cls = findJavaClass(className);

        if (cls != null) {
            return Optional.ofNullable(cls.getFields())
                    .orElseGet(Collections::emptyList)
                    .stream()
                    .map(JavaField::toString)
                    .toList();
        }

		return null;
	}

    public List<String> getMethodCallers(String className, String methodName) {
        if (!isLoaded()) return null;

        JavaMethod method = findJavaMethod(className, methodName);

        if (method != null) {
            return Optional.ofNullable(method.getUseIn())
                    .orElseGet(Collections::emptyList)
                    .stream()
                    .map(JavaNode::toString)
                    .collect(Collectors.toList());
        }

        return null;
    }

    public List<String> getClassCallers(String className) {
        if (!isLoaded()) return null;

        JavaClass cls = findJavaClass(className);

        if (cls != null) {
            return Optional.ofNullable(cls.getUseIn())
                    .orElseGet(Collections::emptyList)
                    .stream()
                    .map(JavaNode::toString)
                    .collect(Collectors.toList());
        }
        return null;
    }

    public List<String> getFieldCallers(String className, String fieldName) {
        if (!isLoaded()) return null;

        JavaField field = findJavaField(className, fieldName);

        if (field != null) {
            return Optional.ofNullable(field.getUseIn())
                    .orElseGet(Collections::emptyList)
                    .stream()
                    .map(JavaNode::toString)
                    .collect(Collectors.toList());
        }

        return null;
    }

    public List<String> getMethodOverrides(String className, String methodName) {
        if (!isLoaded()) return null;

        JavaMethod method = findJavaMethod(className, methodName);

        if (method != null) {
            List<JavaMethod> overrideRelatedMethods = method.getOverrideRelatedMethods();

            return Optional.ofNullable(overrideRelatedMethods)
                    .orElseGet(Collections::emptyList)
                    .stream()
                    .map(JavaMethod::toString)
                    .collect(Collectors.toList());
        }

        return null;
    }

    public List<String> searchAidlClasses() {
        if (!isLoaded()) return null;

        for (JavaClass cls : decompiler.getClassesWithInners()) {
            AidlClass aidlClass = AidlClass.fromInterface(cls);
            if (aidlClass != null) {
                aidlCacheMap.put(aidlClass.interfaceClassName, aidlClass);
            }
        }
        return aidlCacheMap.keySet().stream().toList();
    }

    private AidlClass findAidlClass(String aidlClassName) {
        AidlClass cachedAidl = aidlCacheMap.get(aidlClassName);
        if (cachedAidl != null) {
            return cachedAidl;
        }

        AidlClass foundAidlClass = Optional.ofNullable(findJavaClass(aidlClassName))
                .flatMap(javaClass -> Optional.ofNullable(AidlClass.fromInterface(javaClass)))
                .orElse(null);

        if (foundAidlClass != null) {
            aidlCacheMap.put(aidlClassName, foundAidlClass);
        }

        return foundAidlClass;
    }

    public List<String> getAidlMethods(String aidlClassName) {
        if (!isLoaded()) return null;

        AidlClass aidlClass = findAidlClass(aidlClassName);

        return aidlClass != null ? aidlClass.getAidlMethods() : null;
    }

    public String getAidlImplClass(String aidlClassName) {
        if (!isLoaded()) return null;

        ClassSearch classSearcher = new ClassSearch(decompiler.getClassesWithInners());

        AidlClass aidlClass = findAidlClass(aidlClassName);

        return Optional.ofNullable(aidlClass)
                .flatMap(ac -> Optional.ofNullable(ac.findImpl(classSearcher, false)))
                .map(JavaClass::getFullName)
                .orElse(null);
    }

    private JavaClass findJavaClass(String className) {
        for (JavaClass cls : decompiler.getClassesWithInners()) {
            if (cls.getFullName().equals(className)) {
                return cls;
            }
        }
        return null;
    }

    private JavaMethod findJavaMethod(String className, String methodName) {
        JavaClass cls = findJavaClass(className);
        if (cls != null) {
            for (JavaMethod mtd : cls.getMethods()) {
                if (methodName.equals(mtd.toString())) {
                    return mtd;
                }
            }
        }
        return null;
    }

    private JavaField findJavaField(String className, String fieldName) {
        JavaClass cls = findJavaClass(className);
        if (cls != null) {
            for (JavaField field : cls.getFields()) {
                if (fieldName.equals(field.toString())) {
                    return field;
                }
            }
        }
        return null;
    }

	public boolean isLoaded() {
		return decompiler != null;
	}

    public String getFilePath() {
        return filePath;
    }

	public void close() {
		decompiler.close();
		decompiler = null;
	}

	private static boolean isAndroidFile(String path) {
		return path.endsWith(".apk") ||
				path.endsWith(".dex") ||
				path.endsWith(".jar");
	}
}
