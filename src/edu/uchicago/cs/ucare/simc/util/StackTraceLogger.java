package edu.uchicago.cs.ucare.simc.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StackTraceLogger {
    
    public static void debugStackTrace(Logger log) {
        StackTraceElement[] stackTraces = Thread.currentThread().getStackTrace();
        for (int i = 1; i < stackTraces.length; ++i) {
            log.debug(stackTraces[i].toString());
        }
    }
    
    public static void debugStackTrace(Class type) {
        Logger log = LoggerFactory.getLogger(type);
        StackTraceElement[] stackTraces = Thread.currentThread().getStackTrace();
        for (int i = 1; i < stackTraces.length; ++i) {
            log.debug(stackTraces[i].toString());
        }
    }


    public static void infoStackTrace(Logger log) {
        StackTraceElement[] stackTraces = Thread.currentThread().getStackTrace();
        for (int i = 1; i < stackTraces.length; ++i) {
            log.info(stackTraces[i].toString());
        }
    }
    
    public static void infoStackTrace(Class type) {
        Logger log = LoggerFactory.getLogger(type);
        StackTraceElement[] stackTraces = Thread.currentThread().getStackTrace();
        for (int i = 1; i < stackTraces.length; ++i) {
            log.info(stackTraces[i].toString());
        }
    }

}
