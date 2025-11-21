package com.wrlus.jadx;

import java.util.List;

public class TestMain {
	public static void main(String[] args) {
		JadxInstance instance = new JadxInstance();
		instance.load("/home/xiaolu/Reverse/Android/China/应用宝/应用宝.apk");

        String jvmSignature = "Lcom/tencent/assistant/activity/BaseActivity;->activityExposureReport()V";

		List<String> methods = instance.getMethodCallers(
                SignatureConverter.extractJavaClassFQN(jvmSignature),
                SignatureConverter.toJavaMethodSignature(jvmSignature)
        );
		System.out.println(methods);
	}
}
