//
// JODConverter - Java OpenDocument Converter
// Copyright 2004-2012 Mirko Nasato and contributors
//
// JODConverter is Open Source software, you can redistribute it and/or
// modify it under either (at your option) of the following licenses
//
// 1. The GNU Lesser General Public License v3 (or later)
//    -> http://www.gnu.org/licenses/lgpl-3.0.txt
// 2. The Apache License, Version 2.0
//    -> http://www.apache.org/licenses/LICENSE-2.0.txt
//
package org.artofsolving.jodconverter.office;

import java.io.File;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import org.artofsolving.jodconverter.process.ProcessManager;

class ProcessPoolOfficeManager implements OfficeManager
{
    private final BlockingQueue<PooledOfficeManager> pool;

    private final PooledOfficeManager[] pooledManagers;

    private final long taskQueueTimeout;

    private volatile boolean running = false;

    private final Logger logger = Logger.getLogger(ProcessPoolOfficeManager.class.getName());

    public ProcessPoolOfficeManager(File officeHome, UnoUrl[] unoUrls, String[] runAsArgs, File templateProfileDir,
        File workDir,
        long retryTimeout, long taskQueueTimeout, long taskExecutionTimeout, int maxTasksPerProcess,
        ProcessManager processManager)
    {
        this.taskQueueTimeout = taskQueueTimeout;
        this.pool = new ArrayBlockingQueue<PooledOfficeManager>(unoUrls.length);
        this.pooledManagers = new PooledOfficeManager[unoUrls.length];
        for (int i = 0; i < unoUrls.length; i++) {
            PooledOfficeManagerSettings settings = new PooledOfficeManagerSettings(unoUrls[i]);
            settings.setRunAsArgs(runAsArgs);
            settings.setTemplateProfileDir(templateProfileDir);
            settings.setWorkDir(workDir);
            settings.setOfficeHome(officeHome);
            settings.setRetryTimeout(retryTimeout);
            settings.setTaskExecutionTimeout(taskExecutionTimeout);
            settings.setMaxTasksPerProcess(maxTasksPerProcess);
            settings.setProcessManager(processManager);
            this.pooledManagers[i] = new PooledOfficeManager(settings);
        }
        this.logger.info("ProcessManager implementation is " + processManager.getClass().getSimpleName());
    }

    public synchronized void start() throws OfficeException
    {
        for (int i = 0; i < this.pooledManagers.length; i++) {
            this.pooledManagers[i].start();
            releaseManager(this.pooledManagers[i]);
        }
        this.running = true;
    }

    public void execute(OfficeTask task) throws IllegalStateException, OfficeException
    {
        if (!this.running) {
            throw new IllegalStateException("this OfficeManager is currently stopped");
        }
        PooledOfficeManager manager = null;
        try {
            manager = acquireManager();
            if (manager == null) {
                throw new OfficeException("no office manager available");
            }
            manager.execute(task);
        } finally {
            if (manager != null) {
                releaseManager(manager);
            }
        }
    }

    public synchronized void stop() throws OfficeException
    {
        this.running = false;
        this.logger.info("stopping");
        this.pool.clear();
        for (int i = 0; i < this.pooledManagers.length; i++) {
            this.pooledManagers[i].stop();
        }
        this.logger.info("stopped");
    }

    private PooledOfficeManager acquireManager()
    {
        try {
            return this.pool.poll(this.taskQueueTimeout, TimeUnit.MILLISECONDS);
        } catch (InterruptedException interruptedException) {
            throw new OfficeException("interrupted", interruptedException);
        }
    }

    private void releaseManager(PooledOfficeManager manager)
    {
        try {
            this.pool.put(manager);
        } catch (InterruptedException interruptedException) {
            throw new OfficeException("interrupted", interruptedException);
        }
    }

    public boolean isRunning()
    {
        return this.running;
    }
}
