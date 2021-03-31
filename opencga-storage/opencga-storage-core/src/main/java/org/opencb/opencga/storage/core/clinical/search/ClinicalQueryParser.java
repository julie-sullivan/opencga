package org.opencb.opencga.storage.core.clinical.search;

import org.apache.commons.lang3.StringUtils;
import org.apache.solr.client.solrj.SolrQuery;
import org.opencb.commons.datastore.core.Query;
import org.opencb.commons.datastore.core.QueryOptions;
import org.opencb.commons.datastore.core.QueryParam;
import org.opencb.commons.datastore.mongodb.MongoDBQueryUtils;
import org.opencb.commons.utils.ListUtils;
import org.opencb.opencga.storage.core.clinical.ClinicalVariantQueryParam;
import org.opencb.opencga.storage.core.metadata.VariantStorageMetadataManager;
import org.opencb.opencga.storage.core.variant.search.solr.SolrQueryParser;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ClinicalQueryParser {

    private SolrQueryParser solrQueryParser;

    private static final Pattern OPERATION_DATE_PATTERN = Pattern.compile("^(<=?|>=?|!=|!?=?~|=?=?)([0-9]+)(-?)([0-9]*)");

    public ClinicalQueryParser(VariantStorageMetadataManager variantStorageMetadataManager) {
        solrQueryParser = new SolrQueryParser(variantStorageMetadataManager);
    }

    public SolrQuery parse(Query query, QueryOptions queryOptions) {
        // First, call SolrQueryParser.parse
        SolrQuery solrQuery = solrQueryParser.parse(query, queryOptions);

        String key;

        // ---------- ClinicalAnalysis ----------
        //
        // ID, description, disorder, files, proband ID, family ID, family phenotype name, family member ID

        // ClinicalAnalysis ID
        key = ClinicalVariantQueryParam.CA_ID.key();
        if (StringUtils.isNotEmpty(key)) {
            solrQuery.addField(solrQueryParser.parseCategoryTermValue(key, query.getString(key)));
        }

        // ClinicalAnalysis description
        key = ClinicalVariantQueryParam.CA_DESCRIPTION.key();
        if (StringUtils.isNotEmpty(query.getString(key))) {
            solrQuery.addField(parseInfo(ClinicalVariantUtils.DESCRIPTION_PREFIX, query.getString(key),
                    ClinicalVariantQueryParam.CA_INFO.key()));
        }

        // ClinicalAnalysis disorder
        key = ClinicalVariantQueryParam.CA_DISORDER.key();
        if (StringUtils.isNotEmpty(query.getString(key))) {
            solrQuery.addField(solrQueryParser.parseCategoryTermValue(key, query.getString(key)));
        }

        // ClinicalAnalysis files
        key = ClinicalVariantQueryParam.CA_FILE.key();
        if (StringUtils.isNotEmpty(query.getString(key))) {
            solrQuery.addField(solrQueryParser.parseCategoryTermValue(key, query.getString(key)));
        }

        // ClinicalAnalysis proband ID
        key = ClinicalVariantQueryParam.CA_PROBAND_ID.key();
        if (StringUtils.isNotEmpty(query.getString(key))) {
            solrQuery.addField(solrQueryParser.parseCategoryTermValue(key, query.getString(key)));
        }

        // ClinicalAnalysis proband disorders
        key = ClinicalVariantQueryParam.CA_PROBAND_DISORDERS.key();
        if (StringUtils.isNotEmpty(query.getString(key))) {
            solrQuery.addField(solrQueryParser.parseCategoryTermValue(key, query.getString(key)));
        }

        // ClinicalAnalysis proband phenotypes
        key = ClinicalVariantQueryParam.CA_PROBAND_PHENOTYPES.key();
        if (StringUtils.isNotEmpty(query.getString(key))) {
            solrQuery.addField(solrQueryParser.parseCategoryTermValue(key, query.getString(key)));
        }

        // ClinicalAnalysis family ID
        key = ClinicalVariantQueryParam.CA_FAMILY_ID.key();
        if (StringUtils.isNotEmpty(query.getString(key))) {
            solrQuery.addField(solrQueryParser.parseCategoryTermValue(key, query.getString(key)));
        }

        // ClinicalAnalysis family member IDs
        key = ClinicalVariantQueryParam.CA_FAMILY_MEMBER_IDS.key();
        if (StringUtils.isNotEmpty(query.getString(key))) {
            solrQuery.addField(solrQueryParser.parseCategoryTermValue(key, query.getString(key)));
        }

        key = ClinicalVariantQueryParam.CA_COMMENTS.key();
        if (StringUtils.isNotEmpty(query.getString(key))) {
            solrQuery.addField(parseInfo(ClinicalVariantUtils.COMMENT_PREFIX, query.getString(key),
                    ClinicalVariantQueryParam.CA_INFO.key()));
        }

        // ---------- Interpretation ----------
        //
        //    ID, software name, software version, analyst name, panel name, creation date, more info

        // Interpretation ID
        key = ClinicalVariantQueryParam.INT_ID.key();
        if (StringUtils.isNotEmpty(query.getString(key))) {
            solrQuery.addField(solrQueryParser.parseCategoryTermValue(key, query.getString(key)));
        }

        // TODO: remove
        // Interpretation software name
        key = ClinicalVariantQueryParam.INT_SOFTWARE_NAME.key();
        if (StringUtils.isNotEmpty(query.getString(key))) {
            solrQuery.addField(solrQueryParser.parseCategoryTermValue(key, query.getString(key)));
        }

        // TODO: remove
        // Interpretation software version
        key = ClinicalVariantQueryParam.INT_SOFTWARE_VERSION.key();
        if (StringUtils.isNotEmpty(query.getString(key))) {
            solrQuery.addField(solrQueryParser.parseCategoryTermValue(key, query.getString(key)));
        }

        // Interpretation analysit name
        key = ClinicalVariantQueryParam.INT_ANALYST_NAME.key();
        if (StringUtils.isNotEmpty(query.getString(key))) {
            solrQuery.addField(solrQueryParser.parseCategoryTermValue(key, query.getString(key)));
        }

        // TODO: inside InterpretationMethod (disease panels)
        // Interpretation panel names
        key = ClinicalVariantQueryParam.INT_PANELS.key();
        if (StringUtils.isNotEmpty(query.getString(key))) {
            solrQuery.addField(solrQueryParser.parseCategoryTermValue(key, query.getString(key)));
        }

        // Interpretation info: description, dependency, filters, comments
        key = ClinicalVariantQueryParam.INT_DESCRIPTION.key();
        if (StringUtils.isNotEmpty(query.getString(key))) {
            solrQuery.addField(parseInfo(ClinicalVariantUtils.DESCRIPTION_PREFIX, query.getString(key),
                    ClinicalVariantQueryParam.INT_INFO.key()));
        }

        // TODO: inside InterpretationMethod (software)
        key = ClinicalVariantQueryParam.INT_DEPENDENCY.key();
        if (StringUtils.isNotEmpty(query.getString(key))) {
            solrQuery.addField(parseInfo(ClinicalVariantUtils.DEPENDENCY_PREFIX, query.getString(key),
                    ClinicalVariantQueryParam.INT_INFO.key()));
        }

        // TODO: inside InterpretationMethod (filters)
        key = ClinicalVariantQueryParam.INT_FILTERS.key();
        if (StringUtils.isNotEmpty(query.getString(key))) {
            solrQuery.addField(parseInfo(ClinicalVariantUtils.FILTER_PREFIX, query.getString(key),
                    ClinicalVariantQueryParam.INT_INFO.key()));
        }

        key = ClinicalVariantQueryParam.INT_COMMENTS.key();
        if (StringUtils.isNotEmpty(query.getString(key))) {
            solrQuery.addField(parseInfo(ClinicalVariantUtils.COMMENT_PREFIX, query.getString(key),
                    ClinicalVariantQueryParam.INT_INFO.key()));
        }

        // Interpretation creation date
        key = ClinicalVariantQueryParam.INT_CREATION_DATE.key();
        if (StringUtils.isNotEmpty(query.getString(key))) {
            solrQuery.addField(parseInterpretationCreationDate(query.getString(key)));
        }


        // ---------- Catalog ----------
        //
        //    project ID, assembly, study ID

        // Project
        key = ClinicalVariantQueryParam.PROJECT_ID.key();
        if (StringUtils.isNotEmpty(query.getString(key))) {
            solrQuery.addField(solrQueryParser.parseCategoryTermValue(key, query.getString(key)));
        }

        // Assembly
        key = ClinicalVariantQueryParam.ASSEMBLY.key();
        if (StringUtils.isNotEmpty(query.getString(key))) {
            solrQuery.addField(solrQueryParser.parseCategoryTermValue(key, query.getString(key)));
        }

        // Study
        key = ClinicalVariantQueryParam.STUDY_ID.key();
        if (StringUtils.isNotEmpty(query.getString(key))) {
            solrQuery.addField(solrQueryParser.parseCategoryTermValue(key, query.getString(key)));
        }

        // ---------- ClinicalVariant ----------
        //

        // TODO: remove
        //   deNovo quality score, comments
        key = ClinicalVariantQueryParam.CV_DE_NOVO_QUALITY_SCORE.key();
        if (StringUtils.isNotEmpty(query.getString(key))) {
            solrQuery.addField(solrQueryParser.parseNumericValue(key, query.getString(key)));
        }

        key = ClinicalVariantQueryParam.CV_COMMENTS.key();
        if (StringUtils.isNotEmpty(query.getString(key))) {
            solrQuery.addField(solrQueryParser.parseCategoryTermValue(key, query.getString(key)));
        }


        // ---------- ClinicalVariantEvidence ----------
        //
        parseClinicalVariantEvidence(query, solrQuery);

        return solrQuery;
    }

    private String parseInterpretationCreationDate(String creationDateStr) {
        StringBuilder filter = new StringBuilder();

        String condition;
        boolean firstCondition = true;

        String logicalComparator = " OR ";
        MongoDBQueryUtils.ComparisonOperator comparator;

        String[] creationDates = creationDateStr.split("[,;]");
        for (String creationDate: creationDates) {
            Matcher matcher = OPERATION_DATE_PATTERN.matcher(creationDate);
            String op = "";
            String queryValueString = creationDate;
            if (matcher.find()) {
                op = matcher.group(1);
                queryValueString = matcher.group(2);
            }
            comparator = MongoDBQueryUtils.getComparisonOperator(op, QueryParam.Type.DATE);
            List<String> dateList = new ArrayList<>();
            dateList.add(queryValueString);
            if (!matcher.group(3).isEmpty()) {
                dateList.add(matcher.group(4));
                comparator = MongoDBQueryUtils.ComparisonOperator.BETWEEN;
            }
            // dateList is a list of 1 or 2 strings (dates). Only one will be expected when something like the following is passed:
            // =20171210, 20171210, >=20171210, >20171210, <20171210, <=20171210
            // When 2 strings are passed, we will expect it to be a range such as: 20171201-20171210
            Date date = convertStringToDate(dateList.get(0));

            condition = null;
            switch (comparator) {
                case BETWEEN:
                    if (dateList.size() == 2) {
                        Date to = convertStringToDate(dateList.get(1));
                        condition = ClinicalVariantQueryParam.INT_CREATION_DATE.key() + ":{" + date.getTime() + " TO " + to.getTime() + "}";
                    }
                    break;
                case EQUALS:
                    condition = ClinicalVariantQueryParam.INT_CREATION_DATE.key() + ":" + date.getTime();
                    break;
                case GREATER_THAN:
                    condition = ClinicalVariantQueryParam.INT_CREATION_DATE.key() + ":{" + date.getTime() + " TO *]";
                    break;
                case GREATER_THAN_EQUAL:
                    condition = ClinicalVariantQueryParam.INT_CREATION_DATE.key() + ":[" + date.getTime() + " TO *]";
                    break;
                case LESS_THAN:
                    condition = ClinicalVariantQueryParam.INT_CREATION_DATE.key() + ":[* TO " + date.getTime() + "}";
                    break;
                case LESS_THAN_EQUAL:
                    condition = ClinicalVariantQueryParam.INT_CREATION_DATE.key() + ":[* TO " + date.getTime() + "]";
                    break;
                default:
                    break;
            }
            if (condition != null) {
                if (!firstCondition) {
                    filter.append(logicalComparator);
                }
                firstCondition = false;
                filter.append("(").append(condition).append(")");
            }
        }

        return filter.toString();
    }

    private Date convertStringToDate(String stringDate) {
        if (stringDate.length() == 4) {
            stringDate = stringDate + "0101";
        } else if (stringDate.length() == 6) {
            stringDate = stringDate + "01";
        }
        String myDate = String.format("%-14s", stringDate).replace(" ", "0");
        LocalDateTime localDateTime = LocalDateTime.parse(myDate, DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        // We convert it to date because it is the type used by mongo
        return Date.from(localDateTime.atZone(ZoneId.systemDefault()).toInstant());
    }


    private String parseInfo(String prefix, String value, String solrFieldName) {
        if (prefix.equals(ClinicalVariantUtils.DEPENDENCY_PREFIX)) {
            return parseInterpretationDependency(value);
        }

        String val = value.replace("\"", "");
        String wildcard = "*";
        String logicalComparator = " OR ";
        String[] values = val.split("[,;]");

        StringBuilder filter = new StringBuilder();
        for (int i = 0; i < values.length; ++i) {
            if (filter.length() > 0) {
                filter.append(logicalComparator);
            }
            filter.append(solrFieldName).append(":\"").append(prefix).append(wildcard).append(values[i]).append(wildcard).append("\"");
        }

        return filter.toString();
    }

    private String parseInterpretationDependency(String dependency) {
        String wildcard = "*";
        String logicalComparator = " OR ";
        String[] values = dependency.split("[,;]");
        StringBuilder filter = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            String[] dependencySplit = values[i].split(":");
            if (filter.length() > 0) {
                filter.append(logicalComparator);
            }
            if (dependencySplit.length == 1) {
                filter.append(ClinicalVariantQueryParam.INT_INFO.key()).append(":\"").append(ClinicalVariantUtils.DEPENDENCY_PREFIX)
                        .append(wildcard).append(dependencySplit[0]).append(wildcard).append("\"");
            } else if (dependencySplit.length == 2) {
                filter.append(ClinicalVariantQueryParam.INT_INFO.key()).append(":\"").append(ClinicalVariantUtils.DEPENDENCY_PREFIX)
                        .append(wildcard).append(dependencySplit[0]).append(ClinicalVariantUtils.FIELD_SEPARATOR)
                        .append(dependencySplit[1]).append(wildcard).append("\"");
            }
        }
        return filter.toString();
    }

    private void parseClinicalVariantEvidence(Query query, SolrQuery solrQuery) {
        List<List<String>> combinations = new ArrayList<>();

        String key = ClinicalVariantQueryParam.CVE_PHENOTYPE_NAMES.key();
        if (StringUtils.isNotEmpty(query.getString(key))) {
            updateCombinations(query.getAsStringList(key), combinations);
        }

        key = ClinicalVariantQueryParam.CVE_CONSEQUENCE_TYPE_IDS.key();
        if (StringUtils.isNotEmpty(query.getString(key))) {
            updateCombinations(query.getAsStringList(key), combinations);
        }

        // TODO: check
        Set<String> xrefs = new HashSet<>();
        key = ClinicalVariantQueryParam.CVE_GENE_NAMES.key();
        if (StringUtils.isNotEmpty(query.getString(key))) {
            xrefs.addAll(query.getAsStringList(key));
        }
        key = ClinicalVariantQueryParam.CVE_XREFS.key();
        if (StringUtils.isNotEmpty(query.getString(key))) {
            xrefs.addAll(query.getAsStringList(key));
        }
        if (xrefs.size() > 0) {
            updateCombinations(query.getAsStringList(key), combinations);
        }

        key = ClinicalVariantQueryParam.CVE_PANEL_IDS.key();
        if (StringUtils.isNotEmpty(query.getString(key))) {
            updateCombinations(query.getAsStringList(key), combinations);
        }

        key = ClinicalVariantQueryParam.CVE_TIER.key();
        if (StringUtils.isNotEmpty(query.getString(key))) {
            updateCombinations(query.getAsStringList(key), combinations);
        }

        key = ClinicalVariantQueryParam.CVE_ACMG.key();
        if (StringUtils.isNotEmpty(query.getString(key))) {
            updateCombinations(query.getAsStringList(key), combinations);
        }

        key = ClinicalVariantQueryParam.CVE_CLINICAL_SIGNIFICANCE.key();
        if (StringUtils.isNotEmpty(query.getString(key))) {
            updateCombinations(query.getAsStringList(key), combinations);
        }

        key = ClinicalVariantQueryParam.CVE_DRUG_RESPONSE.key();
        if (StringUtils.isNotEmpty(query.getString(key))) {
            updateCombinations(query.getAsStringList(key), combinations);
        }

        key = ClinicalVariantQueryParam.CVE_TRAIT_ASSOCIATION.key();
        if (StringUtils.isNotEmpty(query.getString(key))) {
            updateCombinations(query.getAsStringList(key), combinations);
        }

        key = ClinicalVariantQueryParam.CVE_FUNCTIONAL_EFFECT.key();
        if (StringUtils.isNotEmpty(query.getString(key))) {
            updateCombinations(query.getAsStringList(key), combinations);
        }

        key = ClinicalVariantQueryParam.CVE_TUMORIGENESIS.key();
        if (StringUtils.isNotEmpty(query.getString(key))) {
            updateCombinations(query.getAsStringList(key), combinations);
        }

        key = ClinicalVariantQueryParam.CVE_OTHER_CLASSIFICATION.key();
        if (StringUtils.isNotEmpty(query.getString(key))) {
            updateCombinations(query.getAsStringList(key), combinations);
        }

        key = ClinicalVariantQueryParam.CVE_ROLES_IN_CANCER.key();
        if (StringUtils.isNotEmpty(query.getString(key))) {
            updateCombinations(query.getAsStringList(key), combinations);
        }

        StringBuilder sb = new StringBuilder();


        key = ClinicalVariantQueryParam.CVE_JUSTIFICATION.key();
        List<String> justifications = query.getAsStringList(key);

        boolean firstOR;
        boolean firstAND = true;
        for (int k = 0; k < combinations.size(); k++) {
            firstOR = true;
            if (!firstAND) {
                sb.append(" AND ");
            }
            sb.append("(");
            for (int i = 0; i < combinations.get(k).size() - 1; i++) {
                for (int j = i + 1; j < combinations.get(k).size(); j++) {
                    key = combinations.get(k).get(i) + ClinicalVariantUtils.FIELD_SEPARATOR + combinations.get(k).get(j);
                    if (ListUtils.isEmpty(justifications)) {
                        if (!firstOR) {
                            sb.append(" OR ");
                        }
                        sb.append(ClinicalVariantQueryParam.CVE_AUX.key()).append(":\"").append(key).append("\"");
                        firstOR = false;
                    } else {
                        for (String justification: justifications) {
                            if (!firstOR) {
                                sb.append(" OR ");
                            }
                            sb.append(ClinicalVariantQueryParam.CVE_JUSTIFICATION.key()).append("_").append(key).append(":\"*")
                                    .append(justification).append("*\"");
                            firstOR = false;
                        }
                    }
                }
            }
            sb.append(")");
        }

        solrQuery.addField(sb.toString());
    }

    private void updateCombinations(List<String> values, List<List<String>> combinations) {
        if (ListUtils.isEmpty(combinations)) {
            for (String value: values) {
                combinations.add(Collections.singletonList(value));
            }
        } else {
            int size = combinations.size();
            for (int i = 0; i < size; i++) {
                List<String> list = combinations.get(i);
                for (int j = 1; j < values.size(); j++) {
                    List<String> updatedList = new ArrayList<>(list);
                    updatedList.add(values.get(j));
                    combinations.add(updatedList);
                }
                list.add(values.get(0));
            }
        }
    }
}
