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
		app = Javalin.create(config -> config.jsonMapper(gsonMapper)).start(host, port);

		/* Health checker API */
		app.get("/health", this::handleHealth);

		/* Android binary loader API */
		app.get("/load", this::handleLoad);
		app.get("/load_dir", this::handleLoadDir);
		app.get("/unload", this::handleUnload);
		app.get("/unload_all", this::handleUnloadAll);

		/* AndroidManifest API */
		app.get("/get_manifest", this::handleGetManifest);
		app.get("/get_all_exported_activities", this::handleGetAllExportedActivities);
		app.get("/get_all_exported_services", this::handleGetAllExportedServices);

		/* Code browser API */
		app.get("/get_method_decompiled_code", this::handleGetMethodDecompiledCode);
		app.get("/get_class_decompiled_code", this::handleGetClassDecompiledCode);
		app.get("/get_class_smali_code", this::handleGetClassSmaliCode);

		/* Class structure API */
		app.get("/get_superclass", this::handleGetSuperClass);
		app.get("/get_interfaces", this::handleGetInterfaces);
		app.get("/get_class_methods", this::handleGetClassMethods);
		app.get("/get_class_fields", this::handleGetClassFields);

        /* Callers and overrides API */
        app.get("/get_method_callers", this::handleGetMethodCallers);
        app.get("/get_class_callers", this::handleGetClassCallers);
        app.get("/get_method_overrides", this::handleGetMethodOverrides);

		/* Management API */
		app.get("/update_max_instance_count", this::handleUpdateMaxInstanceCount);

        logger.info("Jadx daemon MCP HTTP server started at http://{}:{}", host, port);
	}

	public void handleHealth(Context ctx) {
		Map<String, Object> response = new HashMap<>();
		response.put("result", "http://" + host + ":" + port);
		ctx.json(response);
	}

	public void handleLoad(Context ctx) {
		Map<String, Object> response = new HashMap<>();
		String instanceId = ctx.queryParam("instanceId");
		String filePath = ctx.queryParam("filePath");

		if (getJadx(instanceId) != null) {
			response.put("result", instanceId);
			ctx.json(response);
			return;
		}
		if (jadxInstanceMap.size() < maxInstanceCount) {
			JadxInstance instance = new JadxInstance();
			instance.load(filePath);
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
		String instanceId = ctx.queryParam("instanceId");
		String dirPath = ctx.queryParam("dirPath");

		if (getJadx(instanceId) != null) {
			response.put("result", instanceId);
			ctx.json(response);
			return;
		}
		if (jadxInstanceMap.size() < maxInstanceCount) {
			JadxInstance instance = new JadxInstance();
			instance.loadDir(dirPath);
			jadxInstanceMap.put(instanceId, instance);

			response.put("result", instanceId);
			ctx.json(response);
		} else {
			response.put("error", "Max instance count reached, please use unload one instance " +
					"or use `update_max_instance_count` to update max instance count.");
			ctx.status(500).json(response);
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
			ctx.status(500).json(response);
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
			ctx.status(500).json(response);
		}
	}

	public void handleGetAllExportedActivities(Context ctx) {
		Map<String, Object> response = new HashMap<>();
		String instanceId = ctx.queryParam("instanceId");

		JadxInstance instance = getJadx(instanceId);
		if (instance != null) {
			ctx.json(response);
		} else {
			response.put("error", "Cannot find instance by provided instance id: " + instanceId);
			ctx.status(500).json(response);
		}
	}

	public void handleGetAllExportedServices(Context ctx) {
		Map<String, Object> response = new HashMap<>();
		String instanceId = ctx.queryParam("instanceId");

		JadxInstance instance = getJadx(instanceId);
		if (instance != null) {
			ctx.json(response);
		} else {
			response.put("error", "Cannot find instance by provided instance id: " + instanceId);
			ctx.status(500).json(response);
		}
	}

	public void handleGetMethodDecompiledCode(Context ctx) {
		Map<String, Object> response = new HashMap<>();
		String instanceId = ctx.queryParam("instanceId");
		String methodName = ctx.queryParam("methodName");

		JadxInstance instance = getJadx(instanceId);
		if (instance != null) {
			String code = instance.getMethodDecompiledCode(
                    SignatureConverter.extractJavaClassFQN(methodName),
                    SignatureConverter.toJavaMethodSignature(methodName)
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
			ctx.status(500).json(response);
		}
	}

	public void handleGetClassDecompiledCode(Context ctx) {
		Map<String, Object> response = new HashMap<>();
		String instanceId = ctx.queryParam("instanceId");
		String className = ctx.queryParam("className");

		JadxInstance instance = getJadx(instanceId);
		if (instance != null) {
			String code = instance.getClassDecompiledCode(
                    SignatureConverter.toJavaClassSignature(className)
            );
			if (code != null) {
				response.put("result", code);
				ctx.json(response);
			} else {
				response.put("error", "Cannot find class `" + className + "`." );
				ctx.status(404).json(response);
			}
		} else {
			response.put("error", "Cannot find instance by provided instance id: " + instanceId);
			ctx.status(500).json(response);
		}
	}

	public void handleGetClassSmaliCode(Context ctx) {
		Map<String, Object> response = new HashMap<>();
		String instanceId = ctx.queryParam("instanceId");
		String className = ctx.queryParam("className");

		JadxInstance instance = getJadx(instanceId);
		if (instance != null) {
			String code = instance.getClassSmaliCode(
                    SignatureConverter.toJavaClassSignature(className)
            );
			if (code != null) {
				response.put("result", code);
				ctx.json(response);
			} else {
				response.put("error", "Cannot find class `" + className + "`." );
				ctx.status(404).json(response);
			}
		} else {
			response.put("error", "Cannot find instance by provided instance id: " + instanceId);
			ctx.status(500).json(response);
		}
	}

	public void handleGetSuperClass(Context ctx) {
		Map<String, Object> response = new HashMap<>();
		String instanceId = ctx.queryParam("instanceId");
		String className = ctx.queryParam("className");

		JadxInstance instance = getJadx(instanceId);
		if (instance != null) {
			String superClass = instance.getSuperClass(
                    SignatureConverter.toJavaClassSignature(className)
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
			ctx.status(500).json(response);
		}
	}

	public void handleGetInterfaces(Context ctx) {
		Map<String, Object> response = new HashMap<>();
		String instanceId = ctx.queryParam("instanceId");
		String className = ctx.queryParam("className");

		JadxInstance instance = getJadx(instanceId);
		if (instance != null) {
			List<String> interfaceNames = instance.getInterfaces(
                    SignatureConverter.toJavaClassSignature(className)
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
			ctx.status(500).json(response);
		}
	}

	public void handleGetClassMethods(Context ctx) {
		Map<String, Object> response = new HashMap<>();
		String instanceId = ctx.queryParam("instanceId");
		String className = ctx.queryParam("className");

		JadxInstance instance = getJadx(instanceId);
		if (instance != null) {
			List<String> methodNames = instance.getClassMethods(
                    SignatureConverter.toJavaClassSignature(className)
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
			ctx.status(500).json(response);
		}
	}

	public void handleGetClassFields(Context ctx) {
		Map<String, Object> response = new HashMap<>();
		String instanceId = ctx.queryParam("instanceId");
		String className = ctx.queryParam("className");

		JadxInstance instance = getJadx(instanceId);
		if (instance != null) {
			List<String> fieldNames = instance.getClassFields(
                    SignatureConverter.toJavaClassSignature(className)
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
			ctx.status(500).json(response);
		}
	}

    public void handleGetMethodCallers(Context ctx) {
        Map<String, Object> response = new HashMap<>();
        String instanceId = ctx.queryParam("instanceId");
        String methodName = ctx.queryParam("methodName");

        JadxInstance instance = getJadx(instanceId);
        if (instance != null) {
            List<String> callers = instance.getMethodCallers(
                    SignatureConverter.extractJavaClassFQN(methodName),
                    SignatureConverter.toJavaMethodSignature(methodName)
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
            ctx.status(500).json(response);
        }
    }

    public void handleGetClassCallers(Context ctx) {
        Map<String, Object> response = new HashMap<>();
        String instanceId = ctx.queryParam("instanceId");
        String className = ctx.queryParam("className");

        JadxInstance instance = getJadx(instanceId);
        if (instance != null) {
            List<String> callers = instance.getClassCallers(
                    SignatureConverter.toJavaClassSignature(className)
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
            ctx.status(500).json(response);
        }
    }

    public void handleGetMethodOverrides(Context ctx) {
        Map<String, Object> response = new HashMap<>();
        String instanceId = ctx.queryParam("instanceId");
        String methodName = ctx.queryParam("methodName");

        JadxInstance instance = getJadx(instanceId);
        if (instance != null) {
            List<String> overrides = instance.getMethodOverrides(
                    SignatureConverter.extractJavaClassFQN(methodName),
                    SignatureConverter.toJavaMethodSignature(methodName)
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
            ctx.status(500).json(response);
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
}
