package com.wrlus.jadx;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

public class TestMain {

    public static void testAIDL() throws IOException {
        String path = "/home/xiaolu/Firmware/Android/Samsung/SM-S731N_16_S731NKSS5AZC1";
        String output = "service_aidl.txt";

        JadxInstance instance = new JadxInstance(path + "/packages/android");
        instance.loadDir();

        List<String> aidlClasses = instance.searchAidlClasses();
        System.out.println("AIDL classes count: " + aidlClasses.size());

        File outputFile = new File(path, output);
        FileWriter fw = new FileWriter(outputFile);

        for (String aidlClass : aidlClasses) {
            String aidlImplClass = instance.getAidlImplClass(aidlClass);
            fw.write(aidlClass + " [" + aidlImplClass + "]\n");
            List<String> aidlMethods = instance.getAidlMethods(aidlClass);

            System.out.println("AIDL classes " + aidlClass + " methods count: " + aidlMethods.size());
            for (String aidlMethod : aidlMethods) {
                fw.write(aidlMethod + "\n");
            }
            fw.write("\n");
        }
        fw.close();
    }

	public static void main(String[] args) {
        try {
            testAIDL();
        } catch (IOException e) {
            e.printStackTrace();
        }
	}
}
