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

package org.opencb.opencga.analysis.variant.circos;

import org.apache.commons.collections.CollectionUtils;
import org.opencb.biodata.models.variant.Variant;
import org.opencb.biodata.models.variant.avro.BreakendMate;
import org.opencb.biodata.models.variant.avro.StructuralVariantType;
import org.opencb.biodata.models.variant.avro.StructuralVariation;
import org.opencb.commons.datastore.core.Query;
import org.opencb.commons.datastore.core.QueryOptions;
import org.opencb.commons.utils.DockerUtils;
import org.opencb.opencga.analysis.variant.manager.VariantStorageManager;
import org.opencb.opencga.analysis.variant.manager.VariantStorageToolExecutor;
import org.opencb.opencga.core.exceptions.ToolException;
import org.opencb.opencga.core.models.variant.CircosAnalysisParams;
import org.opencb.opencga.core.models.variant.CircosTrack;
import org.opencb.opencga.core.tools.annotations.ToolExecutor;
import org.opencb.opencga.core.tools.variant.CircosAnalysisExecutor;
import org.opencb.opencga.storage.core.variant.adaptors.iterators.VariantDBIterator;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;
import java.util.concurrent.*;

import static org.opencb.opencga.analysis.wrappers.OpenCgaWrapperAnalysis.DOCKER_INPUT_PATH;
import static org.opencb.opencga.analysis.wrappers.OpenCgaWrapperAnalysis.DOCKER_OUTPUT_PATH;
import static org.opencb.opencga.storage.core.variant.adaptors.VariantQueryParam.STUDY;

@ToolExecutor(id="opencga-local", tool = CircosAnalysis.ID,
        framework = ToolExecutor.Framework.LOCAL, source = ToolExecutor.Source.STORAGE)
public class CircosLocalAnalysisExecutor extends CircosAnalysisExecutor implements VariantStorageToolExecutor {

    public final static String R_DOCKER_IMAGE = "opencb/opencga-r:2.0.0-rc1";

    private Query query;

    private File snvsFile;
    private File rearrsFile;
    private File indelsFile;
    private File cnvsFile;

    private boolean plotCopynumber = false;
    private boolean plotIndels = false;
    private boolean plotRearrangements = false;

    public CircosLocalAnalysisExecutor() {
        super();
    }

    public CircosLocalAnalysisExecutor(String study, CircosAnalysisParams params) {
        super(study, params);
    }

    @Override
    public void run() throws ToolException, IOException {

        // Create query
        Query query = new Query();
        query.put(STUDY.key(), getStudy());
        if (getCircosParams().getFilters() != null) {
            query.putAll(getCircosParams().getFilters());
        }

        // Launch a thread per query
        VariantStorageManager storageManager = getVariantStorageManager();

        ExecutorService threadPool = Executors.newFixedThreadPool(4);

        List<Future<Boolean>> futureList = new ArrayList<>(4);
        futureList.add(threadPool.submit(getNamedThread("SNV", () -> snvQuery(query, storageManager))));
        futureList.add(threadPool.submit(getNamedThread("COPY_NUMBER", () -> copyNumberQuery(query, storageManager))));
        futureList.add(threadPool.submit(getNamedThread("INDEL", ()-> indelQuery(query, storageManager))));
        futureList.add(threadPool.submit(getNamedThread("REARRANGEMENT", () -> rearrangementQuery(query, storageManager))));

        threadPool.shutdown();

        try {
            threadPool.awaitTermination(2, TimeUnit.MINUTES);
            if (!threadPool.isTerminated()) {
                for (Future<Boolean> future : futureList) {
                    future.cancel(true);
                }
            }
        } catch (InterruptedException e) {
            throw new ToolException("Error launching threads when executing the Cisco analysis", e);
        }


        // Execute R script
        // circos.R ./snvs.tsv ./indels.tsv ./cnvs.tsv ./rearrs.tsv SampleId
        String rScriptPath = getExecutorParams().getString("opencgaHome") + "/analysis/R/" + getToolId();
        List<AbstractMap.SimpleEntry<String, String>> inputBindings = new ArrayList<>();
        inputBindings.add(new AbstractMap.SimpleEntry<>(rScriptPath, DOCKER_INPUT_PATH));
        AbstractMap.SimpleEntry<String, String> outputBinding = new AbstractMap.SimpleEntry<>(getOutDir().toAbsolutePath().toString(),
                DOCKER_OUTPUT_PATH);
        String scriptParams = "R CMD Rscript --vanilla " + DOCKER_INPUT_PATH + "/circos.R"
                + (plotCopynumber ? "" : " --no_copynumber")
                + (plotIndels ? "" : " --no_indels")
                + (plotRearrangements ? "" : " --no_rearrangements")
                + " --out_path " + DOCKER_OUTPUT_PATH
                + " " + DOCKER_OUTPUT_PATH + "/" + snvsFile.getName()
                + " " + DOCKER_OUTPUT_PATH + "/" + indelsFile.getName()
                + " " + DOCKER_OUTPUT_PATH + "/" + cnvsFile.getName()
                + " " + DOCKER_OUTPUT_PATH + "/" + rearrsFile.getName()
                + " " + getCircosParams().getTitle();

        String cmdline = DockerUtils.run(R_DOCKER_IMAGE, inputBindings, outputBinding, scriptParams, null);
        System.out.println("Docker command line: " + cmdline);
    }

    /**
     * Create file with SNV variants.
     *
     * @param query General query
     * @param storageManager    Variant storage manager
     * @return True or false depending on successs
     */
    private boolean snvQuery(Query query, VariantStorageManager storageManager) {
        try {
            snvsFile = getOutDir().resolve("snvs.tsv").toFile();
            PrintWriter pw = new PrintWriter(snvsFile);
            pw.println("Chromosome\tchromStart\tchromEnd\tref\talt\tlogDistPrev");

            CircosTrack snvTrack = getCircosParams().getCircosTrackByType("SNV");
            if (snvTrack == null) {
                throw new ToolException("Missing SNV track");
            }

            int threshold;

            switch (getCircosParams().getDensity()) {
                case "HIGH":
                    threshold = Integer.MAX_VALUE;
                    break;
                case "MEDIUM":
                    threshold = 250000;
                    break;
                case "LOW":
                default:
                    threshold = 100000;
                    break;
            }

            List<Map<String, String>> filtersList = buildFiltersList(snvTrack);
            if (filtersList.size() == 1) {
                // Only one item in the filters list
                Query snvQuery = new Query(query);
                snvQuery.putAll(filtersList.get(0));
                snvQuery.put("type", "SNV");
                QueryOptions queryOptions = new QueryOptions()
                        .append(QueryOptions.INCLUDE, "id")
                        .append(QueryOptions.SORT, true);

                VariantDBIterator iterator = storageManager.iterator(snvQuery, queryOptions, getToken());

                int prevStart = 0;
                String currentChrom = "";
                while (iterator.hasNext()) {
                    Variant v = iterator.next();
                    if (v.getStart() > v.getEnd()) {
                        // Sanity check
                        addWarning("Skipping variant " + v.toString() + ", start = " + v.getStart() + ", end = " + v.getEnd());
                    } else {
                        if (!v.getChromosome().equals(currentChrom)) {
                            prevStart = 0;
                            currentChrom = v.getChromosome();
                        }
                        int dist = v.getStart() - prevStart;
                        if (dist < threshold) {
                            pw.println("chr" + v.getChromosome() + "\t" + v.getStart() + "\t" + v.getEnd() + "\t" + v.getReference() + "\t"
                                    + v.getAlternate() + "\t" + Math.log10(dist));
                        }
                        prevStart = v.getStart();
                    }
                }
            } else {
                // More than one item in the filters list
                List<Variant> nextList = new LinkedList<>();
                List<VariantDBIterator> iteratorsList = new LinkedList<>();
                for (Map<String, String> filters : filtersList) {
                    Query snvQuery = new Query(query);
                    snvQuery.putAll(filters);
                    snvQuery.put("type", "SNV");
                    QueryOptions queryOptions = new QueryOptions()
                            .append(QueryOptions.INCLUDE, "id")
                            .append(QueryOptions.SORT, true);

                    // Initilize iterator list
                    VariantDBIterator iterator = storageManager.iterator(snvQuery, queryOptions, getToken());
                    iteratorsList.add(iterator);

                    // Initialize next list
                    nextList.add(iterator.hasNext() ? iterator.next() : null);
                }

                int prevStart = 0;
                String currentChrom = "";
                while (computeHasNext(nextList)) {
                    Variant v = computeNext(iteratorsList, nextList);
                    if (v.getStart() > v.getEnd()) {
                        // Sanity check
                        addWarning("Skipping variant " + v.toString() + ", start = " + v.getStart() + ", end = " + v.getEnd());
                    } else {
                        if (!v.getChromosome().equals(currentChrom)) {
                            prevStart = 0;
                            currentChrom = v.getChromosome();
                        }
                        int dist = v.getStart() - prevStart;
                        if (dist < threshold) {
                            pw.println("chr" + v.getChromosome() + "\t" + v.getStart() + "\t" + v.getEnd() + "\t" + v.getReference() + "\t"
                                    + v.getAlternate() + "\t" + Math.log10(dist));
                        }
                        prevStart = v.getStart();
                    }
                }
            }

            pw.close();
        } catch(Exception e){
            return false;
        }
        return true;
    }


    private boolean computeHasNext(List<Variant> nextList) {
        for (Variant next : nextList) {
            if (next != null) {
                return true;
            }
        }
        return false;
    }

    private Variant computeNext(List<VariantDBIterator> iteratorsList, List<Variant> nextList) throws ToolException {
        int index = 0;
        Variant selected = nextList.get(0);
        for (int i = 1; i < nextList.size(); i++) {
            if (selected == null) {
                selected = nextList.get(i);
                index = i;
            } else {
                if (isPrevious(nextList.get(i), selected)) {
                    selected = nextList.get(i);
                    index = i;
                }
            }
        }

        if (selected == null) {
            throw new ToolException("Something wrong happened computing next variant in Circos analysis");
        }

        // Update next list and return the selected one
        nextList.set(index, iteratorsList.get(index).hasNext() ? iteratorsList.get(index).next() : null);
        return selected;
    }

    private boolean isPrevious(Variant v1, Variant v2) {
        if (v1 == null) {
            return false;
        }
        if (v1.getChromosome().equals(v2.getChromosome())) {
            return v1.getStart() <= v2.getStart();
        }
        return v1.getChromosome().compareToIgnoreCase(v2.getChromosome()) <= 0 ;
    }

    /**
     * Create file with copy-number variants.
     *
     * @param query General query
     * @param storageManager    Variant storage manager
     * @return True or false depending on successs
     */
    private boolean copyNumberQuery(Query query, VariantStorageManager storageManager) {
        try {
            cnvsFile = getOutDir().resolve("cnvs.tsv").toFile();
            PrintWriter pw = new PrintWriter(cnvsFile);
            pw.println("Chromosome\tchromStart\tchromEnd\tlabel\tmajorCopyNumber\tminorCopyNumber");

            CircosTrack copyNumberTrack = getCircosParams().getCircosTrackByType("COPY-NUMBER");
            if (copyNumberTrack != null) {
                plotCopynumber = true;

                List<Map<String, String>> filtersList = buildFiltersList(copyNumberTrack);

                QueryOptions queryOptions = new QueryOptions(QueryOptions.INCLUDE, "id,sv");

                for (Map<String, String> filters : filtersList) {
                    Query copyNumberQuery = new Query(query);
                    copyNumberQuery.putAll(filters);
                    copyNumberQuery.put("type", "CNV");

                    VariantDBIterator iterator = storageManager.iterator(copyNumberQuery, queryOptions, getToken());

                    while (iterator.hasNext()) {
                        Variant v = iterator.next();
                        StructuralVariation sv = v.getSv();
                        if (sv != null) {
                            if (sv.getType() == StructuralVariantType.COPY_NUMBER_GAIN) {
                                pw.println("chr" + v.getChromosome() + "\t" + v.getStart() + "\t" + v.getEnd() + "\tNONE\t"
                                        + sv.getCopyNumber() + "\t1");
                            } else if (sv.getType() == StructuralVariantType.COPY_NUMBER_LOSS) {
                                pw.println(v.getChromosome() + "\t" + v.getStart() + "\t" + v.getEnd() + "\tNONE\t"
                                        + "1\t" + sv.getCopyNumber());
                            } else {
                                addWarning("Skipping variant " + v.toString() + ": invalid SV type " + sv.getType() + " for copy-number (CNV)");
                            }
                        } else {
                            addWarning("Skipping variant " + v.toString() + ": SV is empty for copy-number (CNV)");
                        }
                    }
                }
            }

            pw.close();
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    /**
     * Create file with INDEL variants.
     *
     * @param query General query
     * @param storageManager    Variant storage manager
     * @return True or false depending on successs
     */
    private boolean indelQuery(Query query, VariantStorageManager storageManager) {
        try {
            indelsFile = getOutDir().resolve("indels.tsv").toFile();
            PrintWriter pw = new PrintWriter(indelsFile);
            pw.println("Chromosome\tchromStart\tchromEnd\ttype\tclassification");

            CircosTrack copyNumberTrack = getCircosParams().getCircosTrackByType("INDEL");
            if (copyNumberTrack != null) {
                plotIndels = true;

                List<Map<String, String>> filtersList = buildFiltersList(copyNumberTrack);

                QueryOptions queryOptions = new QueryOptions(QueryOptions.INCLUDE, "id");

                for (Map<String, String> filters : filtersList) {
                    Query indelQuery = new Query(query);
                    indelQuery.putAll(filters);
                    indelQuery.put("type", "INSERTION,DELETION,INDEL");

                    VariantDBIterator iterator = storageManager.iterator(indelQuery, queryOptions, getToken());

                    while (iterator.hasNext()) {
                        Variant v = iterator.next();
                        switch (v.getType()) {
                            case INSERTION: {
                                pw.println("chr" + v.getChromosome() + "\t" + v.getStart() + "\t" + v.getEnd() + "\tI\tNone");
                                break;
                            }
                            case DELETION: {
                                pw.println("chr" + v.getChromosome() + "\t" + v.getStart() + "\t" + v.getEnd() + "\tD\tNone");
                                break;
                            }
                            case INDEL: {
                                pw.println("chr" + v.getChromosome() + "\t" + v.getStart() + "\t" + v.getEnd() + "\tDI\tNone");
                                break;
                            }
                            default: {
                                // Sanity check
                                addWarning("Skipping variant " + v.toString() + ": invalid type " + v.getType()
                                        + " for INSERTION, DELETION, INDEL");
                                break;
                            }
                        }
                    }
                }
            }

            pw.close();
        } catch(Exception e){
            return false;
//            throw new ToolExecutorException(e);
        }
        return true;
    }

    /**
     * Create file with rearrangement variants.
     *
     * @param query General query
     * @param storageManager    Variant storage manager
     * @return True or false depending on successs
     */
    private boolean rearrangementQuery(Query query, VariantStorageManager storageManager) {
        try {
            rearrsFile = getOutDir().resolve("rearrs.tsv").toFile();
            PrintWriter pw = new PrintWriter(rearrsFile);
            pw.println("Chromosome\tchromStart\tchromEnd\tChromosome.1\tchromStart.1\tchromEnd.1\ttype");

            CircosTrack rearrangementTrack = getCircosParams().getCircosTrackByType("REARRANGEMENT");
            if (rearrangementTrack != null) {
                plotRearrangements = true;

                List<Map<String, String>> filtersList = buildFiltersList(rearrangementTrack);


                QueryOptions queryOptions = new QueryOptions(QueryOptions.INCLUDE, "id,sv");

                for (Map<String, String> filters : filtersList) {
                    Query rearrangementQuery = new Query(query);
                    rearrangementQuery.putAll(filters);
                    rearrangementQuery.put("type", "DELETION,TRANSLOCATION,INVERSION,DUPLICATION,BREAKEND");

                    VariantDBIterator iterator = storageManager.iterator(rearrangementQuery, queryOptions, getToken());

                    while (iterator.hasNext()) {
                        Variant v = iterator.next();
                        String type = null;
                        switch (v.getType()) {
                            case DELETION: {
                                type = "DEL";
                                break;
                            }
                            case BREAKEND:
                            case TRANSLOCATION: {
                                type = "BND";
                                break;
                            }
                            case DUPLICATION: {
                                type = "DUP";
                                break;
                            }
                            case INVERSION: {
                                type = "INV";
                                break;
                            }
                            default: {
                                // Sanity check
                                addWarning("Skipping variant " + v.toString() + ": invalid type " + v.getType() + " for rearrangement");
                                break;
                            }
                        }

                        if (type != null) {
                            // Check structural variation
                            StructuralVariation sv = v.getSv();
                            if (sv != null) {
                                if (sv.getBreakend() != null) {
                                    if (sv.getBreakend().getMate() != null) {
                                        BreakendMate mate = sv.getBreakend().getMate();
                                        pw.println("chr" + v.getChromosome() + "\t" + v.getStart() + "\t" + v.getEnd() + "\tchr"
                                                + mate.getChromosome() + "\t" + mate.getPosition() + "\t" + mate.getPosition() + "\t" + type);
                                    } else {
                                        addWarning("Skipping variant " + v.toString() + ": " + v.getType() + ", breakend mate is empty for"
                                                + " rearrangement");
                                    }
                                } else {
                                    addWarning("Skipping variant " + v.toString() + ": " + v.getType() + ", breakend is empty for"
                                            + " rearrangement");
                                }
                            } else {
                                addWarning("Skipping variant " + v.toString() + ": " + v.getType() + ", SV is empty for rearrangement");
                            }
                        }
                    }
                }
            }

            pw.close();
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    private <T> Callable<T> getNamedThread(String name, Callable<T> c) {
        String parentThreadName = Thread.currentThread().getName();
        return () -> {
            Thread.currentThread().setName(parentThreadName + "-" + name);
            return c.call();
        };
    }

    private List<Map<String, String>> buildFiltersList(CircosTrack track) throws ToolException {
        if (CollectionUtils.isNotEmpty(track.getFilters())) {
            return track.getFilters();
        }

        Map<String, String> map = new HashMap<>();
        if ("COPY-NUMBER".equals(track.getType())) {
            map.put("type", "CNV");
        } else if ("INDEL".equals(track.getType())) {
            map.put("type", "INSERTION,DELETION,INDEL");
        } else if ("REARRANGEMENT".equals(track.getType())) {
            map.put("type", "DELETION,TRANSLOCATION,INVERSION,DUPLICATION,BREAKEND");
        } else if ("SNV".equals(track.getType())) {
            map.put("type", "SNV");
        } else {
            throw new ToolException("Unknown Circos track type: '" + track.getType() + "'");
        }
        List<Map<String, String>> filtersList = new ArrayList<>();
        filtersList.add(map);
        return filtersList;
    }
}
