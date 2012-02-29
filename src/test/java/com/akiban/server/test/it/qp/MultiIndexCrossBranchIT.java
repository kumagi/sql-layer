/**
 * Copyright (C) 2011 Akiban Technologies Inc.
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see http://www.gnu.org/licenses.
 */

package com.akiban.server.test.it.qp;

import com.akiban.qp.expression.IndexBound;
import com.akiban.qp.expression.IndexKeyRange;
import com.akiban.qp.operator.API;
import com.akiban.qp.operator.Operator;
import com.akiban.qp.row.RowBase;
import com.akiban.qp.rowtype.IndexRowType;
import com.akiban.qp.rowtype.RowType;
import com.akiban.qp.rowtype.Schema;
import com.akiban.qp.rowtype.UserTableRowType;
import com.akiban.server.api.dml.SetColumnSelector;
import com.akiban.server.api.dml.scan.NewRow;
import com.akiban.server.expression.Expression;
import org.junit.Before;
import org.junit.Test;

import static com.akiban.qp.operator.API.*;
import static com.akiban.qp.operator.API.indexScan_Default;
import static com.akiban.qp.operator.API.IntersectOutputOption.*;
import static com.akiban.server.expression.std.Expressions.field;

public class MultiIndexCrossBranchIT extends OperatorITBase
{
    @Before
    public void before()
    {
        p = createTable(
            "schema", "p",
            "pid int not null key",
            "x int",
            "index(x)");
        c = createTable(
            "schema", "c",
            "cid int not null key",
            "pid int",
            "y int",
            "index(y)",
            "constraint __akiban_cp foreign key __akiban_cp(pid) references p(pid)");
        d = createTable(
            "schema", "d",
            "did int not null key",
            "pid int",
            "z int",
            "index(z)",
            "constraint __akiban_dp foreign key __akiban_dp(pid) references p(pid)");
        schema = new Schema(rowDefCache().ais());
        pRowType = schema.userTableRowType(userTable(p));
        cRowType = schema.userTableRowType(userTable(c));
        dRowType = schema.userTableRowType(userTable(d));
        pXIndexRowType = indexType(p, "x");
        cYIndexRowType = indexType(c, "y");
        dZIndexRowType = indexType(d, "z");
        hKeyRowType = schema.newHKeyRowType(pRowType.userTable().hKey());
        coi = groupTable(p);
        adapter = persistitAdapter(schema);
        queryContext = queryContext(adapter);
        db = new NewRow[]{
            // 0x: Both sides empty
            // 1x: C empty
            createNewRow(p, 10L, 1L),
            createNewRow(d, 1000L, 10L, 1L),
            createNewRow(d, 1001L, 10L, 1L),
            createNewRow(d, 1002L, 10L, 1L),
            // 2x: D empty
            createNewRow(p, 20L, 2L),
            createNewRow(c, 2000L, 20L, 2L),
            createNewRow(c, 2001L, 20L, 2L),
            createNewRow(c, 2002L, 20L, 2L),
            // 3x: C, D non-empty
            createNewRow(p, 30L, 3L),
            createNewRow(c, 3000L, 30L, 3L),
            createNewRow(c, 3001L, 30L, 3L),
            createNewRow(c, 3002L, 30L, 3L),
            createNewRow(d, 3000L, 30L, 3L),
            createNewRow(d, 3001L, 30L, 3L),
            createNewRow(d, 3002L, 30L, 3L),
        };
        use(db);
    }

    @Test
    public void test0xAND()
    {
        Operator plan = intersectCyDz(0, OUTPUT_LEFT);
        RowBase[] expected = new RowBase[]{
        };
        compareRows(expected, cursor(plan, queryContext));
        plan = intersectCyDz(0, OUTPUT_RIGHT);
        expected = new RowBase[]{
        };
        compareRows(expected, cursor(plan, queryContext));
    }

    @Test
    public void test1xAND()
    {
        Operator plan = intersectCyDz(1, OUTPUT_LEFT);
        RowBase[] expected = new RowBase[]{
        };
        compareRows(expected, cursor(plan, queryContext));
        plan = intersectCyDz(1, OUTPUT_RIGHT);
        expected = new RowBase[]{
        };
        compareRows(expected, cursor(plan, queryContext));
    }

    @Test
    public void test2xAND()
    {
        Operator plan = intersectCyDz(2, OUTPUT_LEFT);
        RowBase[] expected = new RowBase[]{
        };
        compareRows(expected, cursor(plan, queryContext));
        plan = intersectCyDz(2, OUTPUT_RIGHT);
        expected = new RowBase[]{
        };
        compareRows(expected, cursor(plan, queryContext));
    }

    @Test
    public void test3xAND()
    {
        Operator plan = intersectCyDz(3, OUTPUT_LEFT);
        RowBase[] expected = new RowBase[]{
            row(cRowType, 3L, 30L, 3000L),
            row(cRowType, 3L, 30L, 3001L),
            row(cRowType, 3L, 30L, 3002L),
        };
        compareRows(expected, cursor(plan, queryContext));
        plan = intersectCyDz(3, OUTPUT_RIGHT);
        expected = new RowBase[]{
            row(dRowType, 3L, 30L, 3000L),
            row(dRowType, 3L, 30L, 3001L),
            row(dRowType, 3L, 30L, 3002L),
        };
        compareRows(expected, cursor(plan, queryContext));
    }

    @Test
    public void test0xOR()
    {
        Operator plan = unionCyDz(0);
        String[] expected = new String[]{
        };
        compareRenderedHKeys(expected, cursor(plan, queryContext));
    }

    @Test
    public void test1xOR()
    {
        Operator plan = unionCyDz(1);
        String[] expected = new String[]{
            pKey(10L),
        };
        compareRenderedHKeys(expected, cursor(plan, queryContext));
    }

    @Test
    public void test2xOR()
    {
        Operator plan = unionCyDz(2);
        String[] expected = new String[]{
            pKey(20L),
        };
        compareRenderedHKeys(expected, cursor(plan, queryContext));
    }

    @Test
    public void test3xOR()
    {
        Operator plan = unionCyDz(3);
        String[] expected = new String[]{
            pKey(30L),
        };
        compareRenderedHKeys(expected, cursor(plan, queryContext));
    }

    private Operator intersectCyDz(int key, IntersectOutputOption side)
    {
        Operator plan =
            intersect_Ordered(
                indexScan_Default(
                    cYIndexRowType,
                    cYEQ(key),
                    ordering(field(cYIndexRowType, 1), true, 
                             field(cYIndexRowType, 2), true)),
                indexScan_Default(
                    dZIndexRowType,
                    dZEQ(key),
                    ordering(field(dZIndexRowType, 1), true,
                             field(dZIndexRowType, 2), true)),
                cYIndexRowType,
                dZIndexRowType,
                2,
                2,
                1,
                JoinType.INNER_JOIN,
                side);
        return plan;
    }

    private Operator unionCyDz(int key)
    {
        Operator plan =
            hKeyUnion_Ordered(
                indexScan_Default(
                    cYIndexRowType,
                    cYEQ(key),
                    ordering(field(cYIndexRowType, 1), true,
                             field(cYIndexRowType, 2), true)),
                indexScan_Default(
                    dZIndexRowType,
                    dZEQ(key),
                    ordering(field(dZIndexRowType, 1), true,
                             field(dZIndexRowType, 2), true)),
                cYIndexRowType,
                dZIndexRowType,
                2,
                2,
                1,
                pRowType);
        return plan;
    }

    private IndexKeyRange cYEQ(long y)
    {
        IndexBound yBound = new IndexBound(row(cYIndexRowType, y), new SetColumnSelector(0));
        return IndexKeyRange.bounded(cYIndexRowType, yBound, true, yBound, true);
    }

    private IndexKeyRange dZEQ(long z)
    {
        IndexBound zBound = new IndexBound(row(dZIndexRowType, z), new SetColumnSelector(0));
        return IndexKeyRange.bounded(dZIndexRowType, zBound, true, zBound, true);
    }

    private Ordering ordering(Object... objects)
    {
        Ordering ordering = API.ordering();
        int i = 0;
        while (i < objects.length) {
            Expression expression = (Expression) objects[i++];
            Boolean ascending = (Boolean) objects[i++];
            ordering.append(expression, ascending);
        }
        return ordering;
    }

    private String pKey(Long pid)
    {
        return String.format("{%d,%s}", p, hKeyValue(pid));
    }

    private int p;
    private int c;
    private int d;
    private UserTableRowType pRowType;
    private UserTableRowType cRowType;
    private UserTableRowType dRowType;
    private IndexRowType pXIndexRowType;
    private IndexRowType cYIndexRowType;
    private IndexRowType dZIndexRowType;
    private RowType hKeyRowType;
}