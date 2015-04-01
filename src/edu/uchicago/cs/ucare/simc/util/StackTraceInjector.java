package edu.uchicago.cs.ucare.simc.util;

import java.util.Arrays;

public class StackTraceInjector {
    
    public static int traceCode() {
        Thread currentThread = Thread.currentThread();
        StackTraceElement[] st = currentThread.getStackTrace();
        return Arrays.hashCode(st);
//        return (Arrays.hashCode(st), currentThread.getName());
    }
    
    public static String[] traceCodeWithDetail() {
        Thread currentThread = Thread.currentThread();
        StackTraceElement[] st = currentThread.getStackTrace();
        int hash = Arrays.hashCode(st);
//        int hash = addHash(Arrays.hashCode(st), currentThread.getName());
        String[] detail = new String[st.length + 2];
        detail[0] = hash + "";
        detail[1] = currentThread.getName();
        for (int i = 0; i < st.length; ++i) {
            detail[i + 2] = st[i].toString();
        }
        return detail;
    }
    
    private static int addHash(int hash, Object obj) {
        return hash * 31 + obj.hashCode();
    }

}