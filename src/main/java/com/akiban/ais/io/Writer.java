/* <GENERIC-HEADER - BEGIN>
 *
 * $(COMPANY) $(COPYRIGHT)
 *
 * Created on: Nov, 20, 2009
 * Created by: Thomas Hazel
 *
 * </GENERIC-HEADER - END> */

package com.akiban.ais.io;

import java.util.Collection;

import com.akiban.ais.model.AkibaInformationSchema;
import com.akiban.ais.model.Column;
import com.akiban.ais.model.Group;
import com.akiban.ais.model.GroupTable;
import com.akiban.ais.model.Index;
import com.akiban.ais.model.IndexColumn;
import com.akiban.ais.model.Join;
import com.akiban.ais.model.JoinColumn;
import com.akiban.ais.model.ModelNames;
import com.akiban.ais.model.Table;
import com.akiban.ais.model.Target;
import com.akiban.ais.model.Type;
import com.akiban.ais.model.UserTable;

public class Writer implements ModelNames
{
    private Target target;

    public Writer(Target target)
    {
        this.target = target;
    }

    private void saveTypes(AkibaInformationSchema ais) throws Exception
    {
        Collection<Type> types = ais.getTypes();
        target.writeCount(types.size());
        for (Type type : types) {
            target.writeType(type.map());
        }
    }

    private void saveGroups(AkibaInformationSchema ais) throws Exception
    {
        target.writeCount(ais.getGroups().size());
        for (Group group : ais.getGroups().values()) {
            target.writeGroup(group.map());
        }
    }

    private void saveTables(AkibaInformationSchema ais) throws Exception
    {
        target.writeCount(ais.getGroupTables().size() + ais.getUserTables().size());
        for (GroupTable groupTable : ais.getGroupTables().values()) {
            target.writeTable(groupTable.map());
            UserTable root = groupTable.getRoot();
            assert root != null : groupTable;
            nColumns += groupTable.getColumns().size();
            nIndexes += groupTable.getIndexes().size();
        }
        for (UserTable userTable : ais.getUserTables().values()) {
            target.writeTable(userTable.map());
            nColumns += userTable.getColumns().size();
            nIndexes += userTable.getIndexes().size();
        }
    }

    private void saveColumns(AkibaInformationSchema ais) throws Exception
    {
        target.writeCount(nColumns);
        for (GroupTable groupTable : ais.getGroupTables().values()) {
            saveColumns(groupTable);
        }
        for (UserTable userTable : ais.getUserTables().values()) {
            saveColumns(userTable);
        }
    }

    private void saveColumns(Table table) throws Exception
    {
        for (Column column : table.getColumns()) {
            target.writeColumn(column.map());
        }
    }

    private void saveJoins(AkibaInformationSchema ais) throws Exception
    {
        target.writeCount(ais.getJoins().size());
        for (Join join : ais.getJoins().values()) {
            target.writeJoin(join.map());
            nJoinColumns += join.getJoinColumns().size();
        }
    }

    private void saveJoinColumns(AkibaInformationSchema ais) throws Exception
    {
        target.writeCount(nJoinColumns);
        for (Join join : ais.getJoins().values()) {
            for (JoinColumn joinColumn : join.getJoinColumns()) {
                target.writeJoinColumn(joinColumn.map());
            }
        }
    }

    private void saveIndexes(AkibaInformationSchema ais) throws Exception
    {
        target.writeCount(nIndexes);
        for (UserTable userTable : ais.getUserTables().values()) {
            for (Index index : userTable.getIndexes()) {
                target.writeIndex(index.map());
                nIndexColumns += index.getColumns().size();
            }
        }
        for (GroupTable groupTable : ais.getGroupTables().values()) {
            for (Index index : groupTable.getIndexes()) {
                target.writeIndex(index.map());
                nIndexColumns += index.getColumns().size();
            }
        }
    }

    private void saveIndexColumns(AkibaInformationSchema ais) throws Exception
    {
        target.writeCount(nIndexColumns);
        for (UserTable userTable : ais.getUserTables().values()) {
            for (Index index : userTable.getIndexes()) {
                for (IndexColumn indexColumn : index.getColumns()) {
                    target.writeIndexColumn(indexColumn.map());
                }
            }
        }
        for (GroupTable groupTable : ais.getGroupTables().values()) {
            for (Index index : groupTable.getIndexes()) {
                for (IndexColumn indexColumn : index.getColumns()) {
                    target.writeIndexColumn(indexColumn.map());
                }
            }
        }
    }

    protected void close() throws Exception
    {
        target.close();
    }

    public void save(AkibaInformationSchema ais) throws Exception
    {
        try {
            target.deleteAll();
            saveTypes(ais);
            saveGroups(ais);
            saveTables(ais);
            saveColumns(ais);
            saveJoins(ais);
            saveJoinColumns(ais);
            saveIndexes(ais);
            saveIndexColumns(ais);
        } finally {
            close();
        }
    }

    private int nColumns = 0;
    private int nJoinColumns = 0;
    private int nIndexes = 0;
    private int nIndexColumns = 0;
}
