package org.artofsolving.jodconverter.office;

import java.util.logging.Logger;

public class OfficeVersionDescriptor {

    protected String productName;

    protected String version;

    protected boolean useGnuStyleLongOptions = false;

    private final Logger logger = Logger.getLogger(getClass().getName());

    protected OfficeVersionDescriptor() {
        productName = "???";
        version = "???";
    }

    public OfficeVersionDescriptor(String checkString) {
        logger.fine("Building " + this.getClass().getSimpleName() + ": "
                + checkString.trim());
        String productLine = null;
        String[] lines = checkString.split("\\n");
        for (String line : lines) {
            if (line.contains("--help")) {
                useGnuStyleLongOptions = true;
            }
            String lowerLine = line.trim().toLowerCase();
            if (lowerLine.startsWith("openoffice")
                    || lowerLine.startsWith("libreoffice")) {
                productLine = line.trim();
            }
        }
        if (productLine != null) {
            String[] parts = productLine.split(" ");
            if (parts.length > 0) {
                productName = parts[0];
            } else {
                productName = "???";
            }
            if (parts.length > 1) {
                version = parts[1];
            } else {
                version = "???";
            }
        } else {
            productName = "???";
            version = "???";
        }
        logger.info("soffice info: " + toString());
    }

    public static OfficeVersionDescriptor parseFromExecutableLocation(
            String path) {

        OfficeVersionDescriptor desc = new OfficeVersionDescriptor();

        if (path.toLowerCase().contains("openoffice")) {
            desc.productName = "OpenOffice";
            desc.useGnuStyleLongOptions = false;
        }
        if (path.toLowerCase().contains("libreoffice")) {
            desc.productName = "LibreOffice";
            desc.useGnuStyleLongOptions = true;
        }

        String[] versionsToCheck = { "3.9", "3.8", "3.7", "3.6", "3.5", "3.4",
                "3.3", "3.2", "3.1", "3" };

        for (String v : versionsToCheck) {
            if (path.contains(v)) {
                desc.version = v;
                break;
            }
        }

        return desc;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public boolean useGnuStyleLongOptions() {
        return useGnuStyleLongOptions;
    }

    @Override
    public String toString() {
        return String.format(
                "Product: %s - Version: %s - useGnuStyleLongOptions: %s",
                getProductName(), getVersion(), useGnuStyleLongOptions());
    }
}
