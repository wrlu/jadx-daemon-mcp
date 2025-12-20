#!/usr/bin/env python3
import os
import requests
import json
import re
from fastmcp import FastMCP
from typing import Annotated
from xml.etree import ElementTree as ET


mcp = FastMCP("jadx-daemon-mcp")

INSTANCE_ID_ANNOTATED_STR = "An unique string type id to identify this jadx instance."
FILEPATH_ANNOTATED_STR = "Full path of the file or a the directory."
CLASS_ANNOTATED_STR = "The class name needs to be a Java FQN, e.g. `com.example.abc.AClass`."
METHOD_ANNOTATED_STR = "The method name must be a Java signature with the parent class's Java FQN. " \
"e.g. `com.example.abc.AClass.testMethod(java.lang.String, java.lang.String[], int):java.util.List<java.lang.String>`."
FIELD_ANNOTATED_STR = "The field name must be a Java signature with the parent class's Java FQN, be careful the blank." \
"e.g. `com.example.abc.AClass.testField :java.util.List<java.lang.String>`."
MAX_INSTANCE_COUNT_ANNOTATED_STR = "The new max instance count must be at least 1."


def get_jadx_url() -> str:
    host = os.getenv("JADX_DAEMON_MCP_HOST", "localhost")
    port = os.getenv("JADX_DAEMON_MCP_PORT", "8651")
    return f"http://{host}:{port}"


def preprocess_manifest(manifest_text):
    """
    一个健壮的 Manifest 预处理函数。
    它会清理非法字符，并强行移除所有 <meta-data> 标签以避免解析错误。
    """
    # 1. 确保输入是字符串类型
    if isinstance(manifest_text, bytes):
        try:
            manifest_text = manifest_text.decode('utf-8')
        except UnicodeDecodeError:
            manifest_text = manifest_text.decode('utf-8', 'ignore')
    elif not isinstance(manifest_text, str):
        # 如果既不是 bytes 也不是 str，转换为字符串
        manifest_text = str(manifest_text)

    # 2. 清理基本的非法 XML 字符
    cleaned_chars = []
    for char in manifest_text:
        codepoint = ord(char)
        if (codepoint == 0x9 or codepoint == 0xA or codepoint == 0xD or
                (codepoint >= 0x20 and codepoint <= 0xD7FF) or
                (codepoint >= 0xE000 and codepoint <= 0xFFFD) or
                (codepoint >= 0x10000 and codepoint <= 0x10FFFF)):
            cleaned_chars.append(char)
    text_no_illegal_chars = "".join(cleaned_chars)  # 移除 u 前缀

    # 3. 使用正则表达式，强行移除所有 <meta-data ... /> 标签
    text_no_metadata = re.sub(
        r'<\s*meta-data[^>]*?/>',  # 修正正则表达式，避免贪婪匹配问题
        '',  # 替换为空字符串，即直接删除
        text_no_illegal_chars,
        flags=re.DOTALL | re.IGNORECASE
    )

    return text_no_metadata

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
    filePath: Annotated[str, FILEPATH_ANNOTATED_STR]
) -> dict:
    url = get_jadx_url()
    query = {
        "filePath": filePath
    }
    response = requests.get(url + "/load", params=query)
    return json.loads(response.text)


@mcp.tool(
    name="load_dir",
    description="Load a dir which contains many apks and dexs to jadx decomplier."
)
def load_dir(
    dirPath: Annotated[str, FILEPATH_ANNOTATED_STR]
) -> dict:
    url = get_jadx_url()
    query = {
        "dirPath": dirPath
    }
    response = requests.get(url + "/load_dir", params=query)
    return json.loads(response.text)

@mcp.tool(
    name="lookup_instance_id",
    description="Lookup jadx decomplier instance id by a file path. If you forget your instance id please use this method."
)
def lookup_instance_id(
    path: Annotated[str, FILEPATH_ANNOTATED_STR]
) -> dict:
    url = get_jadx_url()
    query = {
        "path": path
    }
    response = requests.get(url + "/lookup_instance_id", params=query)
    return json.loads(response.text)

@mcp.tool(
    name="unload",
    description="Unload jadx decomplier by instance id."
)
def unload(
    instanceId: Annotated[str, INSTANCE_ID_ANNOTATED_STR], 
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
    instanceId: Annotated[str, INSTANCE_ID_ANNOTATED_STR], 
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
    instanceId: Annotated[str, INSTANCE_ID_ANNOTATED_STR], 
) -> dict:
    url = get_jadx_url()
    query = {
        "instanceId": instanceId,
    }
    response = requests.get(url + "/get_manifest", params=query)
    json_response = json.loads(response.text)

    if "error" in json_response:
        return json_response
    
    manifest_text = preprocess_manifest(json_response["result"])

    try:
        root = ET.fromstring(manifest_text.encode('utf-8'))
    except Exception as e:
        return {
            "error": e
        }

    ANDROID_NS = 'http://schemas.android.com/apk/res/android'
    exported_activities = []

    # 获取包名
    package_name = root.attrib.get('package', '').strip()

    # 查找 <application> 节点
    app_node = root.find('application')
    if app_node is None:
        return {
            "error": "Cannot find application node."
        }

    for activity in app_node.findall('activity'):
        name = activity.attrib.get('{' + ANDROID_NS + '}name')
        exported = activity.attrib.get('{' + ANDROID_NS + '}exported')
        has_intent_filter = len(activity.findall('intent-filter')) > 0

        if not name:
            continue

        if exported == "true" or (exported is None and has_intent_filter):
            normalized = set()

            if name.startswith('.'):
                normalized.add(package_name + name)
            elif '.' not in name:
                normalized.add(name)
                normalized.add(package_name + '.' + name)
            else:
                normalized.add(name)

            exported_activities.extend(normalized)

    return {
        "result": exported_activities
    }


@mcp.tool(
    name="get_all_exported_services",
    description="Get all exported service names from the APK manifest."
)
def get_all_exported_services(
    instanceId: Annotated[str, INSTANCE_ID_ANNOTATED_STR], 
) -> dict:
    url = get_jadx_url()
    query = {
        "instanceId": instanceId,
    }
    response = requests.get(url + "/get_manifest", params=query)
    json_response = json.loads(response.text)

    if "error" in json_response:
        return json_response
    
    manifest_text = preprocess_manifest(json_response["result"])

    try:
        root = ET.fromstring(manifest_text.encode('utf-8'))
    except Exception as e:
        return {
            "error": e
        }

    ANDROID_NS = 'http://schemas.android.com/apk/res/android'
    exported_services = []

    # 获取包名
    package_name = root.attrib.get('package', '').strip()

    # 查找 <application> 节点
    app_node = root.find('application')
    if app_node is None:
        return {
            "error": "Cannot find application node."
        }

    for activity in app_node.findall('service'):
        name = activity.attrib.get('{' + ANDROID_NS + '}name')
        exported = activity.attrib.get('{' + ANDROID_NS + '}exported')
        has_intent_filter = len(activity.findall('intent-filter')) > 0

        if not name:
            continue

        if exported == "true" or (exported is None and has_intent_filter):
            normalized = set()

            if name.startswith('.'):
                normalized.add(package_name + name)
            elif '.' not in name:
                normalized.add(name)
                normalized.add(package_name + '.' + name)
            else:
                normalized.add(name)

            exported_services.extend(normalized)

    return {
        "result": exported_services
    }


@mcp.tool(
    name="get_method_decompiled_code",
    description="Get the decompiled code of the given java method."
)
def get_method_decompiled_code(
    instanceId: Annotated[str, INSTANCE_ID_ANNOTATED_STR],
    methodName: Annotated[str, METHOD_ANNOTATED_STR],
) -> dict:
    url = get_jadx_url()
    query = {
        "instanceId": instanceId,
        "methodName": methodName,
    }
    response = requests.get(url + "/get_method_decompiled_code", params=query)
    return json.loads(response.text)

@mcp.tool(
    name="get_superclass",
    description="Get the superclass of the given java class."
)
def get_superclass(
    instanceId: Annotated[str, INSTANCE_ID_ANNOTATED_STR],
    className: Annotated[str, CLASS_ANNOTATED_STR],
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
    instanceId: Annotated[str, INSTANCE_ID_ANNOTATED_STR],
    className: Annotated[str, CLASS_ANNOTATED_STR],
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
    instanceId: Annotated[str, INSTANCE_ID_ANNOTATED_STR],
    className: Annotated[str, CLASS_ANNOTATED_STR],
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
    instanceId: Annotated[str, INSTANCE_ID_ANNOTATED_STR],
    className: Annotated[str, CLASS_ANNOTATED_STR],
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
    instanceId: Annotated[str, INSTANCE_ID_ANNOTATED_STR],
    methodName: Annotated[str, METHOD_ANNOTATED_STR],
) -> dict:
    url = get_jadx_url()
    query = {
        "instanceId": instanceId,
        "methodName": methodName,
    }
    response = requests.get(url + "/get_method_callers", params=query)
    return json.loads(response.text)


@mcp.tool(
    name="get_class_callers",
    description="Get the caller list of the given java class."
)
def get_class_callers(
    instanceId: Annotated[str, INSTANCE_ID_ANNOTATED_STR],
    className: Annotated[str, CLASS_ANNOTATED_STR],
) -> dict:
    url = get_jadx_url()
    query = {
        "instanceId": instanceId,
        "className": className,
    }
    response = requests.get(url + "/get_class_callers", params=query)
    return json.loads(response.text)


@mcp.tool(
    name="get_field_callers",
    description="Get the caller list of the given java class."
)
def get_field_callers(
    instanceId: Annotated[str, INSTANCE_ID_ANNOTATED_STR],
    fieldName: Annotated[str, FIELD_ANNOTATED_STR],
) -> dict:
    url = get_jadx_url()
    query = {
        "instanceId": instanceId,
        "fieldName": fieldName,
    }
    response = requests.get(url + "/get_field_callers", params=query)
    return json.loads(response.text)


@mcp.tool(
    name="get_method_overrides",
    description="Get the override list of the given java method."
)
def get_method_overrides(
    instanceId: Annotated[str, INSTANCE_ID_ANNOTATED_STR],
    methodName: Annotated[str, METHOD_ANNOTATED_STR],
) -> dict:
    url = get_jadx_url()
    query = {
        "instanceId": instanceId,
        "methodName": methodName,
    }
    response = requests.get(url + "/get_method_overrides", params=query)
    return json.loads(response.text)


@mcp.tool(
    name="search_aidl_classes",
    description="Search for all AIDL classes."
)
def search_aidl_classes(
    instanceId: Annotated[str, INSTANCE_ID_ANNOTATED_STR],
) -> dict:
    url = get_jadx_url()
    query = {
        "instanceId": instanceId,
    }
    response = requests.get(url + "/search_aidl_classes", params=query)
    return json.loads(response.text)


@mcp.tool(
    name="get_aidl_methods",
    description="Get the AIDL methods of the given aidl class."
)
def get_aidl_methods(
    instanceId: Annotated[str, INSTANCE_ID_ANNOTATED_STR],
    className: Annotated[str, CLASS_ANNOTATED_STR],
) -> dict:
    url = get_jadx_url()
    query = {
        "instanceId": instanceId,
        "className": className,
    }
    response = requests.get(url + "/get_aidl_methods", params=query)
    return json.loads(response.text)


@mcp.tool(
    name="get_aidl_impl_class",
    description="Get the implementation of the given aidl class."
)
def get_aidl_impl_class(
    instanceId: Annotated[str, INSTANCE_ID_ANNOTATED_STR],
    className: Annotated[str, CLASS_ANNOTATED_STR],
) -> dict:
    url = get_jadx_url()
    query = {
        "instanceId": instanceId,
        "className": className,
    }
    response = requests.get(url + "/get_aidl_impl_class", params=query)
    return json.loads(response.text)


@mcp.tool(
    name="update_max_instance_count",
    description="Update the max parallel jadx decomplier instance count, if you set a large value, this will use lots of memory and may get a OOM error."
)
def update_max_instance_count(
    count: Annotated[int, MAX_INSTANCE_COUNT_ANNOTATED_STR],
) -> dict:
    url = get_jadx_url()
    query = {
        "count": count,
    }
    response = requests.get(url + "/update_max_instance_count", params=query)
    return json.loads(response.text)


if __name__ == "__main__":
    mcp.run()
