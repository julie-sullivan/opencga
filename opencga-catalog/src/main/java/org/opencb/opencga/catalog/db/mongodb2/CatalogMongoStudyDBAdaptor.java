/*
 * Copyright 2015 OpenCB
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

package org.opencb.opencga.catalog.db.mongodb2;

import com.mongodb.BasicDBObject;
import com.mongodb.BasicDBObjectBuilder;
import com.mongodb.DBObject;
import com.mongodb.WriteResult;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.opencb.commons.datastore.core.ObjectMap;
import org.opencb.commons.datastore.core.Query;
import org.opencb.commons.datastore.core.QueryOptions;
import org.opencb.commons.datastore.core.QueryResult;
import org.opencb.commons.datastore.mongodb.MongoDBCollection;
import org.opencb.opencga.catalog.db.CatalogDBAdaptorFactory;
import org.opencb.opencga.catalog.db.api2.CatalogFileDBAdaptor;
import org.opencb.opencga.catalog.db.api2.CatalogIndividualDBAdaptor;
import org.opencb.opencga.catalog.db.api2.CatalogSampleDBAdaptor;
import org.opencb.opencga.catalog.db.api2.CatalogStudyDBAdaptor;
import org.opencb.opencga.catalog.exceptions.CatalogDBException;
import org.opencb.opencga.catalog.models.*;
import org.opencb.opencga.core.common.TimeUtils;

import java.net.URI;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static org.opencb.opencga.catalog.db.mongodb2.CatalogMongoDBUtils.*;

/**
 * Created on 07/09/15
 *
 * @author Jacobo Coll &lt;jacobo167@gmail.com&gt;
 */
public class CatalogMongoStudyDBAdaptor extends CatalogMongoDBAdaptor implements CatalogStudyDBAdaptor {

    private final CatalogDBAdaptorFactory dbAdaptorFactory;
    private final MongoDBCollection metaCollection;
    private final MongoDBCollection studyCollection;
    private final MongoDBCollection fileCollection;

    public CatalogMongoStudyDBAdaptor(CatalogDBAdaptorFactory dbAdaptorFactory, MongoDBCollection metaCollection, MongoDBCollection
            studyCollection, MongoDBCollection fileCollection) {
//        super(LoggerFactory.getLogger(CatalogMongoIndividualDBAdaptor.class));
        this.dbAdaptorFactory = dbAdaptorFactory;
        this.metaCollection = metaCollection;
        this.studyCollection = studyCollection;
        this.fileCollection = fileCollection;
    }


    /**
     * Study methods
     * ***************************
     */

//    @Override
//    public boolean studyExists(int studyId) {
//        QueryResult<Long> count = studyCollection.count(new BasicDBObject(_ID, studyId));
//        return count.getResult().get(0) != 0;
//    }
//
//    @Override
//    public void checkStudyId(int studyId) throws CatalogDBException {
//        if (!studyExists(studyId)) {
//            throw CatalogDBException.idNotFound("Study", studyId);
//        }
//    }

    private boolean studyAliasExists(int projectId, String studyAlias) throws CatalogDBException {
        // Check if study.alias already exists.
//        DBObject countQuery = BasicDBObjectBuilder
//                .start(_PROJECT_ID, projectId)
//                .append("alias", studyAlias).get();
//
//        QueryResult<Long> queryResult = studyCollection.count(countQuery);
//        return queryResult.getResult().get(0) != 0;

        if (projectId < 0) {
            throw CatalogDBException.newInstance("Project id '{}' is not valid: ", projectId);
        }

        Query query = new Query(QueryParams.PROJECT_ID.key(), projectId).append("alias", studyAlias);
        QueryResult<Long> count = count(query);
        return count.first() != 0;
    }

    @Override
    public QueryResult<Study> createStudy(int projectId, Study study, QueryOptions options) throws CatalogDBException {
        long startTime = startQuery();
        if (projectId < 0) {
            throw CatalogDBException.idNotFound("Project", projectId);
        }

        // Check if study.alias already exists.
        if (studyAliasExists(projectId, study.getAlias())) {
            throw new CatalogDBException("Study {alias:\"" + study.getAlias() + "\"} already exists");
        }

        //Set new ID
        int newId = getNewAutoIncrementId(metaCollection);
        study.setId(newId);

        //Empty nested fields
        List<File> files = study.getFiles();
        study.setFiles(Collections.<File>emptyList());

        List<Job> jobs = study.getJobs();
        study.setJobs(Collections.<Job>emptyList());

        //Create DBObject
        DBObject studyObject = getDbObject(study, "Study");
        studyObject.put(_ID, newId);

        //Set ProjectId
        studyObject.put(_PROJECT_ID, projectId);

        //Insert
        QueryResult<WriteResult> updateResult = studyCollection.insert(studyObject, null);

        //Check if the the study has been inserted
//        if (updateResult.getResult().get(0).getN() == 0) {
//            throw new CatalogDBException("Study {alias:\"" + study.getAlias() + "\"} already exists");
//        }

        // Insert nested fields
        String errorMsg = updateResult.getErrorMsg() != null ? updateResult.getErrorMsg() : "";

        for (File file : files) {
            String fileErrorMsg = dbAdaptorFactory.getCatalogFileDBAdaptor().createFile(study.getId(), file, options).getErrorMsg();
            if (fileErrorMsg != null && !fileErrorMsg.isEmpty()) {
                errorMsg += file.getName() + ":" + fileErrorMsg + ", ";
            }
        }

        for (Job job : jobs) {
//            String jobErrorMsg = createAnalysis(study.getId(), analysis).getErrorMsg();
            String jobErrorMsg = dbAdaptorFactory.getCatalogJobDBAdaptor().createJob(study.getId(), job, options).getErrorMsg();
            if (jobErrorMsg != null && !jobErrorMsg.isEmpty()) {
                errorMsg += job.getName() + ":" + jobErrorMsg + ", ";
            }
        }

        List<Study> studyList = getStudy(study.getId(), options).getResult();
        return endQuery("Create Study", startTime, studyList, errorMsg, null);

    }


    @Override
    public QueryResult<Study> getAllStudiesInProject(int projectId, QueryOptions options) throws CatalogDBException {
        long startTime = startQuery();
        if (!dbAdaptorFactory.getCatalogProjectDbAdaptor().projectExists(projectId)) {
            throw CatalogDBException.idNotFound("Project", projectId);
        }
        return endQuery("getAllSudiesInProject", startTime, getAllStudies(options == null ?
                new QueryOptions(StudyFilterOptions.projectId.toString(), projectId) :
                options.append(StudyFilterOptions.projectId.toString(), projectId)));
    }

    public QueryResult<Study> getAllStudies(QueryOptions queryOptions) throws CatalogDBException {
        long startTime = startQuery();

        List<DBObject> mongoQueryList = new LinkedList<>();

        for (Map.Entry<String, Object> entry : queryOptions.entrySet()) {
            String key = entry.getKey().split("\\.")[0];
            try {
                if (isDataStoreOption(key) || isOtherKnownOption(key)) {
                    continue;   //Exclude DataStore options
                }
                StudyFilterOptions option = StudyFilterOptions.valueOf(key);
                switch (option) {
                    case id:
                        addCompQueryFilter(option, option.name(), queryOptions, _ID, mongoQueryList);
                        break;
                    case projectId:
                        addCompQueryFilter(option, option.name(), queryOptions, _PROJECT_ID, mongoQueryList);
                        break;
                    default:
                        String queryKey = entry.getKey().replaceFirst(option.name(), option.getKey());
                        addCompQueryFilter(option, entry.getKey(), queryOptions, queryKey, mongoQueryList);
                        break;
                }
            } catch (IllegalArgumentException e) {
                throw new CatalogDBException(e);
            }
        }

        DBObject mongoQuery = new BasicDBObject();

        if (!mongoQueryList.isEmpty()) {
            mongoQuery.put("$and", mongoQueryList);
        }

        QueryResult<DBObject> queryResult = studyCollection.find(mongoQuery, filterOptions(queryOptions, FILTER_ROUTE_STUDIES));
        List<Study> studies = parseStudies(queryResult);
        for (Study study : studies) {
            joinFields(study.getId(), study, queryOptions);
        }

        return endQuery("getAllStudies", startTime, studies);
    }

    @Override
    public QueryResult<Study> getStudy(int studyId, QueryOptions options) throws CatalogDBException {
//        long startTime = startQuery();
//        //TODO: Parse QueryOptions include/exclude
//        DBObject query = new BasicDBObject(_ID, studyId);
//        QueryResult result = studyCollection.find(query, filterOptions(options, FILTER_ROUTE_STUDIES));
////        QueryResult queryResult = endQuery("get study", startTime, result);
//
//        List<Study> studies = parseStudies(result);
//        if (studies.isEmpty()) {
//            throw CatalogDBException.idNotFound("Study", studyId);
//        }
//
//        joinFields(studyId, studies.get(0), options);
//
//        //queryResult.setResult(studies);
//        return endQuery("Get Study", startTime, studies);

        return get(new Query(QueryParams.ID.key(), studyId), options);
    }

    @Override
    public QueryResult renameStudy(int studyId, String newStudyName) throws CatalogDBException {
        //TODO
//        long startTime = startQuery();
//
//        QueryResult studyResult = getStudy(studyId, sessionId);
        return null;
    }

    @Override
    public void updateStudyLastActivity(int studyId) throws CatalogDBException {
        modifyStudy(studyId, new ObjectMap("lastActivity", TimeUtils.getTime()));
    }

    @Override
    public QueryResult<Study> modifyStudy(int studyId, ObjectMap parameters) throws CatalogDBException {
        long startTime = startQuery();

        checkStudyId(studyId);
//        BasicDBObject studyParameters = new BasicDBObject();
        Document studyParameters = new Document();

        String[] acceptedParams = {"name", "creationDate", "creationId", "description", "status", "lastActivity", "cipher"};
        filterStringParams(parameters, studyParameters, acceptedParams);

        String[] acceptedLongParams = {"diskUsage"};
        filterLongParams(parameters, parameters, acceptedLongParams);

        String[] acceptedMapParams = {"attributes", "stats"};
        filterMapParams(parameters, studyParameters, acceptedMapParams);

        Map<String, Class<? extends Enum>> acceptedEnums = Collections.singletonMap(("type"), Study.Type.class);
        filterEnumParams(parameters, studyParameters, acceptedEnums);

        if (parameters.containsKey("uri")) {
            URI uri = parameters.get("uri", URI.class);
            studyParameters.put("uri", uri.toString());
        }

        if (!studyParameters.isEmpty()) {
//            BasicDBObject query = new BasicDBObject(_ID, studyId);
            Bson eq = Filters.eq(_ID, studyId);
            BasicDBObject updates = new BasicDBObject("$set", studyParameters);

//            QueryResult<WriteResult> updateResult = studyCollection.update(query, updates, null);
            QueryResult<UpdateResult> updateResult = studyCollection.update(eq, updates, null);
            if (updateResult.getResult().get(0).getModifiedCount() == 0) {
                throw CatalogDBException.idNotFound("Study", studyId);
            }
        }
        return endQuery("Modify study", startTime, getStudy(studyId, null));
    }

    /**
     * At the moment it does not clean external references to itself.
     */
//    @Override
//    public QueryResult<Integer> deleteStudy(int studyId) throws CatalogDBException {
//        long startTime = startQuery();
//        DBObject query = new BasicDBObject(_ID, studyId);
//        QueryResult<WriteResult> remove = studyCollection.remove(query, null);
//
//        List<Integer> deletes = new LinkedList<>();
//
//        if (remove.getResult().get(0).getN() == 0) {
//            throw CatalogDBException.idNotFound("Study", studyId);
//        } else {
//            deletes.add(remove.getResult().get(0).getN());
//            return endQuery("delete study", startTime, deletes);
//        }
//    }

    @Override
    public int getStudyId(int projectId, String studyAlias) throws CatalogDBException {
//        DBObject query = BasicDBObjectBuilder.start(_PROJECT_ID, projectId).append("alias", studyAlias).get();
//        BasicDBObject projection = new BasicDBObject("id", "true");
//        QueryResult<Document> queryResult = studyCollection.find(query, projection, null);
//        List<Study> studies = parseStudies(queryResult);

        Query query1 = new Query(QueryParams.PROJECT_ID.key(), projectId).append("alias", studyAlias);
        QueryOptions queryOptions = new QueryOptions("include", "id");
        QueryResult<Study> studyQueryResult = get(query1, queryOptions);
        List<Study> studies = studyQueryResult.getResult();
        return studies == null || studies.isEmpty() ? -1 : studies.get(0).getId();
    }

    @Override
    public int getProjectIdByStudyId(int studyId) throws CatalogDBException {
//        DBObject query = new BasicDBObject(_ID, studyId);
//        DBObject projection = new BasicDBObject(_PROJECT_ID, "true");
//        QueryResult<DBObject> result = studyCollection.find(query, projection, null);

        Query query = new Query(QueryParams.ID.key(), studyId);
        QueryOptions queryOptions = new QueryOptions("include", QueryParams.PROJECT_ID.key());
        QueryResult result = nativeGet(query, queryOptions);

        if (!result.getResult().isEmpty()) {
            Document study = (Document) result.getResult().get(0);
            Object id = study.get(_PROJECT_ID);
            return id instanceof Number ? ((Number) id).intValue() : (int) Double.parseDouble(id.toString());
        } else {
            throw CatalogDBException.idNotFound("Study", studyId);
        }
    }

    @Override
    public String getStudyOwnerId(int studyId) throws CatalogDBException {
        int projectId = getProjectIdByStudyId(studyId);
        return dbAdaptorFactory.getCatalogProjectDbAdaptor().getProjectOwnerId(projectId);
    }


    private long getDiskUsageByStudy(int studyId) {
        List<DBObject> operations = Arrays.<DBObject>asList(
                new BasicDBObject(
                        "$match",
                        new BasicDBObject(
                                _STUDY_ID,
                                studyId
                                //new BasicDBObject("$in",studyIds)
                        )
                ),
                new BasicDBObject(
                        "$group",
                        BasicDBObjectBuilder
                                .start("_id", "$" + _STUDY_ID)
                                .append("diskUsage",
                                        new BasicDBObject(
                                                "$sum",
                                                "$diskUsage"
                                        )).get()
                )
        );

//        Bson match = Aggregates.match(Filters.eq(_STUDY_ID, studyId));
//        Aggregates.group()

        QueryResult<DBObject> aggregate = fileCollection.aggregate(operations, null);
        if (aggregate.getNumResults() == 1) {
            Object diskUsage = aggregate.getResult().get(0).get("diskUsage");
            if (diskUsage instanceof Integer) {
                return ((Integer) diskUsage).longValue();
            } else if (diskUsage instanceof Long) {
                return ((Long) diskUsage);
            } else {
                return Long.parseLong(diskUsage.toString());
            }
        } else {
            return 0;
        }
    }


    @Override
    public QueryResult<Group> getGroup(int studyId, String userId, String groupId, QueryOptions options) throws CatalogDBException {
        long startTime = startQuery();

        BasicDBObject query = new BasicDBObject(_ID, studyId);
        BasicDBObject groupQuery = new BasicDBObject();
        if (userId != null) {
            groupQuery.put("userIds", userId);
        }
        if (groupId != null) {
            groupQuery.put("id", groupId);
        }
        BasicDBObject project = new BasicDBObject("groups", new BasicDBObject("$elemMatch", groupQuery));

        QueryResult<DBObject> queryResult = studyCollection.find(query, project, filterOptions(options, FILTER_ROUTE_STUDIES + "groups."));
        List<Study> studies = CatalogMongoDBUtils.parseStudies(queryResult);
        List<Group> groups = new ArrayList<>(1);
        for (Study study : studies) {
            if (study.getGroups() != null) {
                groups.addAll(study.getGroups());
            }
        }

        return endQuery("getGroup", startTime, groups);
    }

    boolean groupExists(int studyId, String groupId) throws CatalogDBException {
        BasicDBObject query = new BasicDBObject(_ID, studyId).append("groups.id", groupId);
        return studyCollection.count(query).first() == 1;
    }

    @Override
    public QueryResult<Group> addMemberToGroup(int studyId, String groupId, String userId) throws CatalogDBException {
        long startTime = startQuery();

        if (!groupExists(studyId, groupId)) {
            throw new CatalogDBException("Group \"" + groupId + "\" does not exists in study " + studyId);
        }

        BasicDBObject query = new BasicDBObject(_ID, studyId).append("groups.id", groupId);
        BasicDBObject update = new BasicDBObject("$addToSet", new BasicDBObject("groups.$.userIds", userId));

        QueryResult<WriteResult> queryResult = studyCollection.update(query, update, null);

        if (queryResult.first().getN() != 1) {
            throw new CatalogDBException("Unable to add member to group " + groupId);
        }

        return endQuery("addMemberToGroup", startTime, getGroup(studyId, null, groupId, null));
    }

    @Override
    public QueryResult<Group> removeMemberFromGroup(int studyId, String groupId, String userId) throws CatalogDBException {
        long startTime = startQuery();

        if (!groupExists(studyId, groupId)) {
            throw new CatalogDBException("Group \"" + groupId + "\" does not exists in study " + studyId);
        }

        BasicDBObject query = new BasicDBObject(_ID, studyId).append("groups.id", groupId);
        BasicDBObject update = new BasicDBObject("$pull", new BasicDBObject("groups.$.userIds", userId));

        QueryResult<WriteResult> queryResult = studyCollection.update(query, update, null);

        if (queryResult.first().getN() != 1) {
            throw new CatalogDBException("Unable to remove member to group " + groupId);
        }

        return endQuery("removeMemberFromGroup", startTime, getGroup(studyId, null, groupId, null));
    }


    /*
     * Variables Methods
     * ***************************
     */

    @Override
    public QueryResult<VariableSet> createVariableSet(int studyId, VariableSet variableSet) throws CatalogDBException {
        long startTime = startQuery();

        QueryResult<Long> count = studyCollection.count(
                new BasicDBObject("variableSets.name", variableSet.getName()).append("id", studyId));
        if (count.getResult().get(0) > 0) {
            throw new CatalogDBException("VariableSet { name: '" + variableSet.getName() + "'} already exists.");
        }

        int variableSetId = getNewAutoIncrementId(metaCollection);
        variableSet.setId(variableSetId);
        DBObject object = getDbObject(variableSet, "VariableSet");
        DBObject query = new BasicDBObject(_ID, studyId);
        DBObject update = new BasicDBObject("$push", new BasicDBObject("variableSets", object));

        QueryResult<WriteResult> queryResult = studyCollection.update(query, update, null);

        return endQuery("createVariableSet", startTime, getVariableSet(variableSetId, null));
    }

    @Override
    public QueryResult<VariableSet> getVariableSet(int variableSetId, QueryOptions options) throws CatalogDBException {
        long startTime = startQuery();

//        DBObject query = new BasicDBObject("variableSets.id", variableSetId);
//        DBObject projection = new BasicDBObject(
//                "variableSets",
//                new BasicDBObject(
//                        "$elemMatch",
//                        new BasicDBObject("id", variableSetId)
//                )
//        );

        Bson query = Filters.eq("variableSets.id", variableSetId);
        Bson projection = Projections.elemMatch("variableSets", Filters.eq("id", variableSetId));
        QueryOptions filteredOptions = filterOptions(options, FILTER_ROUTE_STUDIES);

        QueryResult<Document> queryResult = studyCollection.find(query, projection, filteredOptions);
        List<Study> studies = parseStudies(queryResult);
        if (studies.isEmpty() || studies.get(0).getVariableSets().isEmpty()) {
            throw new CatalogDBException("VariableSet {id: " + variableSetId + "} does not exist");
        }

        return endQuery("", startTime, studies.get(0).getVariableSets());
    }

    @Override
    public QueryResult<VariableSet> getAllVariableSets(int studyId, QueryOptions options) throws CatalogDBException {
        long startTime = startQuery();

        List<DBObject> mongoQueryList = new LinkedList<>();


        for (Map.Entry<String, Object> entry : options.entrySet()) {
            String key = entry.getKey().split("\\.")[0];
            try {
                if (isDataStoreOption(key) || isOtherKnownOption(key)) {
                    continue;   //Exclude DataStore options
                }
                CatalogSampleDBAdaptor.VariableSetFilterOption option = CatalogSampleDBAdaptor.VariableSetFilterOption.valueOf(key);
                switch (option) {
                    case studyId:
                        addCompQueryFilter(option, option.name(), options, _ID, mongoQueryList);
                        break;
                    default:
                        String optionsKey = "variableSets." + entry.getKey().replaceFirst(option.name(), option.getKey());
                        addCompQueryFilter(option, entry.getKey(), options, optionsKey, mongoQueryList);
                        break;
                }
            } catch (IllegalArgumentException e) {
                throw new CatalogDBException(e);
            }
        }

        QueryResult<DBObject> queryResult = studyCollection.aggregate(Arrays.<DBObject>asList(
                new BasicDBObject("$match", new BasicDBObject(_ID, studyId)),
                new BasicDBObject("$project", new BasicDBObject("variableSets", 1)),
                new BasicDBObject("$unwind", "$variableSets"),
                new BasicDBObject("$match", new BasicDBObject("$and", mongoQueryList))
        ), filterOptions(options, FILTER_ROUTE_STUDIES));

        List<VariableSet> variableSets = parseObjects(queryResult, Study.class).stream().map(study -> study.getVariableSets().get(0))
                .collect(Collectors.toList());

        return endQuery("", startTime, variableSets);
    }

    @Override
    public QueryResult<VariableSet> deleteVariableSet(int variableSetId, QueryOptions queryOptions) throws CatalogDBException {
        long startTime = startQuery();

        checkVariableSetInUse(variableSetId);
        int studyId = getStudyIdByVariableSetId(variableSetId);
        QueryResult<VariableSet> variableSet = getVariableSet(variableSetId, queryOptions);

        QueryResult<WriteResult> update = studyCollection.update(new BasicDBObject(_ID, studyId), new BasicDBObject("$pull", new
                BasicDBObject("variableSets", new BasicDBObject("id", variableSetId))), null);

        if (update.first().getN() == 0) {
            throw CatalogDBException.idNotFound("VariableSet", variableSetId);
        }

        return endQuery("Delete VariableSet", startTime, variableSet);

    }


    public void checkVariableSetInUse(int variableSetId) throws CatalogDBException {
        QueryResult<Sample> samples = dbAdaptorFactory.getCatalogSampleDBAdaptor().getAllSamples(new QueryOptions(CatalogSampleDBAdaptor
                .SampleFilterOption.variableSetId.toString(), variableSetId));
        if (samples.getNumResults() != 0) {
            String msg = "Can't delete VariableSetId, still in use as \"variableSetId\" of samples : [";
            for (Sample sample : samples.getResult()) {
                msg += " { id: " + sample.getId() + ", name: \"" + sample.getName() + "\" },";
            }
            msg += "]";
            throw new CatalogDBException(msg);
        }
        QueryResult<Individual> individuals = dbAdaptorFactory.getCatalogIndividualDBAdaptor().getAllIndividuals(new QueryOptions
                (CatalogIndividualDBAdaptor.IndividualFilterOption.variableSetId.toString(), variableSetId));
        if (samples.getNumResults() != 0) {
            String msg = "Can't delete VariableSetId, still in use as \"variableSetId\" of individuals : [";
            for (Individual individual : individuals.getResult()) {
                msg += " { id: " + individual.getId() + ", name: \"" + individual.getName() + "\" },";
            }
            msg += "]";
            throw new CatalogDBException(msg);
        }
    }


    @Override
    public int getStudyIdByVariableSetId(int variableSetId) throws CatalogDBException {
        DBObject query = new BasicDBObject("variableSets.id", variableSetId);

        QueryResult<DBObject> queryResult = studyCollection.find(query, new BasicDBObject("id", true), null);

        if (!queryResult.getResult().isEmpty()) {
            Object id = queryResult.getResult().get(0).get("id");
            return id instanceof Number ? ((Number) id).intValue() : (int) Double.parseDouble(id.toString());
        } else {
            throw CatalogDBException.idNotFound("VariableSet", variableSetId);
        }
    }



    /*
    * Helper methods
    ********************/

    //Join fields from other collections
    private void joinFields(User user, QueryOptions options) throws CatalogDBException {
        if (options == null) {
            return;
        }
        if (user.getProjects() != null) {
            for (Project project : user.getProjects()) {
                joinFields(project, options);
            }
        }
    }

    private void joinFields(Project project, QueryOptions options) throws CatalogDBException {
        if (options == null) {
            return;
        }
        if (options.getBoolean("includeStudies")) {
            project.setStudies(getAllStudiesInProject(project.getId(), options).getResult());
        }
    }

    private void joinFields(int studyId, Study study, QueryOptions options) throws CatalogDBException {
        if (studyId <= 0) {
            return;
        }

        if (options == null) {
            study.setDiskUsage(getDiskUsageByStudy(studyId));
            return;
        }

        List<String> include = options.getAsStringList("include");
        List<String> exclude = options.getAsStringList("exclude");
        if ((!include.isEmpty() && include.contains(FILTER_ROUTE_STUDIES + "diskUsage")) ||
                (!exclude.isEmpty() && !exclude.contains(FILTER_ROUTE_STUDIES + "diskUsage"))) {
            study.setDiskUsage(getDiskUsageByStudy(studyId));
        }

        if (options.getBoolean("includeFiles")) {
            study.setFiles(dbAdaptorFactory.getCatalogFileDBAdaptor().getAllFilesInStudy(studyId, options).getResult());
        }
        if (options.getBoolean("includeJobs")) {
            study.setJobs(dbAdaptorFactory.getCatalogJobDBAdaptor().getAllJobsInStudy(studyId, options).getResult());
        }
        if (options.getBoolean("includeSamples")) {
            study.setSamples(dbAdaptorFactory.getCatalogSampleDBAdaptor().getAllSamples(new QueryOptions(CatalogSampleDBAdaptor
                    .SampleFilterOption.studyId.toString(), studyId)).getResult());
        }
    }


    @Override
    public QueryResult<Long> count(Query query) {
        Bson bson = parseQuery(query);
        return studyCollection.count(bson);
    }

    @Override
    public QueryResult distinct(Query query, String field) {
        Bson bson = parseQuery(query);
        return studyCollection.distinct(field, bson);
    }

    @Override
    public QueryResult stats(Query query) {
        return null;
    }

    @Override
    public QueryResult<Study> get(Query query, QueryOptions options) {
        Bson bson = parseQuery(query);
        List<Document> queryResult = studyCollection.find(bson, options).getResult();

        // FIXME: Pedro. Parse and set clazz to study class.

        return null;
    }

    @Override
    public QueryResult nativeGet(Query query, QueryOptions options) {
        Bson bson = parseQuery(query);
        return studyCollection.find(bson, options);
    }

    @Override
    public QueryResult<Study> update(Query query, ObjectMap parameters) {
        return null;
    }

    /**
     * At the moment it does not clean external references to itself.
     */
    @Override
    public QueryResult<Long> delete(Query query) throws CatalogDBException {
        long startTime = startQuery();
//        DBObject query = new BasicDBObject(_ID, studyId);
        Bson bson = parseQuery(query);
        QueryResult<DeleteResult> remove = studyCollection.remove(bson, null);

//        List<Integer> deletes = new LinkedList<>();

        if (remove.getResult().get(0).getDeletedCount() == 0) {
//            throw CatalogDBException.idNotFound("Study", studyId);
            throw CatalogDBException.newInstance("Study id '{}' not found", query.get(QueryParams.ID.key()));
        } else {
//            deletes.add(remove.getResult().get(0).getDeletedCount());
            return endQuery("delete study", startTime, Collections.singletonList(remove.first().getDeletedCount()));
        }
    }

    @Override
    public Iterator<Study> iterator(Query query, QueryOptions options) {
        return null;
    }

    @Override
    public Iterator nativeIterator(Query query, QueryOptions options) {
        Bson bson = parseQuery(query);
        return studyCollection.nativeQuery().find(bson, options).iterator();
    }

    @Override
    public QueryResult rank(Query query, String field, int numResults, boolean asc) {
        return null;
    }

    @Override
    public QueryResult groupBy(Query query, String field, QueryOptions options) {
        Bson bsonQuery = parseQuery(query);
        return groupBy(studyCollection, bsonQuery, field, "name", options);
    }

    @Override
    public QueryResult groupBy(Query query, List<String> fields, QueryOptions options) {
        Bson bsonQuery = parseQuery(query);
        return groupBy(studyCollection, bsonQuery, fields, "name", options);
    }

    @Override
    public void forEach(Query query, Consumer<? super Object> action, QueryOptions options) {

    }

    private Bson parseQuery(Query query) {
        List<Bson> andBsonList = new ArrayList<>();

        // FIXME: Pedro. Check the mongodb names as well as integer createQueries

        createOrQuery(query, QueryParams.ID.key(), "id", andBsonList);
        createOrQuery(query, QueryParams.NAME.key(), "name", andBsonList);
        createOrQuery(query, QueryParams.ALIAS.key(), "alias", andBsonList);
        createOrQuery(query, QueryParams.CREATOR_ID.key(), "creatorId", andBsonList);
        createOrQuery(query, QueryParams.STATUS.key(), "status", andBsonList);
        createOrQuery(query, QueryParams.LAST_ACTIVITY.key(), "lastActivity", andBsonList);
        createOrQuery(query, QueryParams.PROJECT_ID.key(), "_projectId", andBsonList);

        createOrQuery(query, QueryParams.GROUP_ID.key(), "group.id", andBsonList);

        createOrQuery(query, QueryParams.EXPERIMENT_ID.key(), "experiment.id", andBsonList);
        createOrQuery(query, QueryParams.EXPERIMENT_NAME.key(), "experiment.name", andBsonList);
        createOrQuery(query, QueryParams.EXPERIMENT_TYPE.key(), "experiment.type", andBsonList);
        createOrQuery(query, QueryParams.EXPERIMENT_PLATFORM.key(), "experiment.platform", andBsonList);
        createOrQuery(query, QueryParams.EXPERIMENT_MANUFACTURER.key(), "experiment.manufacturer", andBsonList);
        createOrQuery(query, QueryParams.EXPERIMENT_DATE.key(), "experiment.date", andBsonList);
        createOrQuery(query, QueryParams.EXPERIMENT_LAB.key(), "experiment.lab", andBsonList);
        createOrQuery(query, QueryParams.EXPERIMENT_CENTER.key(), "experiment.center", andBsonList);
        createOrQuery(query, QueryParams.EXPERIMENT_RESPONSIBLE.key(), "experiment.responsible", andBsonList);

        createOrQuery(query, QueryParams.FILE_ID.key(), "file.id", andBsonList);
        createOrQuery(query, QueryParams.FILE_NAME.key(), "file.name", andBsonList);
        createOrQuery(query, QueryParams.FILE_TYPE.key(), "file.type", andBsonList);
        createOrQuery(query, QueryParams.FILE_FORMAT.key(), "file.format", andBsonList);
        createOrQuery(query, QueryParams.FILE_BIOFORMAT.key(), "file.bioformat", andBsonList);

        createOrQuery(query, QueryParams.JOB_ID.key(), "job.id", andBsonList);
        createOrQuery(query, QueryParams.JOB_NAME.key(), "job.name", andBsonList);
        createOrQuery(query, QueryParams.JOB_USER_ID.key(), "job.userId", andBsonList);
        createOrQuery(query, QueryParams.JOB_TOOL_NAME.key(), "job.toolName", andBsonList);
        createOrQuery(query, QueryParams.JOB_DATE.key(), "job.date", andBsonList);
        createOrQuery(query, QueryParams.JOB_STATUS.key(), "job.status", andBsonList);
        createOrQuery(query, QueryParams.JOB_DISK_USAGE.key(), "job.diskUsage", andBsonList);

        createOrQuery(query, QueryParams.INDIVIDUAL_ID.key(), "individual.id", andBsonList);
        createOrQuery(query, QueryParams.INDIVIDUAL_NAME.key(), "individual.name", andBsonList);
        createOrQuery(query, QueryParams.INDIVIDUAL_FATHER_ID.key(), "individual.fatherId", andBsonList);
        createOrQuery(query, QueryParams.INDIVIDUAL_MOTHER_ID.key(), "individual.motherId", andBsonList);
        createOrQuery(query, QueryParams.INDIVIDUAL_FAMILY.key(), "individual.family", andBsonList);
        createOrQuery(query, QueryParams.INDIVIDUAL_RACE.key(), "individual.race", andBsonList);

        createOrQuery(query, QueryParams.SAMPLE_ID.key(), "sample.id", andBsonList);
        createOrQuery(query, QueryParams.SAMPLE_NAME.key(), "sample.name", andBsonList);
        createOrQuery(query, QueryParams.SAMPLE_SOURCE.key(), "sample.source", andBsonList);
        createOrQuery(query, QueryParams.SAMPLE_INDIVIDUAL_ID.key(), "sample.individualId", andBsonList);

        createOrQuery(query, QueryParams.DATASET_ID.key(), "dataset.id", andBsonList);
        createOrQuery(query, QueryParams.DATASET_NAME.key(), "dataset.name", andBsonList);

        createOrQuery(query, QueryParams.COHORT_ID.key(), "cohort.id", andBsonList);
        createOrQuery(query, QueryParams.COHORT_NAME.key(), "cohort.name", andBsonList);
        createOrQuery(query, QueryParams.COHORT_TYPE.key(), "cohort.type", andBsonList);

        if (andBsonList.size() > 0) {
            return Filters.and(andBsonList);
        } else {
            return new Document();
        }
    }
}
