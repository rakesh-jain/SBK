/**
 * Copyright (c) KMG. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package io.sbk.api.impl;

import io.sbk.api.PerformanceLogger;
import io.sbk.api.Storage;
import io.sbk.perl.Time;
import io.sbk.perl.TimeUnit;
import io.sbk.perl.impl.MicroSeconds;
import io.sbk.perl.impl.MilliSeconds;
import io.sbk.perl.impl.NanoSeconds;
import io.sbk.system.Printer;
import org.reflections.Reflections;
import org.reflections.ReflectionsException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public  class SbkUtils {
    public final static String CLASS_OPTION = "-class";

    public static Time getTime(PerformanceLogger logger ) {
        final TimeUnit timeUnit = logger.getTimeUnit();
        final Time ret;
        if (timeUnit == TimeUnit.mcs) {
            ret = new MicroSeconds();
        } else if (timeUnit == TimeUnit.ns) {
            ret = new NanoSeconds();
        } else {
            ret = new MilliSeconds();
        }
        Printer.log.info("Time Unit: "+ ret.getTimeUnit().toString());
        Printer.log.info("Minimum Latency: "+logger.getMinLatency()+" "+ret.getTimeUnit().name());
        Printer.log.info("Maximum Latency: "+logger.getMaxLatency()+" "+ret.getTimeUnit().name());
        return ret;
    }

    public static String[] removeOptionsAndValues(String[] args, String[] opts) {
        if (args == null || args.length < 3) {
            return null;
        }
        List<String> optsList = Arrays.asList(opts);
        List<String> ret = new ArrayList<>(args.length);
        int i = 0;
        while (i < args.length) {
            if (optsList.contains(args[i])) {
                i += 1;
                optsList.remove(args[i]);
            } else {
                ret.add(args[i]);
            }
            i += 1;
        }
        return ret.toArray(new String[0]);
    }



    public static String getClassName(String[] args) {
        if (args == null || args.length < 2) {
            return null;
        }
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals(CLASS_OPTION)) {
                if (i+1 < args.length) {
                    return args[i+1];
                } else {
                    return null;
                }
            }
        }
        return null;
    }

    public static List<String> getAvailableStorageClassNames(String pkgName) throws ReflectionsException {
        Reflections reflections = new Reflections(pkgName);
        Set<Class<? extends Storage>> subTypes = reflections.getSubTypesOf(Storage.class);
        return subTypes.stream().map(Class::getSimpleName).sorted().collect(Collectors.toList());
    }

    public static String getStorageClassPath(String pkgName, String className) throws ReflectionsException {
        Reflections reflections = new Reflections(pkgName);
        Set<Class<? extends Storage>> subTypes = reflections.getSubTypesOf(Storage.class);
        for (Class<?> name:subTypes) {
            if (name.getSimpleName().equalsIgnoreCase(className)) {
                return name.getName();
            }
        }
        return null;
    }

    public static String searchList(List<String> list, String name) {
        for (String st: list) {
            if (st.equalsIgnoreCase(name)) {
                return st;
            }
        }
        return null;
    }

}

