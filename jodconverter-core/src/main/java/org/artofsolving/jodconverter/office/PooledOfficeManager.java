//
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

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Logger;

class PooledOfficeManager implements OfficeManager
{
    private final PooledOfficeManagerSettings settings;

    private final ManagedOfficeProcess managedOfficeProcess;

    private final SuspendableThreadPoolExecutor taskExecutor;

    private volatile boolean stopping = false;

    private int taskCount;

    private Future< ? > currentTask;

    private final Logger logger = Logger.getLogger(getClass().getName());

    private OfficeConnectionEventListener connectionEventListener = new OfficeConnectionEventListener()
    {
        public void connected(OfficeConnectionEvent event)
        {
            PooledOfficeManager.this.taskCount = 0;
            PooledOfficeManager.this.taskExecutor.setAvailable(true);
        }

        public void disconnected(OfficeConnectionEvent event)
        {
            PooledOfficeManager.this.taskExecutor.setAvailable(false);
            if (PooledOfficeManager.this.stopping) {
                // expected
                PooledOfficeManager.this.stopping = false;
            } else {
                PooledOfficeManager.this.logger.warning("connection lost unexpectedly; attempting restart");
                if (PooledOfficeManager.this.currentTask != null) {
                    PooledOfficeManager.this.currentTask.cancel(true);
                }
                PooledOfficeManager.this.managedOfficeProcess.restartDueToLostConnection();
            }
        }
    };

    public PooledOfficeManager(UnoUrl unoUrl)
    {
        this(new PooledOfficeManagerSettings(unoUrl));
    }

    public PooledOfficeManager(PooledOfficeManagerSettings settings)
    {
        this.settings = settings;
        this.managedOfficeProcess = new ManagedOfficeProcess(settings);
        this.managedOfficeProcess.getConnection().addConnectionEventListener(this.connectionEventListener);
        this.taskExecutor = new SuspendableThreadPoolExecutor(new NamedThreadFactory("OfficeTaskThread"));
    }

    public void execute(final OfficeTask task) throws OfficeException
    {
        Future< ? > futureTask = this.taskExecutor.submit(new Runnable()
        {
            public void run()
            {
                if (PooledOfficeManager.this.settings.getMaxTasksPerProcess() > 0
                    && ++PooledOfficeManager.this.taskCount == PooledOfficeManager.this.settings
                        .getMaxTasksPerProcess() + 1) {
                    PooledOfficeManager.this.logger.info(String.format(
                        "reached limit of %d maxTasksPerProcess: restarting", PooledOfficeManager.this.settings
                            .getMaxTasksPerProcess()));
                    PooledOfficeManager.this.taskExecutor.setAvailable(false);
                    PooledOfficeManager.this.stopping = true;
                    PooledOfficeManager.this.managedOfficeProcess.restartAndWait();
                    // FIXME taskCount will be 0 rather than 1 at this point
                }
                task.execute(PooledOfficeManager.this.managedOfficeProcess.getConnection());
            }
        });
        this.currentTask = futureTask;
        try {
            futureTask.get(this.settings.getTaskExecutionTimeout(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException timeoutException) {
            this.managedOfficeProcess.restartDueToTaskTimeout();
            throw new OfficeException("task did not complete within timeout", timeoutException);
        } catch (ExecutionException executionException) {
            if (executionException.getCause() instanceof OfficeException) {
                throw (OfficeException) executionException.getCause();
            } else {
                throw new OfficeException("task failed", executionException.getCause());
            }
        } catch (Exception exception) {
            throw new OfficeException("task failed", exception);
        }
    }

    public void start() throws OfficeException
    {
        this.managedOfficeProcess.startAndWait();
    }

    public void stop() throws OfficeException
    {
        this.taskExecutor.setAvailable(false);
        this.stopping = true;
        this.taskExecutor.shutdownNow();
        this.managedOfficeProcess.stopAndWait();
    }

    public boolean isRunning()
    {
        return this.managedOfficeProcess.isConnected();
    }
}
