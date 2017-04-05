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
// Contributors:
//     Laurent Doguin (Nuxeo), Julien Carsique (Nuxeo)
package org.artofsolving.jodconverter.office;

import java.io.File;
import java.util.Map;

import org.artofsolving.jodconverter.util.PlatformUtils;

import com.sun.star.beans.PropertyValue;
import com.sun.star.uno.UnoRuntime;

public class OfficeUtils {

    public static final String SERVICE_DESKTOP = "com.sun.star.frame.Desktop";

    private OfficeUtils() {
        throw new AssertionError("utility class must not be instantiated");
    }

    @SuppressWarnings("unchecked")
    public static <T> T cast(Class<T> type, Object object) {
        return (T) UnoRuntime.queryInterface(type, object);
    }

    public static PropertyValue property(String name, Object value) {
        PropertyValue propertyValue = new PropertyValue();
        propertyValue.Name = name;
        propertyValue.Value = value;
        return propertyValue;
    }

    public static PropertyValue[] toUnoProperties(Map<String, ?> properties) {
        PropertyValue[] propertyValues = new PropertyValue[properties.size()];
        int i = 0;
        for (Map.Entry<String, ?> entry : properties.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> subProperties = (Map<String, Object>) value;
                value = toUnoProperties(subProperties);
            }
            propertyValues[i++] = property(entry.getKey(), value);
        }
        return propertyValues;
    }

    public static String toUrl(File file) {
        String url = "file://" + file.toURI().getRawPath();
        if (url.endsWith("/")) {
            return url.substring(0, url.length() - 1);
        } else {
            return url;
        }
    }

    /**
     * Search for an (Open/Libre)Office install. If the System property
     * "office.home" is defined, it takes precedence.
     *
     * @see PlatformUtils#findOfficeHome()
     *
     * @return Office home found
     */
    public static File getDefaultOfficeHome() {
        String officeHome = System.getProperty("office.home");
        if (officeHome == null) {
            officeHome = PlatformUtils.findOfficeHome();
        }
        return new File(officeHome);
    }

    /**
     * Search for an (Open/Libre)Office profile. If the System property
     * "office.profile" is defined, it takes precedence.
     *
     * @see PlatformUtils#findOfficeProfileDir()
     *
     * @return Office profile found
     */
    public static File getDefaultProfileDir() {
        String officeProfile = System.getProperty("office.profile");
        if (officeProfile == null) {
            officeProfile = PlatformUtils.findOfficeProfileDir();
        }
        return new File(officeProfile);
    }

    public static File getOfficeExecutable(File officeHome) {
        if (PlatformUtils.isMac()) {
            File file = new File(officeHome, "MacOS/soffice.bin");
            if (!file.isFile()) {
                // LibreOffice 4.1.0
                file = new File(officeHome, "MacOS/soffice");
            }
            return file;
        } else {
            return new File(officeHome, "program/soffice.bin");
        }
    }

    public static String getJPipePath(File officeHome) {
        if (PlatformUtils.isMac()) {
            return new File(officeHome, "MacOS/jpipe.so").getAbsolutePath();
        } else if (PlatformUtils.isWindows()) {
            return new File(officeHome, "program/jpipe.dll").getAbsolutePath();
        } else {
            return new File(officeHome, "program/jpipe.so").getAbsolutePath();
        }
    }
}
