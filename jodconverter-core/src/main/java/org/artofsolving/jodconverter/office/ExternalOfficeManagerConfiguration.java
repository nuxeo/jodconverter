//
// JODConverter - Java OpenDocument Converter
// Copyright 2009 Art of Solving Ltd
// Copyright 2004-2009 Mirko Nasato
//
// JODConverter is free software: you can redistribute it and/or
// modify it under the terms of the GNU Lesser General Public License
// as published by the Free Software Foundation, either version 3 of
// the License, or (at your option) any later version.
//
// JODConverter is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
// Lesser General Public License for more details.
//
// You should have received a copy of the GNU Lesser General
// Public License along with JODConverter.  If not, see
// <http://www.gnu.org/licenses/>.
//
package org.artofsolving.jodconverter.office;

import java.util.Properties;

public class ExternalOfficeManagerConfiguration extends AbstractOfficeManagerConfiguration {

    private OfficeConnectionProtocol connectionProtocol;
    private Integer portNumber;
    private String pipeName;
    private Boolean connectOnStart;

    public ExternalOfficeManagerConfiguration() {
        super();
    }

    @Override
    public ExternalOfficeManagerConfiguration load(Properties properties, String prefix) {
        return (ExternalOfficeManagerConfiguration) super.load(properties, prefix);
    }

    public ExternalOfficeManagerConfiguration setConnectionProtocol(OfficeConnectionProtocol connectionProtocol) {
        this.connectionProtocol = connectionProtocol;
        return this;
    }

    public ExternalOfficeManagerConfiguration setPortNumber(int portNumber) {
        this.portNumber = portNumber;
        return this;
    }

    public ExternalOfficeManagerConfiguration setPipeName(String pipeName) {
        this.pipeName = pipeName;
        return this;
    }

    public ExternalOfficeManagerConfiguration setConnectOnStart(boolean connectOnStart) {
        this.connectOnStart = connectOnStart;
        return this;
    }

    public OfficeManager buildOfficeManager() {
        connectionProtocol = connectionProtocol != null ? connectionProtocol : propertiesUtils.getProtocol();
        portNumber = portNumber != null ? portNumber : propertiesUtils.getPort();
        pipeName = pipeName != null ? pipeName : propertiesUtils.getPipeName();
        connectOnStart = connectOnStart != null ? connectOnStart : propertiesUtils.isConnectOnStart();
        UnoUrl unoUrl = connectionProtocol == OfficeConnectionProtocol.SOCKET ? UnoUrl.socket(portNumber) : UnoUrl.pipe(pipeName);
        return new ExternalOfficeManager(unoUrl, connectOnStart);
    }

}
