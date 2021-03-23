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

package org.opencb.opencga.client.rest.clients;

import org.opencb.biodata.models.clinical.interpretation.ClinicalVariant;
import org.opencb.commons.datastore.core.ObjectMap;
import org.opencb.opencga.client.config.ClientConfiguration;
import org.opencb.opencga.client.exceptions.ClientException;
import org.opencb.opencga.client.rest.AbstractParentClient;
import org.opencb.opencga.core.models.clinical.CancerTieringInterpretationAnalysisParams;
import org.opencb.opencga.core.models.clinical.ClinicalAnalysis;
import org.opencb.opencga.core.models.clinical.ClinicalAnalysisAclUpdateParams;
import org.opencb.opencga.core.models.clinical.ClinicalAnalysisCreateParams;
import org.opencb.opencga.core.models.clinical.ClinicalAnalysisUpdateParams;
import org.opencb.opencga.core.models.clinical.Interpretation;
import org.opencb.opencga.core.models.clinical.InterpretationCreateParams;
import org.opencb.opencga.core.models.clinical.InterpretationMergeParams;
import org.opencb.opencga.core.models.clinical.InterpretationUpdateParams;
import org.opencb.opencga.core.models.clinical.TeamInterpretationAnalysisParams;
import org.opencb.opencga.core.models.clinical.TieringInterpretationAnalysisParams;
import org.opencb.opencga.core.models.clinical.ZettaInterpretationAnalysisParams;
import org.opencb.opencga.core.models.job.Job;
import org.opencb.opencga.core.response.RestResponse;


/*
* WARNING: AUTOGENERATED CODE
*
* This code was generated by a tool.
* Autogenerated on: 2020-11-19 12:00:56
*
* Manual changes to this file may cause unexpected behavior in your application.
* Manual changes to this file will be overwritten if the code is regenerated.
*/


/**
 * This class contains methods for the Clinical webservices.
 *    Client version: 2.0.0
 *    PATH: analysis/search
 */
public class ClinicalClient extends AbstractParentClient {

    public ClinicalClient(String token, ClientConfiguration configuration) {
        super(token, configuration);
    }

    /**
     * Update the set of permissions granted for the member.
     * @param members Comma separated list of user or group IDs.
     * @param action Action to be performed [ADD, SET, REMOVE or RESET].
     * @param data JSON containing the parameters to add ACLs.
     * @param params Map containing any of the following optional parameters.
     *       study: Study [[user@]project:]study where study and project can be either the ID or UUID.
     *       propagate: Propagate permissions to related families, individuals, samples and files.
     * @return a RestResponse object.
     * @throws ClientException ClientException if there is any server error.
     */
    public RestResponse<ObjectMap> updateAcl(String members, String action, ClinicalAnalysisAclUpdateParams data, ObjectMap params)
            throws ClientException {
        params = params != null ? params : new ObjectMap();
        params.putIfNotNull("action", action);
        params.put("body", data);
        return execute("analysis", null, "search/acl", members, "update", params, POST, ObjectMap.class);
    }

    /**
     * Create a new search analysis.
     * @param data JSON containing search analysis information.
     * @param params Map containing any of the following optional parameters.
     *       study: Study [[user@]project:]study where study and project can be either the ID or UUID.
     *       createDefaultInterpretation: Flag to create and initialise a default primary interpretation (Id will be
     *            '{clinicalAnalysisId}.1').
     * @return a RestResponse object.
     * @throws ClientException ClientException if there is any server error.
     */
    public RestResponse<ClinicalAnalysis> create(ClinicalAnalysisCreateParams data, ObjectMap params) throws ClientException {
        params = params != null ? params : new ObjectMap();
        params.put("body", data);
        return execute("analysis", null, "search", null, "create", params, POST, ClinicalAnalysis.class);
    }

    /**
     * Clinical Analysis distinct method.
     * @param field Field for which to obtain the distinct values.
     * @param params Map containing any of the following optional parameters.
     *       study: Study [[user@]project:]study where study and project can be either the ID or UUID.
     *       id: Clinical analysis ID.
     *       type: Clinical analysis type.
     *       priority: Priority.
     *       creationDate: Creation date. Format: yyyyMMddHHmmss. Examples: >2018, 2017-2018, <201805.
     *       modificationDate: Modification date. Format: yyyyMMddHHmmss. Examples: >2018, 2017-2018, <201805.
     *       internalStatus: Filter by internal status.
     *       status: Filter by status.
     *       dueDate: Due date (Format: yyyyMMddHHmmss. Examples: >2018, 2017-2018, <201805...).
     *       description: Description.
     *       family: Family id.
     *       proband: Proband id.
     *       sample: Sample id associated to the proband or any member of a family.
     *       individual: Proband id or any member id of a family.
     *       analystAssignee: Clinical analyst assignee.
     *       disorder: Disorder ID or name.
     *       flags: Flags.
     *       release: Release value.
     *       attributes: Text attributes (Format: sex=male,age>20 ...).
     * @return a RestResponse object.
     * @throws ClientException ClientException if there is any server error.
     */
    public RestResponse<ObjectMap> distinct(String field, ObjectMap params) throws ClientException {
        params = params != null ? params : new ObjectMap();
        params.putIfNotNull("field", field);
        return execute("analysis", null, "search", null, "distinct", params, GET, ObjectMap.class);
    }

    /**
     * Interpretation distinct method.
     * @param field Field for which to obtain the distinct values.
     * @param params Map containing any of the following optional parameters.
     *       study: Study [[user@]project:]study where study and project can be either the ID or UUID.
     *       id: Interpretation ID.
     *       clinicalAnalysisId: Clinical Analysis ID.
     *       analyst: Clinical analyst ID.
     *       methods: Interpretation method name.
     *       primaryFindings: Primary finding IDs.
     *       secondaryFindings: Secondary finding IDs.
     *       status: Interpretation status.
     *       creationDate: Creation date.
     *       modificationDate: Modification date.
     * @return a RestResponse object.
     * @throws ClientException ClientException if there is any server error.
     */
    public RestResponse<ObjectMap> distinctInterpretation(String field, ObjectMap params) throws ClientException {
        params = params != null ? params : new ObjectMap();
        params.putIfNotNull("field", field);
        return execute("analysis", null, "search/interpretation", null, "distinct", params, GET, ObjectMap.class);
    }

    /**
     * Search search interpretations.
     * @param params Map containing any of the following optional parameters.
     *       include: Fields included in the response, whole JSON path must be provided.
     *       exclude: Fields excluded in the response, whole JSON path must be provided.
     *       limit: Number of results to be returned.
     *       skip: Number of results to skip.
     *       sort: Sort the results.
     *       study: Study [[user@]project:]study where study and project can be either the ID or UUID.
     *       id: Interpretation ID.
     *       clinicalAnalysisId: Clinical Analysis ID.
     *       analyst: Clinical analyst ID.
     *       methods: Interpretation method name.
     *       primaryFindings: Primary finding IDs.
     *       secondaryFindings: Secondary finding IDs.
     *       status: Interpretation status.
     *       creationDate: Creation date.
     *       modificationDate: Modification date.
     * @return a RestResponse object.
     * @throws ClientException ClientException if there is any server error.
     */
    public RestResponse<Interpretation> searchInterpretation(ObjectMap params) throws ClientException {
        params = params != null ? params : new ObjectMap();
        return execute("analysis", null, "search/interpretation", null, "search", params, GET, Interpretation.class);
    }

    /**
     * Clinical interpretation information.
     * @param interpretations Comma separated list of search interpretation IDs  up to a maximum of 100.
     * @param params Map containing any of the following optional parameters.
     *       include: Fields included in the response, whole JSON path must be provided.
     *       exclude: Fields excluded in the response, whole JSON path must be provided.
     *       interpretations: Comma separated list of search interpretation IDs  up to a maximum of 100.
     *       study: Study [[user@]project:]study where study and project can be either the ID or UUID.
     *       version: Comma separated list of interpretation versions. 'all' to get all the interpretation versions. Not supported if
     *            multiple interpretation ids are provided.
     *       deleted: Boolean to retrieve deleted entries.
     * @return a RestResponse object.
     * @throws ClientException ClientException if there is any server error.
     */
    public RestResponse<Interpretation> infoInterpretation(String interpretations, ObjectMap params) throws ClientException {
        params = params != null ? params : new ObjectMap();
        return execute("analysis", null, "search/interpretation", interpretations, "info", params, GET, Interpretation.class);
    }

    /**
     * Run cancer tiering interpretation analysis.
     * @param data Cancer tiering interpretation analysis params.
     * @param params Map containing any of the following optional parameters.
     *       study: Study [[user@]project:]study where study and project can be either the ID or UUID.
     *       jobId: Job ID. It must be a unique string within the study. An ID will be autogenerated automatically if not provided.
     *       jobDescription: Job description.
     *       jobDependsOn: Comma separated list of existing job IDs the job will depend on.
     *       jobTags: Job tags.
     * @return a RestResponse object.
     * @throws ClientException ClientException if there is any server error.
     */
    public RestResponse<Job> runInterpreterCancerTiering(CancerTieringInterpretationAnalysisParams data, ObjectMap params)
            throws ClientException {
        params = params != null ? params : new ObjectMap();
        params.put("body", data);
        return execute("analysis", null, "search/interpreter/cancerTiering", null, "run", params, POST, Job.class);
    }

    /**
     * Run TEAM interpretation analysis.
     * @param data TEAM interpretation analysis params.
     * @param params Map containing any of the following optional parameters.
     *       study: Study [[user@]project:]study where study and project can be either the ID or UUID.
     *       jobId: Job ID. It must be a unique string within the study. An ID will be autogenerated automatically if not provided.
     *       jobDescription: Job description.
     *       jobDependsOn: Comma separated list of existing job IDs the job will depend on.
     *       jobTags: Job tags.
     * @return a RestResponse object.
     * @throws ClientException ClientException if there is any server error.
     */
    public RestResponse<Job> runInterpreterTeam(TeamInterpretationAnalysisParams data, ObjectMap params) throws ClientException {
        params = params != null ? params : new ObjectMap();
        params.put("body", data);
        return execute("analysis", null, "search/interpreter/team", null, "run", params, POST, Job.class);
    }

    /**
     * Run tiering interpretation analysis.
     * @param data Tiering interpretation analysis params.
     * @param params Map containing any of the following optional parameters.
     *       study: Study [[user@]project:]study where study and project can be either the ID or UUID.
     *       jobId: Job ID. It must be a unique string within the study. An ID will be autogenerated automatically if not provided.
     *       jobDescription: Job description.
     *       jobDependsOn: Comma separated list of existing job IDs the job will depend on.
     *       jobTags: Job tags.
     * @return a RestResponse object.
     * @throws ClientException ClientException if there is any server error.
     */
    public RestResponse<Job> runInterpreterTiering(TieringInterpretationAnalysisParams data, ObjectMap params) throws ClientException {
        params = params != null ? params : new ObjectMap();
        params.put("body", data);
        return execute("analysis", null, "search/interpreter/tiering", null, "run", params, POST, Job.class);
    }

    /**
     * Run Zetta interpretation analysis.
     * @param data Zetta interpretation analysis params.
     * @param params Map containing any of the following optional parameters.
     *       study: Study [[user@]project:]study where study and project can be either the ID or UUID.
     *       jobId: Job ID. It must be a unique string within the study. An ID will be autogenerated automatically if not provided.
     *       jobDescription: Job description.
     *       jobDependsOn: Comma separated list of existing job IDs the job will depend on.
     *       jobTags: Job tags.
     * @return a RestResponse object.
     * @throws ClientException ClientException if there is any server error.
     */
    public RestResponse<Job> runInterpreterZetta(ZettaInterpretationAnalysisParams data, ObjectMap params) throws ClientException {
        params = params != null ? params : new ObjectMap();
        params.put("body", data);
        return execute("analysis", null, "search/interpreter/zetta", null, "run", params, POST, Job.class);
    }

    /**
     * Clinical analysis search.
     * @param params Map containing any of the following optional parameters.
     *       include: Fields included in the response, whole JSON path must be provided.
     *       exclude: Fields excluded in the response, whole JSON path must be provided.
     *       limit: Number of results to be returned.
     *       skip: Number of results to skip.
     *       count: Get the total number of results matching the query. Deactivated by default.
     *       study: Study [[user@]project:]study where study and project can be either the ID or UUID.
     *       id: Clinical analysis ID.
     *       type: Clinical analysis type.
     *       priority: Priority.
     *       creationDate: Creation date. Format: yyyyMMddHHmmss. Examples: >2018, 2017-2018, <201805.
     *       modificationDate: Modification date. Format: yyyyMMddHHmmss. Examples: >2018, 2017-2018, <201805.
     *       internalStatus: Filter by internal status.
     *       status: Filter by status.
     *       dueDate: Due date (Format: yyyyMMddHHmmss. Examples: >2018, 2017-2018, <201805...).
     *       description: Description.
     *       family: Family id.
     *       proband: Proband id.
     *       sample: Sample id associated to the proband or any member of a family.
     *       individual: Proband id or any member id of a family.
     *       analystAssignee: Clinical analyst assignee.
     *       disorder: Disorder ID or name.
     *       flags: Flags.
     *       deleted: Boolean to retrieve deleted entries.
     *       release: Release value.
     *       attributes: Text attributes (Format: sex=male,age>20 ...).
     * @return a RestResponse object.
     * @throws ClientException ClientException if there is any server error.
     */
    public RestResponse<ClinicalAnalysis> search(ObjectMap params) throws ClientException {
        params = params != null ? params : new ObjectMap();
        return execute("analysis", null, "search", null, "search", params, GET, ClinicalAnalysis.class);
    }

    /**
     * Fetch actionable search variants.
     * @param params Map containing any of the following optional parameters.
     *       study: Study [[user@]project:]study where study and project can be either the ID or UUID.
     *       sample: Sample ID.
     * @return a RestResponse object.
     * @throws ClientException ClientException if there is any server error.
     */
    public RestResponse<ClinicalVariant> actionableVariant(ObjectMap params) throws ClientException {
        params = params != null ? params : new ObjectMap();
        return execute("analysis", null, "search/variant", null, "actionable", params, GET, ClinicalVariant.class);
    }

    /**
     * Fetch search variants.
     * @param params Map containing any of the following optional parameters.
     *       include: Fields included in the response, whole JSON path must be provided.
     *       exclude: Fields excluded in the response, whole JSON path must be provided.
     *       limit: Number of results to be returned.
     *       skip: Number of results to skip.
     *       count: Get the total number of results matching the query. Deactivated by default.
     *       approximateCount: Get an approximate count, instead of an exact total count. Reduces execution time.
     *       approximateCountSamplingSize: Sampling size to get the approximate count. Larger values increase accuracy but also increase
     *            execution time.
     *       savedFilter: Use a saved filter at User level.
     *       id: List of IDs, these can be rs IDs (dbSNP) or variants in the format chrom:start:ref:alt, e.g. rs116600158,19:7177679:C:T.
     *       region: List of regions, these can be just a single chromosome name or regions in the format chr:start-end, e.g.:
     *            2,3:100000-200000.
     *       type: List of types, accepted values are SNV, MNV, INDEL, SV, CNV, INSERTION, DELETION, e.g. SNV,INDEL.
     *       study: Filter variants from the given studies, these can be either the numeric ID or the alias with the format
     *            user@project:study.
     *       file: Filter variants from the files specified. This will set includeFile parameter when not provided.
     *       filter: Specify the FILTER for any of the files. If 'file' filter is provided, will match the file and the filter. e.g.:
     *            PASS,LowGQX.
     *       qual: Specify the QUAL for any of the files. If 'file' filter is provided, will match the file and the qual. e.g.: >123.4.
     *       fileData: Filter by file data (i.e. FILTER, QUAL and INFO columns from VCF file). [{file}:]{key}{op}{value}[,;]* . If no file
     *            is specified, will use all files from "file" filter. e.g. AN>200 or file_1.vcf:AN>200;file_2.vcf:AN<10 . Many fields can
     *            be combined. e.g. file_1.vcf:AN>200;DB=true;file_2.vcf:AN<10,FILTER=PASS,LowDP.
     *       sample: Filter variants by sample genotype. This will automatically set 'includeSample' parameter when not provided. This
     *            filter accepts multiple 3 forms: 1) List of samples: Samples that contain the main variant. Accepts AND (;) and OR (,)
     *            operators.  e.g. HG0097,HG0098 . 2) List of samples with genotypes: {sample}:{gt1},{gt2}. Accepts AND (;) and OR (,)
     *            operators.  e.g. HG0097:0/0;HG0098:0/1,1/1 . Unphased genotypes (e.g. 0/1, 1/1) will also include phased genotypes (e.g.
     *            0|1, 1|0, 1|1), but not vice versa. When filtering by multi-allelic genotypes, any secondary allele will match,
     *            regardless of its position e.g. 1/2 will match with genotypes 1/2, 1/3, 1/4, .... Genotype aliases accepted: HOM_REF,
     *            HOM_ALT, HET, HET_REF, HET_ALT and MISS  e.g. HG0097:HOM_REF;HG0098:HET_REF,HOM_ALT . 3) Sample with segregation mode:
     *            {sample}:{segregation}. Only one sample accepted.Accepted segregation modes: [ autosomalDominant, autosomalRecessive,
     *            XLinkedDominant, XLinkedRecessive, YLinked, mitochondrial, deNovo, mendelianError, compoundHeterozygous ]. Value is case
     *            insensitive. e.g. HG0097:DeNovo Sample must have parents defined and indexed. .
     *       sampleData: Filter by any SampleData field from samples. [{sample}:]{key}{op}{value}[,;]* . If no sample is specified, will
     *            use all samples from "sample" or "genotype" filter. e.g. DP>200 or HG0097:DP>200,HG0098:DP<10 . Many FORMAT fields can be
     *            combined. e.g. HG0097:DP>200;GT=1/1,0/1,HG0098:DP<10.
     *       sampleAnnotation: Selects some samples using metadata information from Catalog. e.g.
     *            age>20;phenotype=hpo:123,hpo:456;name=smith.
     *       cohort: Select variants with calculated stats for the selected cohorts.
     *       cohortStatsRef: Reference Allele Frequency: [{study:}]{cohort}[<|>|<=|>=]{number}. e.g. ALL<=0.4.
     *       cohortStatsAlt: Alternate Allele Frequency: [{study:}]{cohort}[<|>|<=|>=]{number}. e.g. ALL<=0.4.
     *       cohortStatsMaf: Minor Allele Frequency: [{study:}]{cohort}[<|>|<=|>=]{number}. e.g. ALL<=0.4.
     *       cohortStatsMgf: Minor Genotype Frequency: [{study:}]{cohort}[<|>|<=|>=]{number}. e.g. ALL<=0.4.
     *       cohortStatsPass: Filter PASS frequency: [{study:}]{cohort}[<|>|<=|>=]{number}. e.g. ALL>0.8.
     *       missingAlleles: Number of missing alleles: [{study:}]{cohort}[<|>|<=|>=]{number}.
     *       missingGenotypes: Number of missing genotypes: [{study:}]{cohort}[<|>|<=|>=]{number}.
     *       score: Filter by variant score: [{study:}]{score}[<|>|<=|>=]{number}.
     *       family: Filter variants where any of the samples from the given family contains the variant (HET or HOM_ALT).
     *       familyDisorder: Specify the disorder to use for the family segregation.
     *       familySegregation: Filter by segregation mode from a given family. Accepted values: [ autosomalDominant, autosomalRecessive,
     *            XLinkedDominant, XLinkedRecessive, YLinked, mitochondrial, deNovo, mendelianError, compoundHeterozygous ].
     *       familyMembers: Sub set of the members of a given family.
     *       familyProband: Specify the proband child to use for the family segregation.
     *       gene: List of genes, most gene IDs are accepted (HGNC, Ensembl gene, ...). This is an alias to 'xref' parameter.
     *       ct: List of SO consequence types, e.g. missense_variant,stop_lost or SO:0001583,SO:0001578.
     *       xref: List of any external reference, these can be genes, proteins or variants. Accepted IDs include HGNC, Ensembl genes,
     *            dbSNP, ClinVar, HPO, Cosmic, ...
     *       biotype: List of biotypes, e.g. protein_coding.
     *       proteinSubstitution: Protein substitution scores include SIFT and PolyPhen. You can query using the score
     *            {protein_score}[<|>|<=|>=]{number} or the description {protein_score}[~=|=]{description} e.g. polyphen>0.1,sift=tolerant.
     *       conservation: Filter by conservation score: {conservation_score}[<|>|<=|>=]{number} e.g. phastCons>0.5,phylop<0.1,gerp>0.1.
     *       populationFrequencyAlt: Alternate Population Frequency: {study}:{population}[<|>|<=|>=]{number}. e.g. 1kG_phase3:ALL<0.01.
     *       populationFrequencyRef: Reference Population Frequency: {study}:{population}[<|>|<=|>=]{number}. e.g. 1kG_phase3:ALL<0.01.
     *       populationFrequencyMaf: Population minor allele frequency: {study}:{population}[<|>|<=|>=]{number}. e.g. 1kG_phase3:ALL<0.01.
     *       transcriptFlag: List of transcript annotation flags. e.g. CCDS, basic, cds_end_NF, mRNA_end_NF, cds_start_NF, mRNA_start_NF,
     *            seleno.
     *       geneTraitId: List of gene trait association id. e.g. "umls:C0007222" , "OMIM:269600".
     *       go: List of GO (Gene Ontology) terms. e.g. "GO:0002020".
     *       expression: List of tissues of interest. e.g. "lung".
     *       proteinKeyword: List of Uniprot protein variant annotation keywords.
     *       drug: List of drug names.
     *       functionalScore: Functional score: {functional_score}[<|>|<=|>=]{number} e.g. cadd_scaled>5.2 , cadd_raw<=0.3.
     *       clinicalSignificance: Clinical significance: benign, likely_benign, likely_pathogenic, pathogenic.
     *       customAnnotation: Custom annotation: {key}[<|>|<=|>=]{number} or {key}[~=|=]{text}.
     *       panel: Filter by genes from the given disease panel.
     *       trait: List of traits, based on ClinVar, HPO, COSMIC, i.e.: IDs, histologies, descriptions,...
     * @return a RestResponse object.
     * @throws ClientException ClientException if there is any server error.
     */
    public RestResponse<ClinicalVariant> queryVariant(ObjectMap params) throws ClientException {
        params = params != null ? params : new ObjectMap();
        return execute("analysis", null, "search/variant", null, "query", params, GET, ClinicalVariant.class);
    }

    /**
     * Returns the acl of the search analyses. If member is provided, it will only return the acl for the member.
     * @param clinicalAnalyses Comma separated list of search analysis IDs or names up to a maximum of 100.
     * @param params Map containing any of the following optional parameters.
     *       study: Study [[user@]project:]study where study and project can be either the ID or UUID.
     *       member: User or group ID.
     *       silent: Boolean to retrieve all possible entries that are queried for, false to raise an exception whenever one of the entries
     *            looked for cannot be shown for whichever reason.
     * @return a RestResponse object.
     * @throws ClientException ClientException if there is any server error.
     */
    public RestResponse<ObjectMap> acl(String clinicalAnalyses, ObjectMap params) throws ClientException {
        params = params != null ? params : new ObjectMap();
        return execute("analysis", null, "search", clinicalAnalyses, "acl", params, GET, ObjectMap.class);
    }

    /**
     * Delete search analyses.
     * @param clinicalAnalyses Comma separated list of search analysis IDs or names up to a maximum of 100.
     * @param params Map containing any of the following optional parameters.
     *       study: Study [[user@]project:]study where study and project can be either the ID or UUID.
     *       force: Force deletion if the ClinicalAnalysis contains interpretations or is locked.
     *       clinicalAnalyses: Comma separated list of search analysis IDs or names up to a maximum of 100.
     * @return a RestResponse object.
     * @throws ClientException ClientException if there is any server error.
     */
    public RestResponse<ClinicalAnalysis> delete(String clinicalAnalyses, ObjectMap params) throws ClientException {
        params = params != null ? params : new ObjectMap();
        return execute("analysis", null, "search", clinicalAnalyses, "delete", params, DELETE, ClinicalAnalysis.class);
    }

    /**
     * Update search analysis attributes.
     * @param clinicalAnalyses Comma separated list of search analysis IDs.
     * @param data JSON containing search analysis information.
     * @param params Map containing any of the following optional parameters.
     *       clinicalAnalyses: Comma separated list of search analysis IDs.
     *       study: Study [[user@]project:]study where study and project can be either the ID or UUID.
     *       commentsAction: Action to be performed if the array of comments is being updated.
     *       flagsAction: Action to be performed if the array of flags is being updated.
     *       filesAction: Action to be performed if the array of files is being updated.
     * @return a RestResponse object.
     * @throws ClientException ClientException if there is any server error.
     */
    public RestResponse<ClinicalAnalysis> update(String clinicalAnalyses, ClinicalAnalysisUpdateParams data, ObjectMap params)
            throws ClientException {
        params = params != null ? params : new ObjectMap();
        params.put("body", data);
        return execute("analysis", null, "search", clinicalAnalyses, "update", params, POST, ClinicalAnalysis.class);
    }

    /**
     * Clinical analysis info.
     * @param clinicalAnalysis Comma separated list of search analysis IDs or names up to a maximum of 100.
     * @param params Map containing any of the following optional parameters.
     *       include: Fields included in the response, whole JSON path must be provided.
     *       exclude: Fields excluded in the response, whole JSON path must be provided.
     *       clinicalAnalysis: Comma separated list of search analysis IDs or names up to a maximum of 100.
     *       study: Study [[user@]project:]study where study and project can be either the ID or UUID.
     *       deleted: Boolean to retrieve deleted entries.
     * @return a RestResponse object.
     * @throws ClientException ClientException if there is any server error.
     */
    public RestResponse<ClinicalAnalysis> info(String clinicalAnalysis, ObjectMap params) throws ClientException {
        params = params != null ? params : new ObjectMap();
        return execute("analysis", null, "search", clinicalAnalysis, "info", params, GET, ClinicalAnalysis.class);
    }

    /**
     * Create a new Interpretation.
     * @param clinicalAnalysis Clinical analysis ID.
     * @param data JSON containing search interpretation information.
     * @param params Map containing any of the following optional parameters.
     *       clinicalAnalysis: Clinical analysis ID.
     *       study: [[user@]project:]study id.
     *       setAs: Set interpretation as.
     * @return a RestResponse object.
     * @throws ClientException ClientException if there is any server error.
     */
    public RestResponse<Interpretation> createInterpretation(String clinicalAnalysis, InterpretationCreateParams data, ObjectMap params)
            throws ClientException {
        params = params != null ? params : new ObjectMap();
        params.put("body", data);
        return execute("analysis/search", clinicalAnalysis, "interpretation", null, "create", params, POST, Interpretation.class);
    }

    /**
     * Clear the fields of the main interpretation of the Clinical Analysis.
     * @param clinicalAnalysis Clinical analysis ID.
     * @param interpretations Interpretation IDs of the Clinical Analysis.
     * @param params Map containing any of the following optional parameters.
     *       study: [[user@]project:]study ID.
     *       interpretations: Interpretation IDs of the Clinical Analysis.
     *       clinicalAnalysis: Clinical analysis ID.
     * @return a RestResponse object.
     * @throws ClientException ClientException if there is any server error.
     */
    public RestResponse<Interpretation> clearInterpretation(String clinicalAnalysis, String interpretations, ObjectMap params)
            throws ClientException {
        params = params != null ? params : new ObjectMap();
        return execute("analysis/search", clinicalAnalysis, "interpretation", interpretations, "clear", params, POST,
                Interpretation.class);
    }

    /**
     * Delete interpretation.
     * @param clinicalAnalysis Clinical analysis ID.
     * @param interpretations Interpretation IDs of the Clinical Analysis.
     * @param params Map containing any of the following optional parameters.
     *       study: [[user@]project:]study ID.
     *       clinicalAnalysis: Clinical analysis ID.
     *       interpretations: Interpretation IDs of the Clinical Analysis.
     *       setAsPrimary: Interpretation id to set as primary from the list of secondaries in case of deleting the actual primary one.
     * @return a RestResponse object.
     * @throws ClientException ClientException if there is any server error.
     */
    public RestResponse<Interpretation> deleteInterpretation(String clinicalAnalysis, String interpretations, ObjectMap params)
            throws ClientException {
        params = params != null ? params : new ObjectMap();
        return execute("analysis/search", clinicalAnalysis, "interpretation", interpretations, "delete", params, DELETE,
                Interpretation.class);
    }

    /**
     * Merge interpretation.
     * @param clinicalAnalysis Clinical analysis ID.
     * @param interpretation Interpretation ID where it will be merged.
     * @param data JSON containing search interpretation to merge from.
     * @param params Map containing any of the following optional parameters.
     *       study: [[user@]project:]study ID.
     *       clinicalAnalysis: Clinical analysis ID.
     *       interpretation: Interpretation ID where it will be merged.
     *       secondaryInterpretationId: Secondary Interpretation ID to merge from.
     *       findings: Comma separated list of findings to merge. If not provided, all findings will be merged.
     * @return a RestResponse object.
     * @throws ClientException ClientException if there is any server error.
     */
    public RestResponse<Interpretation> mergeInterpretation(String clinicalAnalysis, String interpretation, InterpretationMergeParams data,
        ObjectMap params) throws ClientException {
        params = params != null ? params : new ObjectMap();
        params.put("body", data);
        return execute("analysis/search", clinicalAnalysis, "interpretation", interpretation, "merge", params, POST,
                Interpretation.class);
    }

    /**
     * Revert to a previous interpretation version.
     * @param clinicalAnalysis Clinical analysis ID.
     * @param interpretation Interpretation ID.
     * @param version Version to revert to.
     * @param params Map containing any of the following optional parameters.
     *       study: [[user@]project:]study ID.
     *       clinicalAnalysis: Clinical analysis ID.
     *       interpretation: Interpretation ID.
     * @return a RestResponse object.
     * @throws ClientException ClientException if there is any server error.
     */
    public RestResponse<Interpretation> revertInterpretation(String clinicalAnalysis, String interpretation, int version, ObjectMap params)
            throws ClientException {
        params = params != null ? params : new ObjectMap();
        params.putIfNotNull("version", version);
        return execute("analysis/search", clinicalAnalysis, "interpretation", interpretation, "revert", params, POST,
                Interpretation.class);
    }

    /**
     * Update interpretation fields.
     * @param clinicalAnalysis Clinical analysis ID.
     * @param interpretation Interpretation ID.
     * @param data JSON containing search interpretation information.
     * @param params Map containing any of the following optional parameters.
     *       study: [[user@]project:]study ID.
     *       primaryFindingsAction: Action to be performed if the array of primary findings is being updated.
     *       methodsAction: Action to be performed if the array of methods is being updated.
     *       secondaryFindingsAction: Action to be performed if the array of secondary findings is being updated.
     *       commentsAction: Action to be performed if the array of comments is being updated. To REMOVE or REPLACE, the date will need to
     *            be provided to identify the comment.
     *       setAs: Set interpretation as.
     *       clinicalAnalysis: Clinical analysis ID.
     *       interpretation: Interpretation ID.
     * @return a RestResponse object.
     * @throws ClientException ClientException if there is any server error.
     */
    public RestResponse<Interpretation> updateInterpretation(String clinicalAnalysis, String interpretation, InterpretationUpdateParams
        data, ObjectMap params) throws ClientException {
        params = params != null ? params : new ObjectMap();
        params.put("body", data);
        return execute("analysis/search", clinicalAnalysis, "interpretation", interpretation, "update", params, POST,
                Interpretation.class);
    }
}
