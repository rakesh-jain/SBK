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

import io.sbk.api.Benchmark;
import io.sbk.api.RWCount;
import io.sbk.api.grpc.LatenciesRecord;
import io.sbk.perl.LatencyRecord;
import io.sbk.perl.Print;
import io.sbk.perl.ReportLatencies;
import io.sbk.perl.Time;
import io.sbk.perl.LatencyRecordWindow;
import io.sbk.system.Printer;
import lombok.Synchronized;

import javax.annotation.concurrent.GuardedBy;
import java.io.IOException;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;


public class LatenciesRecordsBenchmark implements Benchmark {
    private final Time time;
    private final int reportingIntervalMS;
    private final LatencyRecordWindow window;
    private final ReportLatencies reportLatencies;
    private final RWCount rwCount;
    private final Print logger;
    private final LinkedBlockingQueue<LatenciesRecord> queue;
    private final HashMap<Long, RW> table;

    @GuardedBy("this")
    private CompletableFuture<Void> retFuture;

    @GuardedBy("this")
    private CompletableFuture<Void> qFuture;

    public LatenciesRecordsBenchmark(LatencyRecordWindow window, Time time, int reportingIntervalMS,
                                     ReportLatencies reportLatencies,  RWCount rwCount, Print logger,
                                     LinkedBlockingQueue<LatenciesRecord> queue) {
        this.window = window;
        this.time = time;
        this.reportingIntervalMS = reportingIntervalMS;
        this.reportLatencies = reportLatencies;
        this.rwCount = rwCount;
        this.logger = logger;
        this.queue = queue;
        this.table = new HashMap<>();
        this.retFuture = null;
        this.qFuture = null;
    }

    void run() throws InterruptedException {
        LatenciesRecord record;
        boolean doWork = true;
        Printer.log.info("LatenciesRecord Benchmark Started" );
        long currentTime = time.getCurrentTime();
        window.reset(currentTime);
        final LatencyRecord latencyRecord = new LatencyRecord();
        final RW rwStore = new RW();
        while (doWork) {
            record = queue.poll(reportingIntervalMS, TimeUnit.MILLISECONDS);
            if (record != null) {
                if (record.getSequenceNumber() > 0) {
                    addRW(record.getClientID(), record.getReaders(), record.getWriters(),
                            record.getMaxReaders(), record.getMaxWriters());
                    window.maxLatency = Math.max(record.getMaxLatency(), window.maxLatency);
                    window.totalRecords += record.getTotalRecords();
                    window.totalBytes += record.getTotalBytes();
                    window.totalLatency += record.getTotalLatency();
                    window.higherLatencyDiscardRecords += record.getHigherLatencyDiscardRecords();
                    window.lowerLatencyDiscardRecords += record.getLowerLatencyDiscardRecords();
                    window.validLatencyRecords += record.getValidLatencyRecords();
                    window.invalidLatencyRecords += record.getInvalidLatencyRecords();
                    record.getLatencyMap().forEach(window::reportLatency);
                } else {
                    doWork = false;
                }
            }
            currentTime = time.getCurrentTime();
            if (window.elapsedMilliSeconds(currentTime) > reportingIntervalMS) {
                sumRW(rwStore);
                rwCount.setReaders(rwStore.readers);
                rwCount.setWriters(rwStore.writers);
                rwCount.setMaxReaders(rwStore.maxReaders);
                rwCount.setMaxWriters(rwStore.maxWriters);
                window.print(currentTime, logger, reportLatencies);
                window.reset(currentTime);
            }
        }

        if (window.totalRecords > 0) {
            sumRW(rwStore);
            rwCount.setReaders(rwStore.readers);
            rwCount.setWriters(rwStore.writers);
            rwCount.setMaxReaders(rwStore.maxReaders);
            rwCount.setMaxWriters(rwStore.maxWriters);
            window.print(time.getCurrentTime(), logger, reportLatencies);
        }
    }


    private static class RW {
        public int readers;
        public int writers;
        public int maxReaders;
        public int maxWriters;

        public RW() {
            reset();
        }

        public void reset() {
            readers = writers = maxWriters = maxReaders = 0;
        }


        public void update(int readers, int writers, int maxReaders, int maxWriters) {
            this.readers = Math.max(this.readers, readers);
            this.writers = Math.max(this.writers, writers);
            this.maxReaders = Math.max(this.maxReaders, maxReaders);
            this.maxWriters = Math.max(this.maxWriters, maxWriters);
        }

        public void update(RW rw) {
            update(rw.readers, rw.writers, rw.maxReaders, rw.maxWriters);
        }
    }



    private void addRW(long key, int readers, int writers, int maxReaders, int maxWriters) {
        RW cur = table.get(key);
        if (cur == null) {
            cur = new RW();
            table.put(key, cur);
        }
        cur.update(readers, writers, maxReaders, maxWriters);
    }

    private void sumRW(RW ret) {
        ret.reset();
        table.forEach((k, data) -> {
            ret.readers += data.readers;
            ret.writers += data.writers;
            ret.maxReaders += data.maxReaders;
            ret.maxWriters += data.maxWriters;
        });
        table.clear();
    }


    @Synchronized
    private void shutdown(Throwable ex) {
        if (retFuture == null) {
            return;
        }

        if (retFuture.isDone()) {
            retFuture = null;
            return;
        }

        if (qFuture != null) {
            if (!qFuture.isDone()) {
                try {
                    queue.put(LatenciesRecord.newBuilder().setSequenceNumber(-1).build());
                    qFuture.get();
                } catch (ExecutionException | InterruptedException e) {
                    e.printStackTrace();
                }
            }
            qFuture = null;
        }
        if (ex != null) {
            Printer.log.warn("LatenciesRecord Benchmark with Exception:" + ex.toString());
            retFuture.completeExceptionally(ex);
        } else  {
            Printer.log.info("LatenciesRecord Benchmark Shutdown" );
            retFuture.complete(null);
        }
        retFuture = null;
    }



    @Override
    @Synchronized
    public CompletableFuture<Void> start() throws IOException, InterruptedException, ExecutionException,
            IllegalStateException {
        if (retFuture != null) {
            throw  new IllegalStateException("LatenciesRecord Benchmark is already started\n");
        }
        retFuture = new CompletableFuture<>();
        qFuture =  CompletableFuture.runAsync(() -> {
            try {
                run();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });
        qFuture.whenComplete((ret, ex) -> {
            shutdown(ex);
        });
        return retFuture;
    }

    @Override
    public void stop() {
        shutdown(null);
    }

}


