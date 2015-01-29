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

import java.net.ConnectException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import com.sun.star.beans.XPropertySet;
import com.sun.star.bridge.XBridge;
import com.sun.star.bridge.XBridgeFactory;
import com.sun.star.comp.helper.Bootstrap;
import com.sun.star.connection.NoConnectException;
import com.sun.star.connection.XConnection;
import com.sun.star.connection.XConnector;
import com.sun.star.lang.EventObject;
import com.sun.star.lang.XComponent;
import com.sun.star.lang.XEventListener;
import com.sun.star.lang.XMultiComponentFactory;
import com.sun.star.uno.XComponentContext;

class OfficeConnection implements OfficeContext
{
    private static AtomicInteger bridgeIndex = new AtomicInteger();

    private final UnoUrl unoUrl;

    private XComponent bridgeComponent;

    private XMultiComponentFactory serviceManager;

    private XComponentContext componentContext;

    private final List<OfficeConnectionEventListener> connectionEventListeners =
        new ArrayList<OfficeConnectionEventListener>();

    private volatile boolean connected = false;

    private XEventListener bridgeListener = new XEventListener()
    {
        public void disposing(EventObject event)
        {
            if (OfficeConnection.this.connected) {
                OfficeConnection.this.connected = false;
                OfficeConnection.this.logger.info(String.format("disconnected: '%s'", OfficeConnection.this.unoUrl));
                OfficeConnectionEvent connectionEvent = new OfficeConnectionEvent(OfficeConnection.this);
                for (OfficeConnectionEventListener listener : OfficeConnection.this.connectionEventListeners) {
                    listener.disconnected(connectionEvent);
                }
            }
            // else we tried to connect to a server that doesn't speak URP
        }
    };

    private final Logger logger = Logger.getLogger(getClass().getName());

    public OfficeConnection(UnoUrl unoUrl)
    {
        this.unoUrl = unoUrl;
    }

    public void addConnectionEventListener(OfficeConnectionEventListener connectionEventListener)
    {
        this.connectionEventListeners.add(connectionEventListener);
    }

    public void connect() throws ConnectException
    {
        this.logger.fine(String.format("connecting with connectString '%s'", this.unoUrl));
        try {
            XComponentContext localContext = Bootstrap.createInitialComponentContext(null);
            XMultiComponentFactory localServiceManager = localContext.getServiceManager();
            XConnector connector =
                OfficeUtils.cast(XConnector.class, localServiceManager.createInstanceWithContext(
                "com.sun.star.connection.Connector", localContext));
            XConnection connection = connector.connect(this.unoUrl.getConnectString());
            XBridgeFactory bridgeFactory =
                OfficeUtils.cast(XBridgeFactory.class, localServiceManager.createInstanceWithContext(
                "com.sun.star.bridge.BridgeFactory", localContext));
            String bridgeName = "jodconverter_" + bridgeIndex.getAndIncrement();
            XBridge bridge = bridgeFactory.createBridge(bridgeName, "urp", connection, null);
            this.bridgeComponent = OfficeUtils.cast(XComponent.class, bridge);
            this.bridgeComponent.addEventListener(this.bridgeListener);
            this.serviceManager =
                OfficeUtils.cast(XMultiComponentFactory.class, bridge.getInstance("StarOffice.ServiceManager"));
            XPropertySet properties = OfficeUtils.cast(XPropertySet.class, this.serviceManager);
            this.componentContext =
                OfficeUtils.cast(XComponentContext.class, properties.getPropertyValue("DefaultContext"));
            this.connected = true;
            this.logger.info(String.format("connected: '%s'", this.unoUrl));
            OfficeConnectionEvent connectionEvent = new OfficeConnectionEvent(this);
            for (OfficeConnectionEventListener listener : this.connectionEventListeners) {
                listener.connected(connectionEvent);
            }
        } catch (NoConnectException connectException) {
            throw new ConnectException(String.format("connection failed: '%s'; %s", this.unoUrl, connectException
                .getMessage()));
        } catch (Exception exception) {
            throw new OfficeException("connection failed: " + this.unoUrl, exception);
        }
    }

    public boolean isConnected()
    {
        return this.connected;
    }

    public synchronized void disconnect()
    {
        this.logger.fine(String.format("disconnecting: '%s'", this.unoUrl));
        this.bridgeComponent.dispose();
    }

    public Object getService(String serviceName)
    {
        try {
            return this.serviceManager.createInstanceWithContext(serviceName, this.componentContext);
        } catch (Exception exception) {
            throw new OfficeException(String.format("failed to obtain service '%s'", serviceName), exception);
        }
    }
}
