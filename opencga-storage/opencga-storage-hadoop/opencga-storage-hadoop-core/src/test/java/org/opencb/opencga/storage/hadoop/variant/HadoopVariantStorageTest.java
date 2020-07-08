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

package org.opencb.opencga.storage.hadoop.variant;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.CommonConfigurationKeysPublic;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.hbase.*;
import org.apache.hadoop.hbase.client.Admin;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.ConnectionFactory;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.coprocessor.CoprocessorHost;
import org.apache.hadoop.hbase.fs.HFileSystem;
import org.apache.hadoop.hbase.io.compress.Compression;
import org.apache.hadoop.hbase.io.hfile.CacheConfig;
import org.apache.hadoop.hbase.mapreduce.TableInputFormatBase;
import org.apache.hadoop.hbase.master.HMaster;
import org.apache.hadoop.hbase.master.ServerManager;
import org.apache.hadoop.hbase.master.TableNamespaceManager;
import org.apache.hadoop.hbase.master.procedure.MasterDDLOperationHelper;
import org.apache.hadoop.hbase.master.procedure.ModifyTableProcedure;
import org.apache.hadoop.hbase.procedure.ProcedureManagerHost;
import org.apache.hadoop.hbase.procedure.ZKProcedureUtil;
import org.apache.hadoop.hbase.procedure.flush.RegionServerFlushTableProcedureManager;
import org.apache.hadoop.hbase.procedure2.ProcedureExecutor;
import org.apache.hadoop.hbase.procedure2.store.wal.WALProcedureStore;
import org.apache.hadoop.hbase.regionserver.*;
import org.apache.hadoop.hbase.regionserver.compactions.CompactionConfiguration;
import org.apache.hadoop.hbase.regionserver.snapshot.RegionServerSnapshotManager;
import org.apache.hadoop.hbase.regionserver.wal.FSHLog;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.hbase.util.FSTableDescriptors;
import org.apache.hadoop.hdfs.server.blockmanagement.DatanodeDescriptor;
import org.apache.hadoop.hdfs.server.common.Storage;
import org.apache.hadoop.hdfs.server.datanode.DataNode;
import org.apache.hadoop.hdfs.server.namenode.EditLogFileOutputStream;
import org.apache.hadoop.hdfs.server.namenode.FSEditLog;
import org.apache.hadoop.hdfs.server.namenode.FSNamesystem;
import org.apache.hadoop.hdfs.server.namenode.NameNode;
import org.apache.hadoop.http.HttpServer2;
import org.apache.hadoop.io.compress.CodecPool;
import org.apache.hadoop.mapred.MapTask;
import org.apache.hadoop.mapred.Task;
import org.apache.hadoop.util.Tool;
import org.apache.log4j.Level;
import org.apache.phoenix.coprocessor.MetaDataEndpointImpl;
import org.apache.phoenix.hbase.index.Indexer;
import org.apache.phoenix.hbase.index.covered.data.IndexMemStore;
import org.apache.phoenix.hbase.index.parallel.BaseTaskRunner;
import org.apache.phoenix.hbase.index.write.ParallelWriterIndexCommitter;
import org.apache.phoenix.schema.MetaDataClient;
import org.apache.tools.ant.types.Commandline;
import org.apache.zookeeper.ClientCnxn;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.server.NIOServerCnxn;
import org.apache.zookeeper.server.NIOServerCnxnFactory;
import org.apache.zookeeper.server.PrepRequestProcessor;
import org.apache.zookeeper.server.ZooKeeperServer;
import org.junit.Assert;
import org.junit.rules.ExternalResource;
import org.opencb.biodata.models.variant.VariantFileMetadata;
import org.opencb.biodata.models.variant.avro.VariantType;
import org.opencb.commons.datastore.core.ObjectMap;
import org.opencb.opencga.storage.core.config.StorageConfiguration;
import org.opencb.opencga.storage.core.config.StorageEngineConfiguration;
import org.opencb.opencga.storage.core.exceptions.StorageEngineException;
import org.opencb.opencga.storage.core.variant.VariantStorageBaseTest;
import org.opencb.opencga.storage.core.variant.VariantStorageEngine;
import org.opencb.opencga.storage.core.variant.VariantStorageOptions;
import org.opencb.opencga.storage.core.variant.VariantStorageTest;
import org.opencb.opencga.storage.hadoop.utils.HBaseManager;
import org.opencb.opencga.storage.hadoop.variant.adaptors.phoenix.PhoenixHelper;
import org.opencb.opencga.storage.hadoop.variant.adaptors.phoenix.VariantPhoenixHelper;
import org.opencb.opencga.storage.hadoop.variant.executors.MRExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Created on 15/10/15
 *
 * @author Jacobo Coll &lt;jacobo167@gmail.com&gt;
 */
public interface HadoopVariantStorageTest /*extends VariantStorageManagerTestUtils */ extends VariantStorageTest {

    AtomicReference<HBaseTestingUtility> utility = new AtomicReference<>(null);
    AtomicReference<Configuration> configuration = new AtomicReference<>(null);
//    Set<HadoopVariantStorageEngine> managers = new ConcurrentHashSet<>();
    AtomicReference<HadoopVariantStorageEngine> manager = new AtomicReference<>();

    class HadoopExternalResource extends ExternalResource implements HadoopVariantStorageTest {

        Logger logger = LoggerFactory.getLogger(this.getClass());
        @Override
        public void before() throws Throwable {
            if (utility.get() == null) {

                // Disable most of the useless loggers

                // HBase loggers
                org.apache.log4j.Logger.getLogger(HStore.class).setLevel(Level.WARN);
                org.apache.log4j.Logger.getLogger(HRegion.class).setLevel(Level.WARN);
                org.apache.log4j.Logger.getLogger(HMaster.class).setLevel(Level.WARN);
                org.apache.log4j.Logger.getLogger(HRegionServer.class).setLevel(Level.WARN);
                org.apache.log4j.Logger.getLogger(HFileSystem.class).setLevel(Level.WARN);
//                org.apache.log4j.Logger.getLogger(HBaseAdmin.class).setLevel(Level.WARN); // This logger is interesting!
                org.apache.log4j.Logger.getLogger(ServerManager.class).setLevel(Level.WARN);
                org.apache.log4j.Logger.getLogger(RegionServerSnapshotManager.class).setLevel(Level.WARN);
                org.apache.log4j.Logger.getLogger(SplitLogWorker.class).setLevel(Level.WARN);
                org.apache.log4j.Logger.getLogger("org.apache.hadoop.hbase.backup.regionserver.LogRollRegionServerProcedureManager").setLevel(Level.WARN);
                org.apache.log4j.Logger.getLogger(HeapMemoryManager.class).setLevel(Level.WARN);
                org.apache.log4j.Logger.getLogger("org.apache.hadoop.hbase.master.MasterMobCompactionThread").setLevel(Level.WARN);
                org.apache.log4j.Logger.getLogger(RegionServerFlushTableProcedureManager.class).setLevel(Level.WARN);
                org.apache.log4j.Logger.getLogger("org.apache.hadoop.hbase.master.procedure.MasterProcedureScheduler").setLevel(Level.WARN);
                org.apache.log4j.Logger.getLogger(ChoreService.class).setLevel(Level.WARN);
                org.apache.log4j.Logger.getLogger("org.apache.hadoop.hbase.quotas.RegionServerQuotaManager").setLevel(Level.WARN);
                org.apache.log4j.Logger.getLogger("org.apache.hadoop.hbase.MetaMigrationConvertingToPB").setLevel(Level.WARN); // Removed in hbase2
                org.apache.log4j.Logger.getLogger(TableNamespaceManager.class).setLevel(Level.WARN);
                org.apache.log4j.Logger.getLogger(ProcedureManagerHost.class).setLevel(Level.WARN);
                org.apache.log4j.Logger.getLogger(ZKProcedureUtil.class).setLevel(Level.WARN);
                org.apache.log4j.Logger.getLogger(WALProcedureStore.class).setLevel(Level.WARN);
                org.apache.log4j.Logger.getLogger(ProcedureExecutor.class).setLevel(Level.WARN);
                org.apache.log4j.Logger.getLogger(ModifyTableProcedure.class).setLevel(Level.WARN);
                org.apache.log4j.Logger.getLogger(DefaultStoreFlusher.class.getName()).setLevel(Level.WARN);
                org.apache.log4j.Logger.getLogger("org.apache.hadoop.hbase.regionserver.StoreFile$Reader").setLevel(Level.WARN);// Moved to StoreFileReader in hbase2
                org.apache.log4j.Logger.getLogger("org.apache.hadoop.hbase.regionserver.StoreFileReader").setLevel(Level.WARN);
                org.apache.log4j.Logger.getLogger(RegionCoprocessorHost.class).setLevel(Level.WARN);
                org.apache.log4j.Logger.getLogger(CoprocessorHost.class).setLevel(Level.WARN);
                org.apache.log4j.Logger.getLogger("org.apache.hadoop.hbase.master.RegionStates").setLevel(Level.WARN);// Moved to assignment in hbase2
                org.apache.log4j.Logger.getLogger("org.apache.hadoop.hbase.master.assignment.RegionStates").setLevel(Level.WARN);
                org.apache.log4j.Logger.getLogger("org.apache.hadoop.hbase.master.assignment.RegionStateStore").setLevel(Level.WARN);
                org.apache.log4j.Logger.getLogger("org.apache.hadoop.hbase.master.assignment.AssignProcedure").setLevel(Level.WARN);
                org.apache.log4j.Logger.getLogger("org.apache.hadoop.hbase.master.assignment.RegionTransitionProcedure").setLevel(Level.WARN);
                org.apache.log4j.Logger.getLogger("org.apache.hadoop.hbase.master.AssignmentManager").setLevel(Level.WARN);// Moved to assignment in hbase2
                org.apache.log4j.Logger.getLogger("org.apache.hadoop.hbase.master.assignment.AssignmentManager").setLevel(Level.WARN);
                org.apache.log4j.Logger.getLogger(MasterDDLOperationHelper.class).setLevel(Level.WARN);
                org.apache.log4j.Logger.getLogger(FSTableDescriptors.class).setLevel(Level.WARN);
                org.apache.log4j.Logger.getLogger("org.apache.hadoop.hbase.zookeeper.ZKTableStateManager").setLevel(Level.WARN);// Removed in hbase2
                org.apache.log4j.Logger.getLogger(MetaTableAccessor.class).setLevel(Level.WARN);
                org.apache.log4j.Logger.getLogger(RSRpcServices.class).setLevel(Level.WARN);
                org.apache.log4j.Logger.getLogger(CacheConfig.class).setLevel(Level.WARN);
                org.apache.log4j.Logger.getLogger(CompactionConfiguration.class).setLevel(Level.WARN);
                org.apache.log4j.Logger.getLogger("org.apache.hadoop.hbase.regionserver.CompactSplitThread").setLevel(Level.WARN);// Moved to "CompactSplit" in hbase2
                org.apache.log4j.Logger.getLogger("org.apache.hadoop.hbase.regionserver.CompactSplit").setLevel(Level.WARN);
                org.apache.log4j.Logger.getLogger("org.apache.hadoop.metrics2.impl.MetricsConfig").setLevel(Level.ERROR);
                org.apache.log4j.Logger.getLogger("org.apache.hadoop.hbase.client.ConnectionManager$HConnectionImplementation").setLevel(Level.WARN);

                // Phoenix loggers
                org.apache.log4j.Logger.getLogger(ParallelWriterIndexCommitter.class).setLevel(Level.WARN);
                org.apache.log4j.Logger.getLogger(BaseTaskRunner.class).setLevel(Level.WARN);
                org.apache.log4j.Logger.getLogger(Indexer.class).setLevel(Level.WARN);
                org.apache.log4j.Logger.getLogger(IndexMemStore.class).setLevel(Level.WARN);
                org.apache.log4j.Logger.getLogger("org.apache.phoenix.hbase.index.write.recovery.TrackingParallelWriterIndexCommitter").setLevel(Level.WARN);// Moved in Phoenix5
                org.apache.log4j.Logger.getLogger("org.apache.phoenix.hbase.index.write.TrackingParallelWriterIndexCommitter").setLevel(Level.WARN);
                org.apache.log4j.Logger.getLogger(MetaDataEndpointImpl.class).setLevel(Level.WARN);
                org.apache.log4j.Logger.getLogger(MetaDataClient.class).setLevel(Level.WARN);
//                org.apache.log4j.Logger.getLogger(QueryUtil.class).setLevel(Level.WARN); // Interesting. Only logs the new connections

                // Hadoop loggers
                org.apache.log4j.Logger.getLogger(CodecPool.class).setLevel(Level.WARN);
                org.apache.log4j.Logger.getLogger(HttpServer2.class).setLevel(Level.WARN);
                org.apache.log4j.Logger.getLogger(org.apache.hadoop.ipc.Server.class).setLevel(Level.WARN);

                // Zookeeper loggers
                org.apache.log4j.Logger.getLogger(ZooKeeper.class).setLevel(Level.WARN);
                org.apache.log4j.Logger.getLogger(ZooKeeperServer.class).setLevel(Level.WARN);
                // Disable WARN messages from ClientCnxn. See [HBASE-18654]
                org.apache.log4j.Logger.getLogger(ClientCnxn.class).setLevel(Level.ERROR);
                org.apache.log4j.Logger.getLogger(NIOServerCnxn.class).setLevel(Level.WARN);
                org.apache.log4j.Logger.getLogger(NIOServerCnxnFactory.class).setLevel(Level.WARN);
                org.apache.log4j.Logger.getLogger(PrepRequestProcessor.class).setLevel(Level.WARN);
                // Interesting class for logging new Zookeeper connections
//                org.apache.log4j.Logger.getLogger(RecoverableZooKeeper.class).setLevel(Level.WARN);

                // HDFS loggers
                org.apache.log4j.Logger.getLogger(FSNamesystem.class.getName() + ".audit").setLevel(Level.WARN);
                org.apache.log4j.Logger.getLogger(Storage.class).setLevel(Level.WARN);
                org.apache.log4j.Logger.getLogger(FSNamesystem.class).setLevel(Level.WARN);
                org.apache.log4j.Logger.getLogger(NameNode.stateChangeLog.getName()).setLevel(Level.WARN);
                org.apache.log4j.Logger.getLogger(NameNode.blockStateChangeLog.getName()).setLevel(Level.WARN);
                org.apache.log4j.Logger.getLogger(DataNode.class).setLevel(Level.WARN);
                org.apache.log4j.Logger.getLogger(DatanodeDescriptor.class).setLevel(Level.WARN);
                org.apache.log4j.Logger.getLogger(FSEditLog.class).setLevel(Level.WARN);
                org.apache.log4j.Logger.getLogger(FSHLog.class).setLevel(Level.WARN);
                org.apache.log4j.Logger.getLogger(EditLogFileOutputStream.class).setLevel(Level.WARN);
                org.apache.log4j.Logger.getLogger("org.apache.hadoop.hdfs.server.datanode.fsdataset.impl.FsDatasetAsyncDiskService").setLevel(Level.WARN);

                // MR loggers
//                org.apache.log4j.Logger.getLogger(LocalJobRunner.class).setLevel(Level.WARN);
                org.apache.log4j.Logger.getLogger(Task.class).setLevel(Level.WARN);
                org.apache.log4j.Logger.getLogger(MapTask.class).setLevel(Level.WARN);
                org.apache.log4j.Logger.getLogger(TableInputFormatBase.class).setLevel(Level.WARN);

                utility.set(new HBaseTestingUtility());
                Configuration conf = utility.get().getConfiguration();
                HadoopVariantStorageTest.configuration.set(conf);


//                // Change port to avoid port collisions
//                utility.get().getStorageConfiguration().setInt(HConstants.MASTER_INFO_PORT, HConstants.DEFAULT_MASTER_INFOPORT + 1);

                // Enable phoenix secundary indexes
                conf.set("hbase.regionserver.wal.codec",
                        org.apache.hadoop.hbase.regionserver.wal.IndexedWALEditCodec.class.getName());
                conf.set("hbase.region.server.rpc.scheduler.factory.class",
                        org.apache.hadoop.hbase.ipc.PhoenixRpcSchedulerFactory.class.getName());
                conf.set("hbase.rpc.controllerfactory.class",
                        org.apache.hadoop.hbase.ipc.controller.ServerRpcControllerFactory.class.getName());

                // Not required in Phoenix 4.8
//                conf.set("hbase.master.loadbalancer.class",
//                        org.apache.phoenix.hbase.index.balancer.IndexLoadBalancer.class.getName());
//                conf.set("hbase.coprocessor.master.classes",
//                        org.apache.phoenix.hbase.index.master.IndexMasterObserver.class.getName());
//                conf.set("hbase.coprocessor.regionserver.classes",
//                        org.apache.hadoop.hbase.regionserver.LocalIndexMerger.class.getName());

//                conf.setBoolean(QueryServices.IS_NAMESPACE_MAPPING_ENABLED, true);
                conf.setBoolean("phoenix.schema.isNamespaceMappingEnabled", true);

                // Zookeeper always with the same clientPort.
//                conf.setInt("test.hbase.zookeeper.property.clientPort", 55419);
                // Zookeeper increase max client connexions
                conf.setInt(HConstants.ZOOKEEPER_MAX_CLIENT_CNXNS, 1000);

                // Do not put up web UI
                conf.setInt("hbase.regionserver.info.port", -1);
                conf.setInt("hbase.master.info.port", -1);
                //org.apache.commons.configuration2.Configuration
                utility.get().startMiniCluster(1);

    //            MiniMRCluster miniMRCluster = utility.startMiniMapReduceCluster();
    //            MiniMRClientCluster miniMRClientCluster = MiniMRClientClusterFactory.create(HadoopVariantStorageManagerTestUtils.class, 1, configuration);
    //            miniMRClientCluster.start();

//                checkHBaseMiniCluster();
            }
        }

        @Override
        public void after() {
            try {
                logger.info("Closing HBaseTestingUtility");
//                for (HadoopVariantStorageEngine manager : managers) {
//                    manager.close();
//                }
                if (manager.get() != null) {
                    manager.get().close();
                    manager.set(null);
                }
//                for (Connection connection : HBaseManager.CONNECTIONS) {
//                    connection.close();
//                }
                System.out.println("HBaseManager.getOpenConnections() = " + HBaseManager.getOpenConnections());

                configuration.set(null);
                try {
                    if (utility.get() != null) {
                        utility.get().shutdownMiniCluster();
                    }
                } finally {
                    utility.set(null);
                }
            } catch (Exception e) {
                Assert.fail(e.getMessage());
            }
            System.out.println("##### HBaseMiniCluster down ###################");
        }

        public Configuration getConf() {
            return configuration.get();
        }

        private void checkHBaseMiniCluster() throws IOException {
            Connection con = ConnectionFactory.createConnection(configuration.get());
            HBaseManager hBaseManager = new HBaseManager(configuration.get(), con);

            String tableName = "table";
            byte[] columnFamily = Bytes.toBytes("0");
            hBaseManager.createTableIfNeeded(hBaseManager.getConnection(), tableName, columnFamily,
                    Collections.emptyList(), Compression.Algorithm.NONE);
            hBaseManager.act(tableName, table -> {
                table.put(Arrays.asList(new Put(Bytes.toBytes("r1")).addColumn(columnFamily, Bytes.toBytes("c"), Bytes.toBytes("value 1")),
                        new Put(Bytes.toBytes("r2")).addColumn(columnFamily, Bytes.toBytes("c"), Bytes.toBytes("value 2")),
                        new Put(Bytes.toBytes("r2")).addColumn(columnFamily, Bytes.toBytes("c2"), Bytes.toBytes("value 3"))));
            });

            hBaseManager.act(tableName, table -> {
                table.getScanner(columnFamily).forEach(result -> {
                    System.out.println("Row: " + Bytes.toString(result.getRow()));
                    for (Map.Entry<byte[], byte[]> entry : result.getFamilyMap(columnFamily).entrySet()) {
                        System.out.println(Bytes.toString(entry.getKey()) + " = " + Bytes.toString(entry.getValue()));
                    }
                });
            });

            TableName tname = TableName.valueOf(tableName);
            try (Admin admin = con.getAdmin()) {
                if (admin.tableExists(tname)) {
                    utility.get().deleteTable(tname);
                }
            }

            con.close();
        }

        public void flush(String name) throws Exception {
            TableName table = TableName.valueOf(name);
            utility.get().flush(table);
            utility.get().compact(table, true);
        }
    }

    @Override
    default HadoopVariantStorageEngine getVariantStorageEngine() throws Exception {
        return getHadoopVariantStorageEngine(getOtherStorageConfigurationOptions());
    }


    static HadoopVariantStorageEngine getHadoopVariantStorageEngine() throws Exception {
        return getHadoopVariantStorageEngine(new ObjectMap());
    }

    static HadoopVariantStorageEngine getHadoopVariantStorageEngine(Map<String, ?> otherStorageConfigurationOptions) throws Exception {
        synchronized (manager) {
            if (manager.get() == null) {
                manager.set(new HadoopVariantStorageEngine());
            }
        }
        HadoopVariantStorageEngine manager = HadoopVariantStorageTest.manager.get();

        //Make a copy of the configuration
        Configuration conf = new Configuration(false);
        HBaseConfiguration.merge(conf, HadoopVariantStorageTest.configuration.get());
        StorageConfiguration storageConfiguration = getStorageConfiguration(conf);
        storageConfiguration.getVariantEngine(HadoopVariantStorageEngine.STORAGE_ENGINE_ID)
                .getOptions()
                .putAll(otherStorageConfigurationOptions);

        manager.setConfiguration(storageConfiguration, HadoopVariantStorageEngine.STORAGE_ENGINE_ID, VariantStorageBaseTest.DB_NAME);
        manager.mrExecutor = new TestMRExecutor(configuration.get());
        manager.conf = conf;
        return manager;
    }

    default TestMRExecutor getMrExecutor() {
        return new TestMRExecutor(configuration.get());
    }

    static StorageConfiguration getStorageConfiguration(Configuration conf) throws IOException {
        StorageConfiguration storageConfiguration;
        try (InputStream is = HadoopVariantStorageTest.class.getClassLoader().getResourceAsStream("storage-configuration.yml")) {
            storageConfiguration = StorageConfiguration.load(is);
        }
        return updateStorageConfiguration(storageConfiguration, conf);
    }

    static StorageConfiguration updateStorageConfiguration(StorageConfiguration storageConfiguration, Configuration conf) throws IOException {
        storageConfiguration.getVariant().setDefaultEngine(HadoopVariantStorageEngine.STORAGE_ENGINE_ID);
        StorageEngineConfiguration variantConfiguration = storageConfiguration.getVariantEngine(HadoopVariantStorageEngine.STORAGE_ENGINE_ID);
        ObjectMap options = variantConfiguration.getOptions();

        options.put(HadoopVariantStorageOptions.MR_EXECUTOR.key(), TestMRExecutor.class.getName());
        TestMRExecutor.setStaticConfiguration(conf);

        options.put(HadoopVariantStorageOptions.MR_ADD_DEPENDENCY_JARS.key(), false);
        EnumSet<Compression.Algorithm> supportedAlgorithms = EnumSet.of(Compression.Algorithm.NONE, HBaseTestingUtility.getSupportedCompressionAlgorithms());

        options.put(HadoopVariantStorageOptions.ARCHIVE_TABLE_COMPRESSION.key(), supportedAlgorithms.contains(Compression.Algorithm.GZ)
                ? Compression.Algorithm.GZ.getName()
                : Compression.Algorithm.NONE.getName());
        options.put(HadoopVariantStorageOptions.VARIANT_TABLE_COMPRESSION.key(), supportedAlgorithms.contains(Compression.Algorithm.SNAPPY)
                ? Compression.Algorithm.SNAPPY.getName()
                : Compression.Algorithm.NONE.getName());
        options.put(HadoopVariantStorageOptions.SAMPLE_INDEX_TABLE_COMPRESSION.key(), supportedAlgorithms.contains(Compression.Algorithm.SNAPPY)
                ? Compression.Algorithm.SNAPPY.getName()
                : Compression.Algorithm.NONE.getName());
        options.put(HadoopVariantStorageOptions.ANNOTATION_INDEX_TABLE_COMPRESSION.key(), supportedAlgorithms.contains(Compression.Algorithm.SNAPPY)
                ? Compression.Algorithm.SNAPPY.getName()
                : Compression.Algorithm.NONE.getName());
        options.put(HadoopVariantStorageOptions.PENDING_ANNOTATION_TABLE_COMPRESSION.key(), supportedAlgorithms.contains(Compression.Algorithm.SNAPPY)
                ? Compression.Algorithm.SNAPPY.getName()
                : Compression.Algorithm.NONE.getName());
        options.put(HadoopVariantStorageOptions.PENDING_SECONDARY_INDEX_TABLE_COMPRESSION.key(), supportedAlgorithms.contains(Compression.Algorithm.SNAPPY)
                ? Compression.Algorithm.SNAPPY.getName()
                : Compression.Algorithm.NONE.getName());

        FileSystem fs = FileSystem.get(HadoopVariantStorageTest.configuration.get());
        String intermediateDirectory = fs.getHomeDirectory().toUri().resolve("opencga_test/").toString();
//        System.out.println(HadoopVariantStorageEngine.INTERMEDIATE_HDFS_DIRECTORY + " = " + intermediateDirectory);
        options.put(CommonConfigurationKeysPublic.FS_DEFAULT_NAME_KEY, conf.get(CommonConfigurationKeysPublic.FS_DEFAULT_NAME_KEY));
        options.put(HadoopVariantStorageOptions.INTERMEDIATE_HDFS_DIRECTORY.key(), intermediateDirectory);

        options.put(HadoopVariantStorageOptions.ARCHIVE_TABLE_PRESPLIT_SIZE.key(), 5);
        options.put(HadoopVariantStorageOptions.VARIANT_TABLE_PRESPLIT_SIZE.key(), 5);
        options.put(HadoopVariantStorageOptions.EXPECTED_FILES_NUMBER.key(), 10);
        options.put(VariantStorageOptions.MERGE_MODE.key(), VariantStorageEngine.MergeMode.BASIC);

        options.put(VariantStorageOptions.SPECIES.key(), "hsapiens");
        options.put(VariantStorageOptions.ASSEMBLY.key(), "grch37");

        variantConfiguration.getDatabase().setHosts(Collections.singletonList("hbase://" + HadoopVariantStorageTest.configuration.get().get(HConstants.ZOOKEEPER_QUORUM)));
        return storageConfiguration;
    }

    default Map<String, ?> getOtherStorageConfigurationOptions() {
        return new ObjectMap();
    }

    default void clearHBase() throws Exception {
        try (Connection con = ConnectionFactory.createConnection(configuration.get()); Admin admin = con.getAdmin()) {
            for (TableName tableName : admin.listTableNames()) {
                utility.get().deleteTableIfAny(tableName);
            }
        }
    }

    @Override
    default void clearDB(String tableName) throws Exception {
        if (Objects.equals(tableName, VariantStorageBaseTest.DB_NAME)) {
            try (Connection con = ConnectionFactory.createConnection(configuration.get()); Admin admin = con.getAdmin()) {
                for (TableName table : admin.listTableNames()) {
                    if (table.getNameAsString().startsWith(tableName)) {
                        deleteTable(table.getNameAsString());
                    }
                }
            }
        } else {
            deleteTable(tableName);
        }
    }

    default void deleteTable(String tableName) throws Exception {
        LoggerFactory.getLogger(HadoopVariantStorageTest.class).info("Drop table " + tableName);
        PhoenixHelper phoenixHelper = new PhoenixHelper(configuration.get());
        try (java.sql.Connection con = phoenixHelper.newJdbcConnection()) {
            if (phoenixHelper.tableExists(con, tableName)) {
                phoenixHelper.dropTable(con, tableName, VariantPhoenixHelper.DEFAULT_TABLE_TYPE, true, true);
            }
        }
        utility.get().deleteTableIfAny(TableName.valueOf(tableName));
    }

    @Override
    default void close() throws Exception {
        if (manager.get() != null) {
            manager.get().close();
        }
    }

    default int getExpectedNumLoadedVariants(VariantFileMetadata fileMetadata) {
        int numRecords = 0;
        for (VariantType variantType : HadoopVariantStorageEngine.TARGET_VARIANT_TYPE_SET) {
            numRecords += fileMetadata.getStats().getTypeCount().getOrDefault(variantType.name(), 0L).intValue();
        }
        return numRecords;
    }

    class TestMRExecutor extends MRExecutor {

        private static Configuration staticConfiguration;
        private final Configuration configuration;

        public TestMRExecutor() {
            this.configuration = new Configuration(staticConfiguration);
        }

        public TestMRExecutor(Configuration configuration) {
            this.configuration = configuration;
        }

        public static void setStaticConfiguration(Configuration staticConfiguration) {
            TestMRExecutor.staticConfiguration = staticConfiguration;
        }


        @Override
        public <T extends Tool> int run(Class<T> clazz, String[] args, ObjectMap options) throws StorageEngineException {
            try {
                // Copy configuration
                Configuration conf = new Configuration(false);
                HBaseConfiguration.merge(conf, configuration);

                System.out.println("Executing " + clazz.getSimpleName() + ": " + Arrays.toString(args));
                Method method = clazz.getMethod("privateMain", String[].class, Configuration.class);
                Object o = method.invoke(clazz.newInstance(), args, conf);
                System.out.println("Finish execution " + clazz.getSimpleName());
                if (((Number) o).intValue() != 0) {
                    throw new StorageEngineException("Error executing MapReduce. Exit code: " + o);
                }
                return ((Number) o).intValue();
            } catch (Exception e) {
                throw new StorageEngineException("Error executing MapReduce.", e);
            }
        }

        @Override
        public int run(String executable, String args) {
            try {
                // Copy configuration
                Configuration conf = new Configuration(false);
                HBaseConfiguration.merge(conf, configuration);

                String className = executable.substring(executable.lastIndexOf(" ") + 1);
                Class<?> clazz = Class.forName(className);
                System.out.println("Executing " + clazz.getSimpleName() + ": " + executable + " " + args);
                Method method = clazz.getMethod("privateMain", String[].class, Configuration.class);
                Object o = method.invoke(clazz.newInstance(), Commandline.translateCommandline(args), conf);
                System.out.println("Finish execution " + clazz.getSimpleName());
                if (((Number) o).intValue() != 0) {
                    throw new RuntimeException("Exit code = " + o);
                }
                return ((Number) o).intValue();

            } catch (Exception e) {
//                e.printStackTrace();
//                return -1;
                throw new RuntimeException(e);
            }
//            return 0;
        }
    }


}
