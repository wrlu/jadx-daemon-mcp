package com.wrlus.jadx;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.json.JsonMapper;
import org.jetbrains.annotations.NotNull;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class McpServer {
	private static final Logger logger = LoggerFactory.getLogger(McpServer.class);
	private static final int DEFAULT_MAX_JADX_INSTANCE_COUNT = 1;
	private Javalin app;
	private final String host;
	private final int port;

	private final Map<String, JadxInstance> jadxInstanceMap = new HashMap<>();
	private int maxInstanceCount = DEFAULT_MAX_JADX_INSTANCE_COUNT;

	public McpServer(String host, int port) {
		this.host = host;
		this.port = port;
	}

	public void start() {
        stop();

        Gson gson = new GsonBuilder().create();
		JsonMapper gsonMapper = new JsonMapper() {
			@NotNull
			@Override
			public String toJsonString(@NotNull Object obj, @NotNull Type type) {
				return gson.toJson(obj, type);
			}

			@NotNull
			@Override
			public <T> T fromJsonString(@NotNull String json, @NotNull Type targetType) {
				return gson.fromJson(json, targetType);
			}
		};
		app = Javalin.create(config -> {
            /* Health checker API */
            config.routes.get("/health", this::handleHealth);

            /* Android binary loader API */
            config.routes.get("/load", this::handleLoad);
            config.routes.get("/load_dir", this::handleLoadDir);
            config.routes.get("/lookup_instance_id", this::handleLookupInstanceId);
            config.routes.get("/unload", this::handleUnload);
            config.routes.get("/unload_all", this::handleUnloadAll);

            /* AndroidManifest API */
            config.routes.get("/get_manifest", this::handleGetManifest);

            /* Code browser API */
            config.routes.get("/get_method_decompiled_code", this::handleGetMethodDecompiledCode);

            /* Class structure API */
            config.routes.get("/get_superclass", this::handleGetSuperClass);
            config.routes.get("/get_interfaces", this::handleGetInterfaces);
            config.routes.get("/get_class_methods", this::handleGetClassMethods);
            config.routes.get("/get_class_fields", this::handleGetClassFields);

            /* Callers and overrides API */
            config.routes.get("/get_method_callers", this::handleGetMethodCallers);
            config.routes.get("/get_class_callers", this::handleGetClassCallers);
            config.routes.get("/get_field_callers", this::handleGetFieldCallers);
            config.routes.get("/get_method_overrides", this::handleGetMethodOverrides);

            /* AIDL API */
            config.routes.get("/search_aidl_classes", this::handleSearchAidlClasses);
            config.routes.get("/get_aidl_methods", this::handleGetAidlMethods);
            config.routes.get("/get_aidl_impl_class", this::handleGetAidlImplClass);

            /* Management API */
            config.routes.get("/update_max_instance_count", this::handleUpdateMaxInstanceCount);

            config.jsonMapper(gsonMapper);
        }).start(host, port);

        logger.info("Jadx daemon MCP HTTP server started at http://{}:{}", host, port);
	}

    public void stop() {
        if (app != null) {
            app.stop();
        }
    }

	public void handleHealth(Context ctx) {
		Map<String, Object> response = new HashMap<>();
		response.put("result", "http://" + host + ":" + port);
		ctx.json(response);
	}

	public void handleLoad(Context ctx) {
		Map<String, Object> response = new HashMap<>();
		String filePath = ctx.queryParam("filePath");

        String instanceId = findJadxByPath(filePath);

		if (instanceId != null) {
			response.put("result", instanceId);
			ctx.json(response);
			return;
		}
		if (jadxInstanceMap.size() < maxInstanceCount) {
            instanceId = UUID.randomUUID().toString();

			JadxInstance instance = new JadxInstance(filePath);
			instance.load();
			jadxInstanceMap.put(instanceId, instance);

			response.put("result", instanceId);
			ctx.json(response);
		} else {
			response.put("error", "Max instance count reached, please use unload one instance " +
					"or use `update_max_instance_count` to update max instance count.");
			ctx.status(500).json(response);
		}
	}

    public void handleLoadDir(Context ctx) {
        Map<String, Object> response = new HashMap<>();
        String dirPath = ctx.queryParam("dirPath");

        String instanceId = findJadxByPath(dirPath);

        if (instanceId != null) {
            response.put("result", instanceId);
            ctx.json(response);
            return;
        }
        if (jadxInstanceMap.size() < maxInstanceCount) {
            instanceId = UUID.randomUUID().toString();

            JadxInstance instance = new JadxInstance(dirPath);
            instance.loadDir();
            jadxInstanceMap.put(instanceId, instance);

            response.put("result", instanceId);
            ctx.json(response);
        } else {
            response.put("error", "Max instance count reached, please use unload one instance " +
                    "or use `update_max_instance_count` to update max instance count.");
            ctx.status(500).json(response);
        }
    }

    public void handleLookupInstanceId(Context ctx) {
        Map<String, Object> response = new HashMap<>();
        String path = ctx.queryParam("path");

        String instanceId = findJadxByPath(path);

        if (instanceId != null) {
            response.put("result", instanceId);
            ctx.json(response);
        } else {
            response.put("error", "Cannot lookup instance by provided path: " + path);
            ctx.status(404).json(response);
        }
    }

	public void handleUnload(Context ctx) {
		Map<String, Object> response = new HashMap<>();
		String instanceId = ctx.queryParam("instanceId");

		JadxInstance instance = getJadx(instanceId);
		if (instance != null) {
			instance.close();
			jadxInstanceMap.remove(instanceId);

			response.put("result", instanceId);
			ctx.json(response);
		} else {
			response.put("error", "Cannot find instance by provided instance id: " + instanceId);
			ctx.status(404).json(response);
		}
	}

	public void handleUnloadAll(Context ctx) {
		Map<String, Object> response = new HashMap<>();
		int size = jadxInstanceMap.size();

		jadxInstanceMap.forEach((s, instance) -> instance.close());
		jadxInstanceMap.clear();

		response.put("result", size);
		ctx.json(response);
	}

	public void handleGetManifest(Context ctx) {
		Map<String, Object> response = new HashMap<>();
		String instanceId = ctx.queryParam("instanceId");

		JadxInstance instance = getJadx(instanceId);
		if (instance != null) {
			String manifest = instance.getManifest();
			if (manifest != null) {
				response.put("result", manifest);
				ctx.json(response);
			} else {
				response.put("error", "AndroidManifest.xml not found or failed to load.");
				ctx.status(404).json(response);
			}
		} else {
			response.put("error", "Cannot find instance by provided instance id: " + instanceId);
			ctx.status(404).json(response);
		}
	}

	public void handleGetMethodDecompiledCode(Context ctx) {
		Map<String, Object> response = new HashMap<>();
		String instanceId = ctx.queryParam("instanceId");
		String methodName = ctx.queryParam("methodName");

		JadxInstance instance = getJadx(instanceId);
		if (instance != null) {
            boolean isJVMSignature = SignatureConverter.isJVMSignature(methodName);

			String code = instance.getMethodDecompiledCode(
                    SignatureConverter.extractJavaClassFQN(methodName),
                    isJVMSignature ? SignatureConverter.toJavaMethodSignature(methodName) : methodName
            );
			if (code != null) {
				response.put("result", code);
				ctx.json(response);
			} else {
				response.put("error", "Cannot find method `" + methodName + "`." );
				ctx.status(404).json(response);
			}
		} else {
			response.put("error", "Cannot find instance by provided instance id: " + instanceId);
			ctx.status(404).json(response);
		}
	}

	public void handleGetSuperClass(Context ctx) {
		Map<String, Object> response = new HashMap<>();
		String instanceId = ctx.queryParam("instanceId");
		String className = ctx.queryParam("className");

		JadxInstance instance = getJadx(instanceId);
		if (instance != null) {
            boolean isJVMSignature = SignatureConverter.isJVMSignature(className);

			String superClass = instance.getSuperClass(
                    isJVMSignature ? SignatureConverter.toJavaClassSignature(className) : className
            );
			if (superClass != null) {
				response.put("result", superClass);
				ctx.json(response);
			} else {
				response.put("error", "Cannot find class `" + className + "`." );
				ctx.status(404).json(response);
			}
		} else {
			response.put("error", "Cannot find instance by provided instance id: " + instanceId);
			ctx.status(404).json(response);
		}
	}

	public void handleGetInterfaces(Context ctx) {
		Map<String, Object> response = new HashMap<>();
		String instanceId = ctx.queryParam("instanceId");
		String className = ctx.queryParam("className");

		JadxInstance instance = getJadx(instanceId);
		if (instance != null) {
            boolean isJVMSignature = SignatureConverter.isJVMSignature(className);

			List<String> interfaceNames = instance.getInterfaces(
                    isJVMSignature ? SignatureConverter.toJavaClassSignature(className) : className
            );
			if (interfaceNames != null) {
				response.put("result", interfaceNames);
				ctx.json(response);
			} else {
				response.put("error", "Cannot find class `" + className + "`." );
				ctx.status(404).json(response);
			}
		} else {
			response.put("error", "Cannot find instance by provided instance id: " + instanceId);
			ctx.status(404).json(response);
		}
	}

	public void handleGetClassMethods(Context ctx) {
		Map<String, Object> response = new HashMap<>();
		String instanceId = ctx.queryParam("instanceId");
		String className = ctx.queryParam("className");

		JadxInstance instance = getJadx(instanceId);
		if (instance != null) {
            boolean isJVMSignature = SignatureConverter.isJVMSignature(className);

			List<String> methodNames = instance.getClassMethods(
                    isJVMSignature ? SignatureConverter.toJavaClassSignature(className) : className
            );
			if (methodNames != null) {
				response.put("result", methodNames);
				ctx.json(response);
			} else {
				response.put("error", "Cannot find class `" + className + "`." );
				ctx.status(404).json(response);
			}
		} else {
			response.put("error", "Cannot find instance by provided instance id: " + instanceId);
			ctx.status(404).json(response);
		}
	}

	public void handleGetClassFields(Context ctx) {
		Map<String, Object> response = new HashMap<>();
		String instanceId = ctx.queryParam("instanceId");
		String className = ctx.queryParam("className");

		JadxInstance instance = getJadx(instanceId);
		if (instance != null) {
            boolean isJVMSignature = SignatureConverter.isJVMSignature(className);

			List<String> fieldNames = instance.getClassFields(
                    isJVMSignature ? SignatureConverter.toJavaClassSignature(className) : className
            );
			if (fieldNames != null) {
				response.put("result", fieldNames);
				ctx.json(response);
			} else {
				response.put("error", "Cannot find class `" + className + "`." );
				ctx.status(404).json(response);
			}
		} else {
			response.put("error", "Cannot find instance by provided instance id: " + instanceId);
			ctx.status(404).json(response);
		}
	}

    public void handleGetMethodCallers(Context ctx) {
        Map<String, Object> response = new HashMap<>();
        String instanceId = ctx.queryParam("instanceId");
        String methodName = ctx.queryParam("methodName");

        JadxInstance instance = getJadx(instanceId);
        if (instance != null) {
            boolean isJVMSignature = SignatureConverter.isJVMSignature(methodName);

            List<String> callers = instance.getMethodCallers(
                    SignatureConverter.extractJavaClassFQN(methodName),
                    isJVMSignature ? SignatureConverter.toJavaMethodSignature(methodName) :
                            methodName
            );
            if (callers != null) {
                response.put("result", callers);
                ctx.json(response);
            } else {
                response.put("error", "Cannot find caller for method `" + methodName + "`." );
                ctx.status(404).json(response);
            }
        } else {
            response.put("error", "Cannot find instance by provided instance id: " + instanceId);
            ctx.status(404).json(response);
        }
    }

    public void handleGetClassCallers(Context ctx) {
        Map<String, Object> response = new HashMap<>();
        String instanceId = ctx.queryParam("instanceId");
        String className = ctx.queryParam("className");

        JadxInstance instance = getJadx(instanceId);
        if (instance != null) {
            boolean isJVMSignature = SignatureConverter.isJVMSignature(className);

            List<String> callers = instance.getClassCallers(
                    isJVMSignature ? SignatureConverter.toJavaClassSignature(className) : className
            );
            if (callers != null) {
                response.put("result", callers);
                ctx.json(response);
            } else {
                response.put("error", "Cannot find caller for class `" + className + "`." );
                ctx.status(404).json(response);
            }
        } else {
            response.put("error", "Cannot find instance by provided instance id: " + instanceId);
            ctx.status(404).json(response);
        }
    }

    public void handleGetFieldCallers(Context ctx) {
        Map<String, Object> response = new HashMap<>();
        String instanceId = ctx.queryParam("instanceId");
        String fieldName = ctx.queryParam("fieldName");

        JadxInstance instance = getJadx(instanceId);
        if (instance != null) {
            boolean isJVMSignature = SignatureConverter.isJVMSignature(fieldName);

            List<String> callers = instance.getFieldCallers(
                    SignatureConverter.extractJavaClassFQN(fieldName),
                    isJVMSignature ? SignatureConverter.toJavaFieldSignature(fieldName) : fieldName
            );
            if (callers != null) {
                response.put("result", callers);
                ctx.json(response);
            } else {
                response.put("error", "Cannot find caller for field `" + fieldName + "`." );
                ctx.status(404).json(response);
            }
        } else {
            response.put("error", "Cannot find instance by provided instance id: " + instanceId);
            ctx.status(404).json(response);
        }
    }

    public void handleGetMethodOverrides(Context ctx) {
        Map<String, Object> response = new HashMap<>();
        String instanceId = ctx.queryParam("instanceId");
        String methodName = ctx.queryParam("methodName");

        JadxInstance instance = getJadx(instanceId);
        if (instance != null) {
            boolean isJVMSignature = SignatureConverter.isJVMSignature(methodName);

            List<String> overrides = instance.getMethodOverrides(
                    SignatureConverter.extractJavaClassFQN(methodName),
                    isJVMSignature ? SignatureConverter.toJavaMethodSignature(methodName) : methodName
            );
            if (overrides != null) {
                response.put("result", overrides);
                ctx.json(response);
            } else {
                response.put("error", "Cannot find overrides for method `" + methodName + "`." );
                ctx.status(404).json(response);
            }
        } else {
            response.put("error", "Cannot find instance by provided instance id: " + instanceId);
            ctx.status(404).json(response);
        }
    }

    public void handleSearchAidlClasses(Context ctx) {
        Map<String, Object> response = new HashMap<>();
        String instanceId = ctx.queryParam("instanceId");

        JadxInstance instance = getJadx(instanceId);
        if (instance != null) {
            List<String> aidlClasses = instance.searchAidlClasses();
            if (aidlClasses != null) {
                response.put("result", aidlClasses);
                ctx.json(response);
            } else {
                response.put("error", "Cannot find AIDL class." );
                ctx.status(404).json(response);
            }
        } else {
            response.put("error", "Cannot find instance by provided instance id: " + instanceId);
            ctx.status(404).json(response);
        }
    }

    public void handleGetAidlMethods(Context ctx) {
        Map<String, Object> response = new HashMap<>();
        String instanceId = ctx.queryParam("instanceId");
        String className = ctx.queryParam("className");

        JadxInstance instance = getJadx(instanceId);
        if (instance != null) {
            boolean isJVMSignature = SignatureConverter.isJVMSignature(className);

            List<String> aidlMethods = instance.getAidlMethods(
                    isJVMSignature ? SignatureConverter.toJavaClassSignature(className) : className
            );
            if (aidlMethods != null) {
                response.put("result", aidlMethods);
                ctx.json(response);
            } else {
                response.put("error", "Cannot find AIDL method in class `" + className + "`." );
                ctx.status(404).json(response);
            }
        } else {
            response.put("error", "Cannot find instance by provided instance id: " + instanceId);
            ctx.status(404).json(response);
        }
    }

    public void handleGetAidlImplClass(Context ctx) {
        Map<String, Object> response = new HashMap<>();
        String instanceId = ctx.queryParam("instanceId");
        String className = ctx.queryParam("className");

        JadxInstance instance = getJadx(instanceId);
        if (instance != null) {
            boolean isJVMSignature = SignatureConverter.isJVMSignature(className);

            String aidlImplClass = instance.getAidlImplClass(
                    isJVMSignature ? SignatureConverter.toJavaClassSignature(className) : className
            );
            if (aidlImplClass != null) {
                response.put("result", aidlImplClass);
                ctx.json(response);
            } else {
                response.put("error", "Cannot find AIDL impl of class `" + className +
                        "`, may be a native AIDL or not in current binaries." );
                ctx.status(404).json(response);
            }
        } else {
            response.put("error", "Cannot find instance by provided instance id: " + instanceId);
            ctx.status(404).json(response);
        }
    }

    public void handleUpdateMaxInstanceCount(Context ctx) {
		Map<String, Object> response = new HashMap<>();
		maxInstanceCount = ctx.queryParamAsClass("count", Integer.class)
				.check(it -> it == null || it > 0, "Count must be positive")
				.getOrDefault(1);
		ctx.json(response);
	}

	private JadxInstance getJadx(String instanceId) {
		return jadxInstanceMap.get(instanceId);
	}

    private String findJadxByPath(String path) {
        for (String instanceId : jadxInstanceMap.keySet()) {
            JadxInstance instance = jadxInstanceMap.get(instanceId);
            if (path.equals(instance.getFilePath()) && instance.isLoaded()) {
                return instanceId;
            }
        }
        return null;
    }
}
