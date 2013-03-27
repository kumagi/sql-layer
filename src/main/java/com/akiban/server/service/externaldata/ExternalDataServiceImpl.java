/**
 * Copyright (C) 2009-2013 Akiban Technologies, Inc.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.akiban.server.service.externaldata;

import com.akiban.ais.model.AkibanInformationSchema;
import com.akiban.ais.model.CacheValueGenerator;
import com.akiban.ais.model.Column;
import com.akiban.ais.model.UserTable;
import com.akiban.qp.memoryadapter.MemoryAdapter;
import com.akiban.qp.operator.API;
import com.akiban.qp.operator.Cursor;
import com.akiban.qp.operator.Operator;
import com.akiban.qp.operator.QueryContext;
import com.akiban.qp.rowtype.RowType;
import com.akiban.qp.operator.SimpleQueryContext;
import com.akiban.qp.operator.StoreAdapter;
import com.akiban.qp.persistitadapter.PersistitAdapter;
import com.akiban.qp.rowtype.Schema;
import com.akiban.server.api.dml.scan.NewRow;
import com.akiban.server.api.DMLFunctions;
import com.akiban.server.error.NoSuchTableException;
import com.akiban.server.service.Service;
import com.akiban.server.service.ServiceManager;
import com.akiban.server.service.config.ConfigurationService;
import com.akiban.server.service.dxl.DXLService;
import com.akiban.server.service.externaldata.JsonRowWriter.WriteTableRow;
import com.akiban.server.service.session.Session;
import com.akiban.server.service.transaction.TransactionService;
import com.akiban.server.service.tree.TreeService;
import com.akiban.server.store.Store;
import com.akiban.server.types3.mcompat.mtypes.MString;
import com.akiban.server.types3.pvalue.PValue;
import com.akiban.util.AkibanAppender;

import com.google.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.Collections;
import java.util.List;

public class ExternalDataServiceImpl implements ExternalDataService, Service {
    private final ConfigurationService configService;
    private final DXLService dxlService;
    private final Store store;
    private final TransactionService transactionService;
    private final TreeService treeService;
    private final ServiceManager serviceManager;
    
    private static final Logger logger = LoggerFactory.getLogger(ExternalDataServiceImpl.class);

    private static final CacheValueGenerator<PlanGenerator> CACHED_PLAN_GENERATOR =
            new CacheValueGenerator<PlanGenerator>() {
                @Override
                public PlanGenerator valueFor(AkibanInformationSchema ais) {
                    return new PlanGenerator(ais);
                }
            };


    @Inject
    public ExternalDataServiceImpl(ConfigurationService configService,
                                   DXLService dxlService, Store store,
                                   TransactionService transactionService,
                                   TreeService treeService,
                                   ServiceManager serviceManager) {
        this.configService = configService;
        this.dxlService = dxlService;
        this.store = store;
        this.transactionService = transactionService;
        this.treeService = treeService;
        this.serviceManager = serviceManager;
    }

    private UserTable getTable(AkibanInformationSchema ais, String schemaName, String tableName) {
        UserTable table = ais.getUserTable(schemaName, tableName);
        if (table == null) {
            // TODO: Consider sending in-band as JSON.
            throw new NoSuchTableException(schemaName, tableName);
        }
        return table;
    }

    private StoreAdapter getAdapter(Session session, UserTable table, Schema schema) {
        if (table.hasMemoryTableFactory())
            return new MemoryAdapter(schema, session, configService);
        StoreAdapter adapter = session.get(StoreAdapter.STORE_ADAPTER_KEY);
        if (adapter == null)
            adapter = new PersistitAdapter(schema, store, treeService, session, configService);
        return adapter;
    }

    private void dumpAsJson(Session session,
                            PrintWriter writer,
                            UserTable table,
                            List<List<String>> keys,
                            int depth,
                            boolean withTransaction,
                            Schema schema,
                            Operator plan) {
        StoreAdapter adapter = getAdapter(session, table, schema);
        QueryContext queryContext = new SimpleQueryContext(adapter) {
                @Override
                public ServiceManager getServiceManager() {
                    return serviceManager;
                }
            };
        JsonRowWriter json = new JsonRowWriter(new TableRowTracker(table, depth));
        WriteTableRow rowWriter = new WriteTableRow();
        AkibanAppender appender = AkibanAppender.of(writer);
        boolean transaction = false;
        Cursor cursor = null;
        try {
            if (withTransaction) {
                transactionService.beginTransaction(session);
                transaction = true;
            }
            cursor = API.cursor(plan, queryContext);
            appender.append("[");
            boolean begun = false;

            if (keys == null) {
                begun = json.writeRows(cursor, appender, "\n", rowWriter);
            } else {
                PValue pvalue = new PValue(MString.VARCHAR.instance(Integer.MAX_VALUE, false));
                for (List<String> key : keys) {
                    for (int i = 0; i < key.size(); i++) {
                        String akey = key.get(i);
                        pvalue.putString(akey, null);
                        queryContext.setPValue(i, pvalue);
                    }
                    if (json.writeRows(cursor, appender, begun ? ",\n" : "\n", rowWriter))
                        begun = true;
                }
            }

            appender.append(begun ? "\n]" : "]");
            if (withTransaction) {
                transactionService.commitTransaction(session);
                transaction = false;
            }
        }
        finally {
            if (cursor != null)
                cursor.destroy();
            if (transaction)
                transactionService.rollbackTransaction(session);
        }
    }

    /* ExternalDataService */

    @Override
    public void dumpAllAsJson(Session session, PrintWriter writer,
                              String schemaName, String tableName,
                              int depth, boolean withTransaction) {
        AkibanInformationSchema ais = dxlService.ddlFunctions().getAIS(session);
        UserTable table = getTable(ais, schemaName, tableName);
        logger.debug("Writing all of {}", table);
        PlanGenerator generator = ais.getCachedValue(this, CACHED_PLAN_GENERATOR);
        Operator plan = generator.generateScanPlan(table);
        dumpAsJson(session, writer, table, null, depth, withTransaction, generator.getSchema(), plan);
    }

    @Override
    public void dumpBranchAsJson(Session session, PrintWriter writer,
                                 String schemaName, String tableName, 
                                 List<List<String>> keys, int depth,
                                 boolean withTransaction) {
        AkibanInformationSchema ais = dxlService.ddlFunctions().getAIS(session);
        UserTable table = getTable(ais, schemaName, tableName);
        logger.debug("Writing from {}: {}", table, keys);
        PlanGenerator generator = ais.getCachedValue(this, CACHED_PLAN_GENERATOR);
        Operator plan = generator.generateBranchPlan(table);
        dumpAsJson(session, writer, table, keys, depth, withTransaction, generator.getSchema(), plan);
    }

    @Override
    public void dumpBranchAsJson(Session session, PrintWriter writer,
                                 String schemaName, String tableName, 
                                 Operator scan, RowType scanType, int depth,
                                 boolean withTransaction) {
        AkibanInformationSchema ais = dxlService.ddlFunctions().getAIS(session);
        UserTable table = getTable(ais, schemaName, tableName);
        logger.debug("Writing from {}: {}", table, scan);
        PlanGenerator generator = ais.getCachedValue(this, CACHED_PLAN_GENERATOR);
        Operator plan = generator.generateBranchPlan(table, scan, scanType);
        dumpAsJson(session, writer, table, Collections.singletonList(Collections.<String>emptyList()), depth, withTransaction, generator.getSchema(), plan);
    }

    @Override
    public long loadTableFromCsv(Session session, InputStream inputStream, 
                                 CsvFormat format, long skipRows,
                                 UserTable toTable, List<Column> toColumns,
                                 long commitFrequency, QueryContext context) 
            throws IOException {
        CsvRowReader reader = new CsvRowReader(toTable, toColumns, inputStream, format,
                                               context);
        if (skipRows > 0)
            reader.skipRows(skipRows);
        return loadTableFromRowReader(session, inputStream, reader, commitFrequency);
    }

    @Override
    public long loadTableFromMysqlDump(Session session, InputStream inputStream, 
                                       String encoding,
                                       UserTable toTable, List<Column> toColumns,
                                       long commitFrequency, QueryContext context) 
            throws IOException {
        MysqlDumpRowReader reader = new MysqlDumpRowReader(toTable, toColumns,
                                                           inputStream, encoding, 
                                                           context);
        return loadTableFromRowReader(session, inputStream, reader, commitFrequency);
    }

    protected long loadTableFromRowReader(Session session, InputStream inputStream, 
                                          RowReader reader, long commitFrequency)
            throws IOException {
        DMLFunctions dml = dxlService.dmlFunctions();
        boolean bulkload = store.isBulkloading();
        long pending = 0, total = 0;
        boolean transaction = false;
        try {
            NewRow row;
            do {
                row = reader.nextRow();
                if (row != null) {
                    logger.trace("Read row: {}", row);
                    if (!transaction && !bulkload) {
                        transactionService.beginTransaction(session);
                        transaction = true;
                    }
                    dml.writeRow(session, row);
                    total++;
                    pending++;
                }
                if ((row == null) ? transaction : (pending >= commitFrequency)) {
                    logger.debug("Committing {} rows", pending);
                    transactionService.commitTransaction(session);
                    transaction = false;
                    pending = 0;
                }
            } while (row != null);
        }
        finally {
            if (transaction)
                transactionService.rollbackTransaction(session);
        }
        return total;
    }

    /* Service */
    
    @Override
    public void start() {
    }

    @Override
    public void stop() {
    }

    @Override
    public void crash() {
    }

}