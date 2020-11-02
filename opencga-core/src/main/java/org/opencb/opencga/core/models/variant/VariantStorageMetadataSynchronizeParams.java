package org.opencb.opencga.core.models.variant;

import org.opencb.opencga.core.tools.ToolParams;

import java.util.List;

public class VariantStorageMetadataSynchronizeParams extends ToolParams {
    public static final String DESCRIPTION = "";

    private List<String> files;

    public List<String> getFiles() {
        return files;
    }

    public VariantStorageMetadataSynchronizeParams setFiles(List<String> files) {
        this.files = files;
        return this;
    }
}
