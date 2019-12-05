/*
 * Copyright 2015-2017 OpenCB
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

package org.opencb.opencga.app.cli.analysis;

import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.opencb.biodata.models.variant.metadata.Aggregation;
import org.opencb.commons.datastore.core.DataResult;
import org.opencb.commons.datastore.core.Query;
import org.opencb.commons.datastore.core.QueryOptions;
import org.opencb.opencga.analysis.variant.OpenCGATestExternalResource;
import org.opencb.opencga.app.cli.internal.InternalMain;
import org.opencb.opencga.catalog.db.api.CohortDBAdaptor;
import org.opencb.opencga.catalog.db.api.FileDBAdaptor;
import org.opencb.opencga.catalog.exceptions.CatalogException;
import org.opencb.opencga.catalog.managers.CatalogManager;
import org.opencb.opencga.core.models.*;
import org.opencb.opencga.storage.core.variant.VariantStorageOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

/**
 * Created on 09/05/16
 *
 * @author Jacobo Coll &lt;jacobo167@gmail.com&gt;
 */
public class InternalMainTest {


    public static final String STORAGE_ENGINE = "mongodb";
    @Rule
    public OpenCGATestExternalResource opencga = new OpenCGATestExternalResource();


    private CatalogManager catalogManager;
    private final String userId = "user";
    private final String dbNameVariants = "opencga_variants_test";
    private final String dbNameAlignments = "opencga_alignments_test";
    private String sessionId;
    private String projectId;
    private String studyId;
    private String outdirId;
    private Logger logger = LoggerFactory.getLogger(InternalMainTest.class);
    private Map<File.Bioformat, DataStore> datastores;


    @Before
    public void setUp() throws Exception {
        catalogManager = opencga.getCatalogManager();


        opencga.clearStorageDB(STORAGE_ENGINE, dbNameVariants);
        opencga.clearStorageDB(STORAGE_ENGINE, dbNameAlignments);

        User user = catalogManager.getUserManager().create(userId, "User", "user@email.org", "user", "ACME", null, Account.Type.FULL, null).first();

        sessionId = catalogManager.getUserManager().login(userId, "user");
        projectId = catalogManager.getProjectManager().create("p1", "p1", "Project 1", "ACME", "Homo sapiens",
                null, null, "GRCh38", new QueryOptions(), sessionId).first().getId();

        datastores = new HashMap<>();
        datastores.put(File.Bioformat.VARIANT, new DataStore(STORAGE_ENGINE, dbNameVariants));
        datastores.put(File.Bioformat.ALIGNMENT, new DataStore(STORAGE_ENGINE, dbNameAlignments));


    }

    private void createStudy(Map<File.Bioformat, DataStore> datastores, String studyName) throws CatalogException {
        Study study = catalogManager.getStudyManager().create(projectId, studyName, studyName, studyName, Study.Type.CASE_CONTROL, null,
                "Study " +
                        "1", null, null, null, null, datastores, null, Collections.singletonMap(VariantStorageOptions.STATS_AGGREGATION.key(),
                        Aggregation.NONE), null, sessionId).first();
        studyId = study.getId();
        outdirId = catalogManager.getFileManager().createFolder(studyId, Paths.get("data", "index").toString(), null,
                true, null, QueryOptions.empty(), sessionId).first().getId();

    }

    @Test
    public void testVariantIndex() throws Exception {
//        Job job;
        createStudy(datastores, "s1");
        File file1 = opencga.createFile(studyId, "1000g_batches/1-500.filtered.10k.chr22.phase3_shapeit2_mvncall_integrated_v5.20130502.genotypes.vcf.gz", sessionId);
        File file2 = opencga.createFile(studyId, "1000g_batches/501-1000.filtered.10k.chr22.phase3_shapeit2_mvncall_integrated_v5.20130502.genotypes.vcf.gz", sessionId);
        File file3 = opencga.createFile(studyId, "1000g_batches/1001-1500.filtered.10k.chr22.phase3_shapeit2_mvncall_integrated_v5.20130502.genotypes.vcf.gz", sessionId);
        File file4 = opencga.createFile(studyId, "1000g_batches/1501-2000.filtered.10k.chr22.phase3_shapeit2_mvncall_integrated_v5.20130502.genotypes.vcf.gz", sessionId);
        File file5 = opencga.createFile(studyId, "1000g_batches/2001-2504.filtered.10k.chr22.phase3_shapeit2_mvncall_integrated_v5.20130502.genotypes.vcf.gz", sessionId);

        // Index file1
        execute("variant", "index",
                "--session-id", sessionId,
                "--study", "user@p1:s1",
                "-o", opencga.createTmpOutdir(studyId, "index_1", sessionId),
                "--file", file1.getPath());
        assertEquals(FileIndex.IndexStatus.READY, catalogManager.getFileManager().get(studyId, file1.getId(), null, sessionId).first().getIndex().getStatus().getName());

        assertEquals(Cohort.CohortStatus.NONE, catalogManager.getCohortManager().search(studyId, new Query(CohortDBAdaptor.QueryParams.ID.key(), "ALL"), null, sessionId).first().getStatus().getName());

//        job = catalogManager.getJobManager().get(studyId, new Query(JobDBAdaptor.QueryParams.INPUT.key(), file1.getId()), null, sessionId).first();
//        assertEquals(Job.JobStatus.READY, job.getStatus().getName());

        // Annotate variants from chr2 (which is not indexed)
        execute("variant", "annotate",
                "--session-id", sessionId,
                "--study", studyId,
                "-o", opencga.createTmpOutdir(studyId, "annot_2", sessionId),
                "--filter-region", "2");

        // Annotate all variants
        execute("variant", "annotate",
                "--session-id", sessionId,
                "--study", studyId,
                "-o", opencga.createTmpOutdir(studyId, "annot_all", sessionId),
                "--output-filename", "myAnnot",
                "--path", outdirId);

        File outputFile = catalogManager.getFileManager().search(studyId, new Query(FileDBAdaptor.QueryParams.NAME.key(),
                "~myAnnot"), null, sessionId).first();
        assertNotNull(outputFile);
//        job = catalogManager.getJobManager().get(outputFile.getJob().getId(), null, sessionId).first();
//        assertEquals(Job.JobStatus.READY, job.getStatus().getName());
//        assertEquals(outdirId, job.getOutDir().getId());

        // Index file2
        execute("variant", "index",
                "--session-id", sessionId,
                "--file", file2.getId(),
                "--calculate-stats",
                "-o", opencga.createTmpOutdir(studyId, "index_2", sessionId));

        assertEquals(FileIndex.IndexStatus.READY, catalogManager.getFileManager().get(studyId, file2.getId(), null, sessionId).first().getIndex()
                .getStatus().getName());
        assertEquals(Cohort.CohortStatus.READY, catalogManager.getCohortManager().search(studyId, new Query(CohortDBAdaptor.QueryParams.ID.key(), "ALL"), null, sessionId).first().getStatus().getName());

//        job = catalogManager.getJobManager().get(studyId, new Query(JobDBAdaptor.QueryParams.INPUT.key(), file2.getId()), null, sessionId).first();
//        assertEquals(Job.JobStatus.READY, job.getStatus().getName());
//        assertEquals(outdirId, job.getOutDir().getId());

        // Annotate all variants
        execute("variant", "annotate",
                "--session-id", sessionId,
                "--study", studyId,
                "-o", opencga.createTmpOutdir(studyId, "annot_all_2", sessionId));

        // Index file3
        execute("variant", "index",
                "--session-id", sessionId,
                "--file", String.valueOf(file3.getId()),
                "-o", opencga.createTmpOutdir(studyId, "index_3", sessionId));
        assertEquals(FileIndex.IndexStatus.READY, catalogManager.getFileManager().get(studyId, file3.getId(), null, sessionId).first().getIndex().getStatus().getName());
        assertEquals(Cohort.CohortStatus.INVALID, catalogManager.getCohortManager().search(studyId, new Query(CohortDBAdaptor.QueryParams.ID.key(), "ALL"), null, sessionId).first().getStatus().getName());
//        job = catalogManager.getJobManager().get(studyId, new Query(JobDBAdaptor.QueryParams.INPUT.key(), file3.getId()), null, sessionId).first();
//        assertEquals(Job.JobStatus.READY, job.getStatus().getName());
//        assertNotEquals(outdirId, job.getOutDir().getId());

//        // Index file4 and stats
//        execute("variant", "index",
//                "--session-id", sessionId,
//                "--file", String.valueOf(file4.getId()),
//                "--calculate-stats", "--queue");
//        assertEquals(FileIndex.IndexStatus.INDEXING, catalogManager.getFileManager().get(file4.getId(), null, sessionId).first().getIndex().getStatus().getName());
//        assertEquals(Cohort.CohortStatus.CALCULATING, catalogManager.getCohortManager().get(studyId, new Query(CohortDBAdaptor.QueryParams.NAME.key(), "ALL"), null, sessionId).first().getStatus().getName());
//        Job job = catalogManager.getJobManager().get(studyId, new Query(JobDBAdaptor.QueryParams.INPUT.key(), file4.getId()), null, sessionId).first();
//        assertEquals(Job.JobStatus.PREPARED, job.getStatus().getName());
//        opencga.runStorageJob(job, sessionId);
//
//        assertEquals(FileIndex.IndexStatus.READY, catalogManager.getFileManager().get(file4.getId(), null, sessionId).first().getIndex().getStatus().getName());
//        assertEquals(Cohort.CohortStatus.READY, catalogManager.getCohortManager().get(studyId, new Query(CohortDBAdaptor.QueryParams.NAME.key(), "ALL"), null, sessionId).first().getStatus().getName());
//        job = catalogManager.getJobManager().get(studyId, new Query(JobDBAdaptor.QueryParams.INPUT.key(), file4.getId()), null, sessionId).first();
//        assertEquals(Job.JobStatus.READY, job.getStatus().getName());

        // Index file5 and annotation
        execute("variant", "index",
                "--session-id", sessionId,
                "--file", file5.getId(),
                "-o", opencga.createTmpOutdir(studyId, "index_5", sessionId),
                "--annotate");
        assertEquals(FileIndex.IndexStatus.READY, catalogManager.getFileManager().get(studyId, file5.getId(), null, sessionId).first().getIndex().getStatus().getName());
        assertEquals(Cohort.CohortStatus.INVALID, catalogManager.getCohortManager().search(studyId, new Query(CohortDBAdaptor.QueryParams.ID.key(), "ALL"), null, sessionId).first().getStatus().getName());

//        job = catalogManager.getJobManager().get(studyId, new Query(JobDBAdaptor.QueryParams.INPUT.key(), file5.getId()), null, sessionId).first();
//        assertEquals(Job.JobStatus.READY, job.getStatus().getName());

        execute("variant", "stats",
                "--session-id", sessionId,
                "--study", studyId,
                "--cohort-ids", "ALL",
                "-o", opencga.createTmpOutdir(studyId, "stats_all", sessionId));
        assertEquals(Cohort.CohortStatus.READY, catalogManager.getCohortManager().search(studyId, new Query(CohortDBAdaptor.QueryParams.ID.key(), "ALL"), null, sessionId).first().getStatus().getName());


        catalogManager.getCohortManager().create(studyId, "coh1", Study.Type.CONTROL_SET, "", file1.getSamples(), null, null,
                sessionId);
        catalogManager.getCohortManager().create(studyId, "coh2", Study.Type.CONTROL_SET, "", file2.getSamples(), null, null,
                sessionId);

        execute("variant", "stats",
                "--session-id", sessionId,
                "--study", studyId,
                "--cohort-ids", "coh1",
                "-o", opencga.createTmpOutdir(studyId, "stats_coh1", sessionId));
        assertEquals(Cohort.CohortStatus.READY, catalogManager.getCohortManager().search(studyId, new Query(CohortDBAdaptor.QueryParams.ID.key(), "coh1"), null, sessionId).first().getStatus().getName());
        assertEquals(Cohort.CohortStatus.NONE, catalogManager.getCohortManager().search(studyId, new Query(CohortDBAdaptor.QueryParams.ID.key(), "coh2"), null, sessionId).first().getStatus().getName());

//        execute(new String[]{"variant", "query", "--session-id", sessionId, "--study", studyId, "--limit", "10"});
    }

    @Test
    public void testVariantIndexAndQuery() throws CatalogException, IOException {
        createStudy(datastores, "s2");
        File file1 = opencga.createFile(studyId, "1000g_batches/1-500.filtered.10k.chr22.phase3_shapeit2_mvncall_integrated_v5.20130502.genotypes.vcf.gz", sessionId);
//        File file1 = opencga.createFile(studyId, "variant-test-file.vcf.gz", sessionId);
//        File file1 = opencga.createFile(studyId, "100k.chr22.phase3_shapeit2_mvncall_integrated_v5.20130502.genotypes.vcf.gz", sessionId);
//        File file1 = opencga.createFile(studyId, "10k.chr22.phase3_shapeit2_mvncall_integrated_v5.20130502.genotypes.vcf.gz", sessionId);

        DataResult<Sample> allSamples = catalogManager.getSampleManager().search(studyId, new Query(), new QueryOptions(), sessionId);
        String c1 = catalogManager.getCohortManager().create(studyId, "C1", Study.Type.CONTROL_SET, "", allSamples.getResults().subList(0,
                allSamples.getResults().size() / 2), null, null, sessionId).first().getId();
        String c2 = catalogManager.getCohortManager().create(studyId, "C2", Study.Type.CONTROL_SET, "", allSamples.getResults().subList(allSamples.getResults().size()
                / 2 + 1, allSamples.getResults().size()), null, null, sessionId).first().getId();
        String c3 = catalogManager.getCohortManager().create(studyId, "C3", Study.Type.CONTROL_SET, "", allSamples.getResults().subList(0, 1), null,
                null, sessionId).first().getId();
        Sample sample = catalogManager.getSampleManager().create(studyId, new Sample().setId("Sample"), null, sessionId).first();
        String c4 = catalogManager.getCohortManager().create(studyId, "C4", Study.Type.CONTROL_SET, "", Collections.singletonList(sample),
                null, null, sessionId).first().getId();

        // Index file1
        execute("variant", "index",
                "--session-id", sessionId,
                "--study", studyId,
                "--file", file1.getName(),
                "-o", opencga.createTmpOutdir(studyId, "index", sessionId),
                "--calculate-stats",
                "--annotate");

        assertEquals(FileIndex.IndexStatus.READY, catalogManager.getFileManager().get(studyId, file1.getId(), null, sessionId).first().getIndex().getStatus().getName());
        assertEquals(Cohort.CohortStatus.READY, catalogManager.getCohortManager().search(studyId, new Query(CohortDBAdaptor.QueryParams.ID.key(), "ALL"), null, sessionId).first().getStatus().getName());

//        job = catalogManager.getJobManager().get(studyId, new Query(JobDBAdaptor.QueryParams.INPUT.key(), file1.getId()), null, sessionId).first();
//        assertEquals(Job.JobStatus.READY, job.getStatus().getName());

        execute("variant", "stats",
                "--session-id", sessionId,
                "--study", studyId,
                "--cohort-ids", c1 + "," + c2 + "," + c3 + "," + c4,
                "-o", opencga.createTmpOutdir(studyId, "stats", sessionId));

//        execute(new String[]{"variant", "query", "--session-id", sessionId, "--output-sample", "35,36", "--limit", "10"});


        System.out.println("------------------------------------------------------");
        System.out.println("Export output format: cellbase");
        System.out.println("------------------------------------------------------");
        execute("variant", "export-frequencies", "--session-id", sessionId, "--limit", "1000", "--output-format", "cellbase");

//        System.out.println("------------------------------------------------------");
//        System.out.println("Export output format: avro");
//        System.out.println("------------------------------------------------------");
//        execute(new String[]{"variant", "query", "--session-id", sessionId, "--output-format", "avro", "--output", "/tmp/100k.chr22.phase3_shapeit2_mvncall_integrated_v5.20130502.genotypes.vcf.gz.avro.snappy"});

        System.out.println("------------------------------------------------------");
        System.out.println("Export output format: avro.snappy");
        System.out.println("------------------------------------------------------");
        execute("variant", "query", "--session-id", sessionId, "--output-sample", "HG00096,HG00097", "--limit", "10", "--output-format", "avro.snappy");

        System.out.println("------------------------------------------------------");
        System.out.println("Export output format: vcf");
        System.out.println("------------------------------------------------------");
        execute("variant", "query", "--session-id", sessionId, "--output-sample", "HG00096,HG00097", "--limit", "10", "--output-format", "vcf");

        System.out.println("------------------------------------------------------");
        System.out.println("Export output format: cellbase (populationFrequencies)");
        System.out.println("------------------------------------------------------");
        execute("variant", "query", "--session-id", sessionId, "--output-sample", "HG00096,HG00097", "--limit", "10", "--output-format", "cellbase");

        System.out.println("------------------------------------------------------");
        System.out.println("Export output format: tsv");
        System.out.println("------------------------------------------------------");
        execute("variant", "query", "--session-id", sessionId, "--output-sample", "HG00096,HG00097", "--limit", "10", "--output-format", "stats");

        System.out.println("------------------------------------------------------");
        System.out.println("Export-frequencies output format: tsv");
        System.out.println("------------------------------------------------------");
        execute("variant", "export-frequencies", "--session-id", sessionId, "--limit", "10", "--output-format", "tsv");

        System.out.println("------------------------------------------------------");
        System.out.println("Export-frequencies output format: vcf");
        System.out.println("------------------------------------------------------");
        execute("variant", "export-frequencies", "--session-id", sessionId, "--limit", "10", "--output-format", "vcf");

        System.out.println("------------------------------------------------------");
        System.out.println("Export-frequencies output format: default");
        System.out.println("------------------------------------------------------");
        execute("variant", "export-frequencies", "--session-id", sessionId, "--limit", "10");

    }

    @Test
    public void testAlignmentIndex() throws CatalogException, IOException {
//        Job job;

        File bam = opencga.createFile(studyId, "HG00096.chrom20.small.bam", sessionId);
//        File bai = opencga.createFile(studyId, "HG00096.chrom20.small.bam.bai", sessionId);

        //String temporalDir = opencga.createTmpOutdir(studyId, "index", sessionId);

        // Index file1
        execute("alignment", "index",
                "--session-id", sessionId,
                "--study", studyId,
                "--file", bam.getName());


        java.io.File baiFile = new java.io.File(bam.getUri().getPath() + ".bai");

//        assertEquals(FileIndex.IndexStatus.READY, catalogManager.getFileManager().get(studyId, bam.getId(), null, sessionId).first().getIndex().getStatus().getName());
//        job = catalogManager.getJobManager().get(studyId, new Query(JobDBAdaptor.QueryParams.INPUT.key(), bam.getUid()), null, sessionId).first();
//        assertEquals(Job.JobStatus.READY, job.getStatus().getName());

        assertEquals(3, Files.list(baiFile.getParentFile().toPath()).collect(Collectors.toList()).size());
        assertTrue(baiFile.exists());
//        assertTrue(Files.exists(Paths.get(temporalDir).resolve("HG00096.chrom20.small.bam.bw")));
//        assertTrue(Files.exists(Paths.get(temporalDir).resolve("status.json")));

//        execute("alignment", "query", "--session-id", sessionId, "--file", "user@p1:s1:" + bam.getPath(), "--region", "20");

    }

    @Test
    public void testStatsRun() throws CatalogException, IOException {
        createStudy(datastores, "s1");

        String filename = "HG00096.chrom20.small.bam";
        File bam = opencga.createFile(studyId, filename, sessionId);

        String temporalDir = opencga.createTmpOutdir(studyId, "_run_stats", sessionId);

        // Index file1
        execute("alignment", "stats-run",
                "--session-id", sessionId,
                "--study", studyId,
                "--input-file", bam.getName(),
                "-o", temporalDir);

        assertTrue(Files.exists(Paths.get(temporalDir).resolve(filename + ".stats.txt")));
    }

    @Test
    public void testPlinkFisher() throws CatalogException, IOException {
        createStudy(datastores, "s1");

        File tpedFile = opencga.createFile(studyId, "test.tped", sessionId);
        File tfamFile = opencga.createFile(studyId, "test.tfam", sessionId);

        String temporalDir = opencga.createTmpOutdir(studyId, "plink", sessionId);

        // Plink: fisher test
        execute("variant", "plink",
                "--session-id", sessionId,
                "--study", studyId,
                "--tped-file", tpedFile.getUri().getPath(),
                "--tfam-file", tfamFile.getUri().getPath(),
                "-Dfisher=",
                "-Dout=plink-output",
                "-o", temporalDir);

        assertEquals(5, Files.list(Paths.get(temporalDir)).collect(Collectors.toList()).size());
        assertTrue(Files.exists(Paths.get(temporalDir).resolve("plink-output.assoc.fisher")));
    }

    @Test
    public void testRvtestsWaldScore() throws CatalogException, IOException {
        createStudy(datastores, "s1");

        File vcfFile = opencga.createFile(studyId, "example.vcf", sessionId);
        File phenoFile = opencga.createFile(studyId, "pheno", sessionId);

        String temporalDir = opencga.createTmpOutdir(studyId, "rvtests", sessionId);

        // Plink: fisher test
        execute("variant", "rvtests",
                "--session-id", sessionId,
                "--study", studyId,
                "--executable", "rvtest",
                "--vcf-file", vcfFile.getUri().getPath(),
                "--pheno-file", phenoFile.getUri().getPath(),
                "-Dsingle=wald,score",
                "-Dout=rvtests-output",
                "-o", temporalDir);

        assertEquals(6, Files.list(Paths.get(temporalDir)).collect(Collectors.toList()).size());
        assertTrue(Files.exists(Paths.get(temporalDir).resolve("rvtests-output.SingleWald.assoc")));
        assertTrue(Files.exists(Paths.get(temporalDir).resolve("rvtests-output.SingleScore.assoc")));
    }

    @Test
    public void testRvtestsVcf2Kinship() throws CatalogException, IOException {
        createStudy(datastores, "s1");

        File vcfFile = opencga.createFile(studyId, "example.vcf", sessionId);

        String temporalDir = opencga.createTmpOutdir(studyId, "rvtests", sessionId);

        // Plink: fisher test
        execute("variant", "rvtests",
                "--session-id", sessionId,
                "--study", studyId,
                "--executable", "vcf2kinship",
                "--vcf-file", vcfFile.getUri().getPath(),
                "-Dbn=",
                "-Dout=rvtests-output",
                "-o", temporalDir);

        assertEquals(5, Files.list(Paths.get(temporalDir)).collect(Collectors.toList()).size());
        assertTrue(Files.exists(Paths.get(temporalDir).resolve("rvtests-output.kinship")));
    }

    @Test
    public void testAlignmentWrappers() throws CatalogException, IOException {
        createStudy(datastores, "s1");

        // bwa index
        System.out.println("---------------   bwa index   ---------------");

        File fastaFile = opencga.createFile(studyId, "Homo_sapiens.GRCh38.dna.chromosome.MT.fa.gz", sessionId);

        String temporalDir1 = opencga.createTmpOutdir(studyId, "_alignment1", sessionId);

        execute("alignment", "bwa",
                "--token", sessionId,
                "--study", studyId,
                "--command", "index",
                "--fasta-file", fastaFile.getId(),
                "-o", temporalDir1);

        assertEquals(8, Files.list(Paths.get(temporalDir1)).collect(Collectors.toList()).size());
        assertTrue(Files.exists(Paths.get(temporalDir1).resolve(Paths.get(fastaFile.getUri().getPath() + ".bwt").toFile().getName())));
        assertTrue(Files.exists(Paths.get(temporalDir1).resolve(Paths.get(fastaFile.getUri().getPath() + ".pac").toFile().getName())));
        assertTrue(Files.exists(Paths.get(temporalDir1).resolve(Paths.get(fastaFile.getUri().getPath() + ".ann").toFile().getName())));
        assertTrue(Files.exists(Paths.get(temporalDir1).resolve(Paths.get(fastaFile.getUri().getPath() + ".amb").toFile().getName())));
        assertTrue(Files.exists(Paths.get(temporalDir1).resolve(Paths.get(fastaFile.getUri().getPath() + ".sa").toFile().getName())));

        // bwa mem
        System.out.println("---------------   bwa mem   ---------------");

        File fastqFile = opencga.createFile(studyId, "ERR251000.1K.fastq.gz", sessionId);

        String temporalDir2 = opencga.createTmpOutdir(studyId, "_alignment2", sessionId);
        String samFile = temporalDir2 + "/alignment.sam";

        execute("alignment", "bwa",
                "--session-id", sessionId,
                "--study", studyId,
                "--command", "mem",
                "--index-base-file", Paths.get(temporalDir1).resolve(Paths.get(fastaFile.getUri().getPath()).toFile().getName()).toString(),
                "--fastq1-file", fastqFile.getUri().getPath(),
                "--sam-file", samFile,
                "-o", temporalDir2);

//        fail("------- stop -----");

        assertEquals(4, Files.list(Paths.get(temporalDir2)).collect(Collectors.toList()).size());
        assertTrue(new java.io.File(samFile).exists());

        // samtools view (.sam -> .bam)
        System.out.println("---------------   samtools view   ---------------");


        String temporalDir3 = opencga.createTmpOutdir(studyId, "_alignment3", sessionId);
        String bamFile = temporalDir3 + "/alignment.bam";

        execute("alignment", "samtools",
                "--session-id", sessionId,
                "--study", studyId,
                "--command", "view",
                "--input-file", samFile,
                "--output-file", bamFile,
                "-Db=",
                "-DS=",
                "-o", temporalDir3);

        assertEquals(4, Files.list(Paths.get(temporalDir3)).collect(Collectors.toList()).size());
        assertTrue(new java.io.File(bamFile).exists());

        // samtools sort
        System.out.println("---------------   samtools sort   ---------------");

        String temporalDir4 = opencga.createTmpOutdir(studyId, "_alignment4", sessionId);
        String sortedBamFile = temporalDir4 + "/alignment.sorted.bam";

        execute("alignment", "samtools",
                "--session-id", sessionId,
                "--study", studyId,
                "--command", "sort",
                "--input-file", bamFile,
                "--output-file", sortedBamFile,
                "-o", temporalDir4);

        assertEquals(4, Files.list(Paths.get(temporalDir4)).collect(Collectors.toList()).size());
        assertTrue(new java.io.File(sortedBamFile).exists());

        // samtools index
        System.out.println("---------------   samtools index   ---------------");

        String temporalDir5 = opencga.createTmpOutdir(studyId, "_alignment5", sessionId);
        String baiFile = temporalDir5 + "/alignment.sorted.bam.bai";

        execute("alignment", "samtools",
                "--session-id", sessionId,
                "--study", studyId,
                "--command", "index",
                "--input-file", sortedBamFile,
                "--output-file", baiFile,
                "-o", temporalDir5);

        assertEquals(4, Files.list(Paths.get(temporalDir5)).collect(Collectors.toList()).size());
        assertTrue(new java.io.File(baiFile).exists());

        // deeptools bamCoverage
        System.out.println("---------------   deeptools bamCoverage   ---------------");

        FileUtils.copyFile(new java.io.File(baiFile), new java.io.File(temporalDir4 + "/" + new java.io.File(baiFile).getName()));
        String temporalDir6 = opencga.createTmpOutdir(studyId, "_alignment6", sessionId);
        String bwFile = temporalDir6 + "/alignment.bw";

        execute("alignment", "deeptools",
                "--session-id", sessionId,
                "--study", studyId,
                "--executable", "bamCoverage",
                "--bam-file", sortedBamFile,
                "--coverage-file", bwFile,
                "-Dof=bigwig",
                "-o", temporalDir6);

        assertEquals(4, Files.list(Paths.get(temporalDir6)).collect(Collectors.toList()).size());
        assertTrue(new java.io.File(bwFile).exists());

        // samtools stats
        System.out.println("---------------   samtools stats   ---------------");

        String temporalDir7 = opencga.createTmpOutdir(studyId, "_alignment7", sessionId);
        String statsFile = temporalDir7 + "/alignment.stats";

        execute("alignment", "samtools",
                "--session-id", sessionId,
                "--study", studyId,
                "--command", "stats",
                "--input-file", sortedBamFile,
                "--output-file", statsFile,
                "-o", temporalDir7);

        assertEquals(4, Files.list(Paths.get(temporalDir7)).collect(Collectors.toList()).size());
        assertTrue(new java.io.File(statsFile).exists());
    }


    public int execute(String... args) {
        int exitValue = InternalMain.privateMain(args);
        assertEquals(0, exitValue);
        return exitValue;
    }

}