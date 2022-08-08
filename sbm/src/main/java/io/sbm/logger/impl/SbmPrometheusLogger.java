/**
 * Copyright (c) KMG. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 */
package io.sbm.logger.impl;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.perl.api.LatencyRecord;
import io.sbk.action.Action;
import io.sbk.config.Config;
import io.sbm.logger.RamLogger;
import io.sbm.logger.SetRW;
import io.sbk.logger.impl.PrometheusLogger;
import io.sbk.logger.impl.PrometheusRWMetricsServer;
import io.sbk.params.ParsedOptions;
import io.sbk.system.Printer;
import io.time.Time;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicInteger;


/**
 * Class for Recoding/Printing benchmark results on micrometer Composite Meter Registry.
 */
public class SbmPrometheusLogger extends PrometheusLogger implements SetRW, RamLogger {
    final static String CONFIG_FILE = "sbm-metrics.properties";
    final static String SBM_PREFIX = "Sbm";
    final static int MAX_REQUEST_RW_IDS = 10;
    private AtomicInteger connections;
    private AtomicInteger maxConnections;
    private SbmMetricsPrometheusServer prometheusServer;


    /**
     * Constructor RamPrometheusLogger calling its super calls and initializing {@link #prometheusServer} = null.
     */
    public SbmPrometheusLogger() {
        super();
        prometheusServer = null;
    }

    public InputStream getMetricsConfigStream() {
        return SbmPrometheusLogger.class.getClassLoader().getResourceAsStream(CONFIG_FILE);
    }

    @Override
    @SuppressFBWarnings("EI_EXPOSE_REP")
    public @Nonnull PrometheusRWMetricsServer getPrometheusRWMetricsServer() throws IOException {
        if (prometheusServer == null) {
            prometheusServer = new SbmMetricsPrometheusServer(Config.NAME, action.name(), storageName,
                    percentiles, time, metricsConfig);
        }
        return prometheusServer;
    }

    @Override
    public void parseArgs(final ParsedOptions params) throws IllegalArgumentException {
        super.parseArgs(params);
        this.maxReaderRequestIds = MAX_REQUEST_RW_IDS;
        this.maxWriterRequestIds = MAX_REQUEST_RW_IDS;
    }

    @Override
    public void open(final ParsedOptions params, final String storageName, Action action, Time time) throws IllegalArgumentException, IOException {
        super.open(params, storageName, action, time);
        this.connections = new AtomicInteger(0);
        this.maxConnections = new AtomicInteger(0);
        Printer.log.info("SBK Connections PrometheusLogger Started");
    }


    @Override
    public void incrementConnections() {
        connections.incrementAndGet();
        maxConnections.incrementAndGet();
        if (prometheusServer != null) {
            prometheusServer.incrementConnections();
        }
    }

    @Override
    public void decrementConnections() {
        connections.decrementAndGet();
        if (prometheusServer != null) {
            prometheusServer.decrementConnections();
        }
    }

    @Override
    public void recordWriteRequests(int writerId, long startTime, long bytes, long events) {
        if (isRequestWrites) {
            super.recordWriteRequests(writerId % maxWriterRequestIds, startTime, bytes, events);
        }
    }


    @Override
    public void recordReadRequests(int readerId, long startTime, long bytes, long events) {
        if (isRequestReads) {
            super.recordReadRequests(readerId % maxReaderRequestIds, startTime, bytes, events);
        }
    }


    public void print(String ramPrefix, String prefix, int writers, int maxWriters, int readers, int maxReaders,
                      long writeRequestBytes, double writeRequestsMbPerSec, long writeRequests,
                      double writeRequestsPerSec, long readRequestBytes, double readRequestsMbPerSec,
                      long readRequests, double readRequestsPerSec, double seconds, long bytes,
                      long records, double recsPerSec, double mbPerSec,
                      double avgLatency, long minLatency, long maxLatency, long invalid, long lowerDiscard,
                      long higherDiscard, long slc1, long slc2, long[] percentileValues) {
        StringBuilder out = new StringBuilder(ramPrefix);
        out.append(String.format(" %5d Connections, %5d Max Connections: ", connections.get(), maxConnections.get()));
        out.append(prefix);
        appendResultString(out, writers, maxWriters, readers, maxReaders,
                writeRequestBytes, writeRequestsMbPerSec, writeRequests, writeRequestsPerSec,
                readRequestBytes, readRequestsMbPerSec, readRequests, readRequestsPerSec,
                seconds, bytes, records, recsPerSec, mbPerSec, avgLatency, minLatency, maxLatency,
                invalid, lowerDiscard, higherDiscard, slc1, slc2, percentileValues);
        System.out.println(out);

    }

    @Override
    public void print(int writers, int maxWriters, int readers, int maxReaders,
                      long writeRequestBytes, double writeRequestsMbPerSec, long writesRequests,
                      double writeRequestsPerSec, long readRequestBytes, double readRequestsMbPerSec,
                      long readRequests, double readRequestsPerSec, double seconds, long bytes,
                      long records, double recsPerSec, double mbPerSec,
                      double avgLatency, long minLatency, long maxLatency, long invalid, long lowerDiscard,
                      long higherDiscard, long slc1, long slc2, long[] percentileValues) {
        print(SBM_PREFIX, prefix, writers, maxWriters, readers, maxReaders,
                writeRequestBytes, writeRequestsMbPerSec, writesRequests, writeRequestsPerSec,
                readRequests, readRequestsMbPerSec, readRequests, readRequestsPerSec,
                seconds, bytes, records, recsPerSec, mbPerSec, avgLatency, minLatency, maxLatency,
                invalid, lowerDiscard, higherDiscard, slc1, slc2, percentileValues);

        if (prometheusServer != null) {
            prometheusServer.print(seconds, bytes, records, recsPerSec, mbPerSec, avgLatency, minLatency,
                    maxLatency, invalid, lowerDiscard, higherDiscard, slc1, slc2, percentileValues);
        }
        if (csvEnable) {
            writeToCSV(SBM_PREFIX, REGULAR_PRINT, connections.get(), maxConnections.get(),
                    (long) seconds, bytes, records, recsPerSec, mbPerSec, avgLatency, maxLatency, invalid,
                    lowerDiscard, higherDiscard, slc1, slc2, percentileValues);
        }
    }

    @Override
    public void printTotal(int writers, int maxWriters, int readers, int maxReaders,
                           long writeRequestBytes, double writeRequestsMbPerSec, long writeRequests,
                           double writeRequestsPerSec, long readRequestBytes, double readRequestsMBPerSec,
                           long readRequests, double readRequestsPerSec, double seconds, long bytes,
                           long records, double recsPerSec, double mbPerSec,
                           double avgLatency, long minLatency, long maxLatency, long invalid, long lowerDiscard,
                           long higherDiscard, long slc1, long slc2, long[] percentileValues) {
        print("Total : " + SBM_PREFIX, prefix, writers, maxWriters, readers, maxReaders,
                writeRequestBytes, writeRequestsMbPerSec, writeRequests, writeRequestsPerSec,
                readRequestBytes, readRequestsMBPerSec, readRequests, readRequestsPerSec,
                seconds, bytes, records, recsPerSec, mbPerSec, avgLatency, minLatency, maxLatency,
                invalid, lowerDiscard, higherDiscard, slc1, slc2, percentileValues);

        if (csvEnable) {
            writeToCSV(SBM_PREFIX, TOTAL_PRINT, connections.get(), maxConnections.get(),
                    (long) seconds, bytes, records, recsPerSec, mbPerSec, avgLatency, maxLatency, invalid,
                    lowerDiscard, higherDiscard, slc1, slc2, percentileValues);
        }
    }


    @Override
    public void reportLatencyRecord(LatencyRecord record) {

    }

    @Override
    public void reportLatency(long latency, long count) {

    }

    @Override
    public void setWriters(int val) {
        writers.set(val);
    }

    @Override
    public void setMaxWriters(int val) {
        maxWriters.set(Math.max(maxWriters.get(), val));
    }

    @Override
    public void setReaders(int val) {
        readers.set(val);
    }

    @Override
    public void setMaxReaders(int val) {
        maxReaders.set(Math.max(maxReaders.get(), val));
    }
}
