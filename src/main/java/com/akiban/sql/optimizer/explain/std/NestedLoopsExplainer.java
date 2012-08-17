/**
 * END USER LICENSE AGREEMENT (“EULA”)
 *
 * READ THIS AGREEMENT CAREFULLY (date: 9/13/2011):
 * http://www.akiban.com/licensing/20110913
 *
 * BY INSTALLING OR USING ALL OR ANY PORTION OF THE SOFTWARE, YOU ARE ACCEPTING
 * ALL OF THE TERMS AND CONDITIONS OF THIS AGREEMENT. YOU AGREE THAT THIS
 * AGREEMENT IS ENFORCEABLE LIKE ANY WRITTEN AGREEMENT SIGNED BY YOU.
 *
 * IF YOU HAVE PAID A LICENSE FEE FOR USE OF THE SOFTWARE AND DO NOT AGREE TO
 * THESE TERMS, YOU MAY RETURN THE SOFTWARE FOR A FULL REFUND PROVIDED YOU (A) DO
 * NOT USE THE SOFTWARE AND (B) RETURN THE SOFTWARE WITHIN THIRTY (30) DAYS OF
 * YOUR INITIAL PURCHASE.
 *
 * IF YOU WISH TO USE THE SOFTWARE AS AN EMPLOYEE, CONTRACTOR, OR AGENT OF A
 * CORPORATION, PARTNERSHIP OR SIMILAR ENTITY, THEN YOU MUST BE AUTHORIZED TO SIGN
 * FOR AND BIND THE ENTITY IN ORDER TO ACCEPT THE TERMS OF THIS AGREEMENT. THE
 * LICENSES GRANTED UNDER THIS AGREEMENT ARE EXPRESSLY CONDITIONED UPON ACCEPTANCE
 * BY SUCH AUTHORIZED PERSONNEL.
 *
 * IF YOU HAVE ENTERED INTO A SEPARATE WRITTEN LICENSE AGREEMENT WITH AKIBAN FOR
 * USE OF THE SOFTWARE, THE TERMS AND CONDITIONS OF SUCH OTHER AGREEMENT SHALL
 * PREVAIL OVER ANY CONFLICTING TERMS OR CONDITIONS IN THIS AGREEMENT.
 */
package com.akiban.sql.optimizer.explain.std;

import com.akiban.qp.exec.Plannable;
import com.akiban.qp.operator.Operator;
import com.akiban.qp.rowtype.RowType;
import com.akiban.sql.optimizer.explain.*;
import java.util.Map;


public class NestedLoopsExplainer extends OperationExplainer
{
    public NestedLoopsExplainer (String name, Operator innerOp, Operator outerOp, RowType innerType, RowType outerType, Map<Object, Explainer> extraInfo)
    {
        super(Type.NESTED_LOOPS, buildMap(name, innerOp, outerOp, innerType, outerType, extraInfo));
    }
    
    private static Attributes buildMap (String name, Operator innerOp, Operator outerOp, RowType innerType, RowType outerType, Map<Object, Explainer> extraInfo)
    {
        Attributes atts = new Attributes();
        
        atts.put(Label.NAME, PrimitiveExplainer.getInstance(name));
        atts.put(Label.INPUT_OPERATOR, outerOp.getExplainer(extraInfo));
        atts.put(Label.INPUT_OPERATOR, innerOp.getExplainer(extraInfo));
        if (innerType != null)
            atts.put(Label.INNER_TYPE, innerType.getExplainer(extraInfo));
        if (outerType != null)
            atts.put(Label.OUTER_TYPE, outerType.getExplainer(extraInfo));
        
        return atts;
    }
}
