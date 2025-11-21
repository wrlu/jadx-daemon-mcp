#!/usr/bin/env python3
import os
import requests
import json
from fastmcp import FastMCP
from typing import Annotated


mcp = FastMCP("jadx-daemon-mcp")


def get_jadx_url() -> str:
    host = os.getenv("JADX_DAEMON_MCP_HOST", "localhost")
    port = os.getenv("JADX_DAEMON_MCP_PORT", "8651")
    return f"http://{host}:{port}"


@mcp.tool(
    name="health",
    description="Health check."
)
def health() -> dict:
    url = get_jadx_url()
    response = requests.get(url + "/health")
    return json.loads(response.text)


@mcp.tool(
    name="load",
    description="Load a single apk or dex file to jadx decomplier."
)
def load(
    instanceId: Annotated[str, "An unique string type id to identify this jadx instance."], 
    filePath: Annotated[str, "Full path of the single apk or dex file."]
) -> dict:
    url = get_jadx_url()
    query = {
        "instanceId": instanceId,
        "filePath": filePath
    }
    response = requests.get(url + "/load", params=query)
    return json.loads(response.text)


@mcp.tool(
    name="load_dir",
    description="Load a dir which contains many apks and dexs to jadx decomplier."
)
def load_dir(
    instanceId: Annotated[str, "An unique string type id to identify this jadx instance."], 
    dirPath: Annotated[str, "Full path of the single apk or dex file."]
) -> dict:
    url = get_jadx_url()
    query = {
        "instanceId": instanceId,
        "dirPath": dirPath
    }
    response = requests.get(url + "/load_dir", params=query)
    return json.loads(response.text)


@mcp.tool(
    name="unload",
    description="Unload jadx decomplier by instance id."
)
def unload(
    instanceId: Annotated[str, "An unique string type id to identify this jadx instance."], 
) -> dict:
    url = get_jadx_url()
    query = {
        "instanceId": instanceId,
    }
    response = requests.get(url + "/unload", params=query)
    return json.loads(response.text)


@mcp.tool(
    name="unload_all",
    description="Unload all instances from jadx decomplier."
)
def unload_all() -> dict:
    url = get_jadx_url()
    response = requests.get(url + "/unload_all")
    return json.loads(response.text)


@mcp.tool(
    name="get_manifest",
    description="Get the AndroidManifest.xml file content."
)
def get_manifest(
    instanceId: Annotated[str, "An unique string type id to identify this jadx instance."], 
) -> dict:
    url = get_jadx_url()
    query = {
        "instanceId": instanceId,
    }
    response = requests.get(url + "/get_manifest", params=query)
    return json.loads(response.text)


@mcp.tool(
    name="get_all_exported_activities",
    description="Get all exported activity names from the APK manifest."
)
def get_all_exported_activities(
    instanceId: Annotated[str, "An unique string type id to identify this jadx instance."], 
) -> dict:
    url = get_jadx_url()
    query = {
        "instanceId": instanceId,
    }
    response = requests.get(url + "/get_all_exported_activities", params=query)
    return json.loads(response.text)


@mcp.tool(
    name="get_all_exported_services",
    description="Get all exported service names from the APK manifest."
)
def get_all_exported_services(
    instanceId: Annotated[str, "An unique string type id to identify this jadx instance."], 
) -> dict:
    url = get_jadx_url()
    query = {
        "instanceId": instanceId,
    }
    response = requests.get(url + "/get_all_exported_services", params=query)
    return json.loads(response.text)


@mcp.tool(
    name="get_method_decompiled_code",
    description="Get the decompiled code of the given java method."
)
def get_method_decompiled_code(
    instanceId: Annotated[str, "An unique string type id to identify this jadx instance."],
    methodName: Annotated[str, "The method signature must be the full JVM method signature, e.g. `Lcom/example/abc;->testMethod(Ljava/lang/String;I)V`."],
) -> dict:
    url = get_jadx_url()
    query = {
        "instanceId": instanceId,
        "className": className,
        "methodName": methodName,
    }
    response = requests.get(url + "/get_method_decompiled_code", params=query)
    return json.loads(response.text)


@mcp.tool(
    name="get_class_decompiled_code",
    description="Get the decompiled code of the given java class."
)
def get_class_decompiled_code(
    instanceId: Annotated[str, "An unique string type id to identify this jadx instance."],
    className: Annotated[str, "The class name needs to be a JVM class descriptor, e.g. `Lcom/example/abc/SomeClass;`."],
) -> dict:
    url = get_jadx_url()
    query = {
        "instanceId": instanceId,
        "className": className,
    }
    response = requests.get(url + "/get_class_decompiled_code", params=query)
    return json.loads(response.text)


@mcp.tool(
    name="get_class_smali_code",
    description="Get the smali code of the given java class."
)
def get_class_smali_code(
    instanceId: Annotated[str, "An unique string type id to identify this jadx instance."],
    className: Annotated[str, "The class name needs to be a JVM class descriptor, e.g. `Lcom/example/abc/SomeClass;`."],
) -> dict:
    url = get_jadx_url()
    query = {
        "instanceId": instanceId,
        "className": className,
    }
    response = requests.get(url + "/get_class_smali_code", params=query)
    return json.loads(response.text)


@mcp.tool(
    name="get_superclass",
    description="Get the superclass of the given java class."
)
def get_superclass(
    instanceId: Annotated[str, "An unique string type id to identify this jadx instance."],
    className: Annotated[str, "The class name needs to be a JVM class descriptor, e.g. `Lcom/example/abc/SomeClass;`."],
) -> dict:
    url = get_jadx_url()
    query = {
        "instanceId": instanceId,
        "className": className,
    }
    response = requests.get(url + "/get_superclass", params=query)
    return json.loads(response.text)


@mcp.tool(
    name="get_interfaces",
    description="Get the interfaces of the given java class."
)
def get_interfaces(
    instanceId: Annotated[str, "An unique string type id to identify this jadx instance."],
    className: Annotated[str, "The class name needs to be a JVM class descriptor, e.g. `Lcom/example/abc/SomeClass;`."],
) -> dict:
    url = get_jadx_url()
    query = {
        "instanceId": instanceId,
        "className": className,
    }
    response = requests.get(url + "/get_interfaces", params=query)
    return json.loads(response.text)


@mcp.tool(
    name="get_class_methods",
    description="Get the method list of the given java class."
)
def get_class_methods(
    instanceId: Annotated[str, "An unique string type id to identify this jadx instance."],
    className: Annotated[str, "The class name needs to be a JVM class descriptor, e.g. `Lcom/example/abc/SomeClass;`."],
) -> dict:
    url = get_jadx_url()
    query = {
        "instanceId": instanceId,
        "className": className,
    }
    response = requests.get(url + "/get_class_methods", params=query)
    return json.loads(response.text)


@mcp.tool(
    name="get_class_fields",
    description="Get the field list of the given java class."
)
def get_class_fields(
    instanceId: Annotated[str, "An unique string type id to identify this jadx instance."],
    className: Annotated[str, "The class name needs to be a JVM class descriptor, e.g. `Lcom/example/abc/SomeClass;`."],
) -> dict:
    url = get_jadx_url()
    query = {
        "instanceId": instanceId,
        "className": className,
    }
    response = requests.get(url + "/get_class_fields", params=query)
    return json.loads(response.text)


@mcp.tool(
    name="get_method_callers",
    description="Get the caller list of the given java method."
)
def get_method_callers(
    instanceId: Annotated[str, "An unique string type id to identify this jadx instance."],
    methodName: Annotated[str, "The method signature must be the full JVM method signature, e.g. `Lcom/example/abc;->testMethod(Ljava/lang/String;I)V`."],
) -> dict:
    url = get_jadx_url()
    query = {
        "instanceId": instanceId,
        "className": className,
        "methodName": methodName,
    }
    response = requests.get(url + "/get_method_callers", params=query)
    return json.loads(response.text)


@mcp.tool(
    name="get_class_callers",
    description="Get the caller list of the given java class."
)
def get_class_callers(
    instanceId: Annotated[str, "An unique string type id to identify this jadx instance."],
    className: Annotated[str, "The class name needs to be a JVM class descriptor, e.g. `Lcom/example/abc/SomeClass;`."],
) -> dict:
    url = get_jadx_url()
    query = {
        "instanceId": instanceId,
        "className": className,
    }
    response = requests.get(url + "/get_class_callers", params=query)
    return json.loads(response.text)


@mcp.tool(
    name="get_method_overrides",
    description="Get the override list of the given java method."
)
def get_method_overrides(
    instanceId: Annotated[str, "An unique string type id to identify this jadx instance."],
    methodName: Annotated[str, "The method signature must be the full JVM method signature, e.g. `Lcom/example/abc;->testMethod(Ljava/lang/String;I)V`."],
) -> dict:
    url = get_jadx_url()
    query = {
        "instanceId": instanceId,
        "className": className,
        "methodName": methodName,
    }
    response = requests.get(url + "/get_method_overrides", params=query)
    return json.loads(response.text)


@mcp.tool(
    name="update_max_instance_count",
    description="Update the max parallel jadx decomplier instance count, if you set a large value, this will use lots of memory and may get a OOM error."
)
def update_max_instance_count(
    count: Annotated[int, "The new max instance count must be at least 1."],
) -> dict:
    url = get_jadx_url()
    query = {
        "count": count,
    }
    response = requests.get(url + "/update_max_instance_count", params=query)
    return json.loads(response.text)


if __name__ == "__main__":
    mcp.run()
