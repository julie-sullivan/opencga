/*
 * Copyright 2015-2020 OpenCB
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.opencb.opencga.analysis.wrappers;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.opencb.commons.exec.Command;
import org.opencb.opencga.core.exceptions.ToolException;
import org.opencb.opencga.core.models.common.Enums;
import org.opencb.opencga.core.tools.annotations.Tool;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

@Tool(id = DeeptoolsWrapperAnalysis.ID, resource = Enums.Resource.ALIGNMENT, description = DeeptoolsWrapperAnalysis.DESCRIPTION)
public class DeeptoolsWrapperAnalysis extends OpenCgaWrapperAnalysis {

    public final static String ID = "deeptools";
    public final static String DESCRIPTION = "Deeptools is a suite of python tools particularly developed for the efficient analysis of"
            + " high-throughput sequencing data, such as ChIP-seq, RNA-seq or MNase-seq.";

    public final static String DEEPTOOLS_DOCKER_IMAGE = "dhspence/docker-deeptools";

    private String command;
    private String bamFile;

    protected void check() throws Exception {
        super.check();

        if (StringUtils.isEmpty(command)) {
            throw new ToolException("Missing deeptools command. Supported command is 'bamCoverage'");
        }

        switch (command) {
            case "bamCoverage":
                if (StringUtils.isEmpty(bamFile)) {
                    throw new ToolException("Missing BAM file when executing 'deeptools " + command + "'.");
                }
                break;
            default:
                // TODO: support the remaining deeptools commands
                throw new ToolException("Deeptools command '" + command + "' is not available. Supported command is"
                        + " 'bamCoverage'");
        }

    }

    @Override
    protected void run() throws Exception {
        step(() -> {

            if (command.equals("bamCoverage")) {
                org.opencb.opencga.core.models.file.File catalogBamFile = getCatalogFile(bamFile);
                Path bamPath = Paths.get(catalogBamFile.getUri());
                Path newCoveragePath = getOutDir().resolve(bamPath.getFileName() + ".bw");
                Path prevCoveragePath = bamPath.getParent().resolve(newCoveragePath.getFileName());

                if (!prevCoveragePath.toFile().exists()) {
                    String commandLine = getCommandLine();
                    logger.info("Deeptools command line: " + commandLine);
                    // Execute command and redirect stdout and stderr to the files: stdout.txt and stderr.txt
                    Command cmd = new Command(getCommandLine())
                            .setOutputOutputStream(
                                    new DataOutputStream(new FileOutputStream(getScratchDir().resolve(STDOUT_FILENAME).toFile())))
                            .setErrorOutputStream(
                                    new DataOutputStream(new FileOutputStream(getScratchDir().resolve(STDERR_FILENAME).toFile())));

                    cmd.run();

                    if (newCoveragePath.toFile().exists()) {
                        Path dest = Paths.get(new File(catalogBamFile.getUri()).getParent());

                        moveFile(getStudy(), newCoveragePath, dest, catalogBamFile.getPath(), token);
                    } else {
                        File file = new File(getScratchDir() + "/" + STDERR_FILENAME);
                        String msg = "Something wrong executing Deeptools bamCoverage";
                        if (file.exists()) {
                            msg = StringUtils.join(FileUtils.readLines(file, Charset.defaultCharset()), ". ");
                        }
                        throw new ToolException(msg);
                    }
                } else {
                    addWarning("Skipping BAM coverage from Deeptools: coverage BigWig file already exists at " + prevCoveragePath);
                }
//
//                boolean isLinked = true;
//                OpenCGAResult<org.opencb.opencga.core.models.file.File> fileResult;
//                try {
//                    fileResult = catalogManager.getFileManager().get(getStudy(), catalogBamFile.getPath(), QueryOptions.empty(), token);
//                    if (fileResult.getNumResults() <= 0) {
//                        isLinked = false;
//                    }
//                } catch (CatalogException e) {
//                    isLinked = false;
//                }
//                if (!isLinked) {
//                    catalogManager.getFileManager().link(getStudy(), prevCoveragePath.toUri(), Paths.get(catalogBamFile.getPath())
//                                    .getParent().toString(), new ObjectMap("parents", true), token);
//                }
            }
        });
    }

//    @Override
//    public String getDockerImageName() {
//        return DEEPTOOLS_DOCKER_IMAGE;
//    }

//    @Override
    public String getCommandLine() throws ToolException {
        StringBuilder sb = new StringBuilder("docker run ");

        // Mount management
        Map<String, String> srcTargetMap = new HashMap<>();
        updateFileMaps(bamFile, sb, fileUriMap, srcTargetMap);

        sb.append("--mount type=bind,source=\"")
                .append(getOutDir().toAbsolutePath()).append("\",target=\"").append(DOCKER_OUTPUT_PATH).append("\" ");

        // Docker image and version
        sb.append(DEEPTOOLS_DOCKER_IMAGE);
        if (params.containsKey(DOCKER_IMAGE_VERSION_PARAM)) {
            sb.append(":").append(params.getString(DOCKER_IMAGE_VERSION_PARAM));
        }

        // Deeptools command
        sb.append(" ").append(command);

        // Deeptools options
        for (String param : params.keySet()) {
            if (checkParam(param)) {
                String value = params.getString(param);
                if (param.length() >= 3) {
                    sb.append(" --");
                } else {
                    sb.append(" -");
                }
                sb.append(param);
                if (StringUtils.isNotEmpty(value)) {
                    sb.append(" ").append(value);
                }
            }
        }

        switch (command) {
            case "bamCoverage": {
                File file = new File(fileUriMap.get(bamFile).getPath());
                String coverageFilename = file.getName() + ".bw";

                sb.append(" -b ").append(srcTargetMap.get(file.getParentFile().getAbsolutePath())).append("/").append(file.getName());
                sb.append(" -o ").append(DOCKER_OUTPUT_PATH).append("/").append(coverageFilename);
                break;
            }
        }

        return sb.toString();
    }

    private boolean checkParam(String param) {
        if (param.equals(DOCKER_IMAGE_VERSION_PARAM)) {
            return false;
        } else if ("bamCoverage".equals(command)) {
            if ("o".equals(param) || "b".equals(param) || "overwrite".equals(param)) {
                return false;
            }

        }
        return true;
    }

    public String getCommand() {
        return command;
    }

    public DeeptoolsWrapperAnalysis setCommand(String command) {
        this.command = command;
        return this;
    }

    public String getBamFile() {
        return bamFile;
    }

    public DeeptoolsWrapperAnalysis setBamFile(String bamFile) {
        this.bamFile = bamFile;
        return this;
    }
}
