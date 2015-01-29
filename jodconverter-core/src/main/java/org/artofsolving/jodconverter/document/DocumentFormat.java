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
package org.artofsolving.jodconverter.document;

import java.util.HashMap;
import java.util.Map;

public class DocumentFormat
{
    private String name;

    private String extension;

    private String mediaType;

    private DocumentFamily inputFamily;

    private Map<String, ? > loadProperties;

    private Map<DocumentFamily, Map<String, ? >> storePropertiesByFamily;

    public DocumentFormat()
    {
        // default
    }

    public DocumentFormat(String name, String extension, String mediaType)
    {
        this.name = name;
        this.extension = extension;
        this.mediaType = mediaType;
    }

    public String getName()
    {
        return this.name;
    }

    public void setName(String name)
    {
        this.name = name;
    }

    public String getExtension()
    {
        return this.extension;
    }

    public void setExtension(String extension)
    {
        this.extension = extension;
    }

    public String getMediaType()
    {
        return this.mediaType;
    }

    public void setMediaType(String mediaType)
    {
        this.mediaType = mediaType;
    }

    public DocumentFamily getInputFamily()
    {
        return this.inputFamily;
    }

    public void setInputFamily(DocumentFamily documentFamily)
    {
        this.inputFamily = documentFamily;
    }

    public Map<String, ? > getLoadProperties()
    {
        return this.loadProperties;
    }

    public void setLoadProperties(Map<String, ? > loadProperties)
    {
        this.loadProperties = loadProperties;
    }

    public Map<DocumentFamily, Map<String, ? >> getStorePropertiesByFamily()
    {
        return this.storePropertiesByFamily;
    }

    public void setStorePropertiesByFamily(Map<DocumentFamily, Map<String, ? >> storePropertiesByFamily)
    {
        this.storePropertiesByFamily = storePropertiesByFamily;
    }

    public void setStoreProperties(DocumentFamily family, Map<String, ? > storeProperties)
    {
        if (this.storePropertiesByFamily == null) {
            this.storePropertiesByFamily = new HashMap<DocumentFamily, Map<String, ? >>();
        }
        this.storePropertiesByFamily.put(family, storeProperties);
    }

    public Map<String, ? > getStoreProperties(DocumentFamily family)
    {
        if (this.storePropertiesByFamily == null) {
            return null;
        }
        return this.storePropertiesByFamily.get(family);
    }
}
