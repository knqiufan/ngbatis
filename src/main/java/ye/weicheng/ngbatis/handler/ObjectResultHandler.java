// Copyright 2022-present Weicheng Ye. All rights reserved.
// Use of this source code is governed by a MIT-style license that can be
// found in the LICENSE file.
package ye.weicheng.ngbatis.handler;

import com.vesoft.nebula.client.graph.data.Node;
import com.vesoft.nebula.client.graph.data.ResultSet;
import com.vesoft.nebula.client.graph.data.ValueWrapper;
import org.springframework.stereotype.Component;
import ye.weicheng.ngbatis.utils.ReflectUtil;
import ye.weicheng.ngbatis.utils.ResultSetUtil;

import java.util.List;

import static ye.weicheng.ngbatis.utils.ResultSetUtil.nodeToResultType;

/**
 * 结果集数据类型转换器
 * <p> ResultSet -> Object </p>
 *
 * @author yeweicheng
 * @since 2022-06-10 22:53
 * <br>Now is history!
 */
@Component
public class ObjectResultHandler extends AbstractResultHandler<Object, Object> {
    @Override
    public Object handle(Object newResult, ResultSet result, Class resultType) throws NoSuchFieldException, IllegalAccessException {
        if(result.rowsSize() == 0) return null;
        List<String> columnNames = result.getColumnNames();
        ResultSet.Record record = result.rowValues(0);
        for (int i = 0; i < columnNames.size(); i++) {
            ValueWrapper valueWrapper = record.values().get( i );
            Object v = ResultSetUtil.getValue( valueWrapper );
            if ( columnNames.size() == 1 && v instanceof Node ) {
                return nodeToResultType( (Node)v, resultType );
            }
            ReflectUtil.setValue( newResult, columnNames.get( i ), v);
        }
        return newResult;
    }
}
