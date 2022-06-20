// Copyright 2022-present Weicheng Ye. All rights reserved.
// Use of this source code is governed by a MIT-style license that can be
// found in the LICENSE file.
package ye.weicheng.ngbatis.proxy;

import org.apache.logging.log4j.util.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sun.reflect.generics.reflectiveObjects.ParameterizedTypeImpl;
import ye.weicheng.ngbatis.PkGenerator;
import ye.weicheng.ngbatis.exception.ParseException;
import ye.weicheng.ngbatis.models.MethodModel;
import ye.weicheng.ngbatis.utils.ReflectUtil;
import ye.weicheng.ngbatis.utils.StringUtil;

import javax.persistence.Id;
import javax.persistence.Table;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author yeweicheng
 * @since 2022-06-14 4:25
 * <br>Now is history!
 */
public class NebulaDaoBasicExt {

    private static Logger log = LoggerFactory.getLogger( NebulaDaoBasicExt.class );

    public static String recordToQL(Object record, boolean selective ) {
        Class<?> type = record.getClass();
        String vertexName = vertexName(type);
        StringBuilder builder = new StringBuilder("INSERT VERTEX ");
        builder.append( vertexName );
        String propsWithValues = columnsToQL( record, type, selective,  vertexName );
        builder.append( propsWithValues );
        return builder.toString();
    }

    public static String vertexName( Class<?> entityType ) {
        Table tableAnno = entityType.getAnnotation( Table.class );
        String x_x = StringUtil.xX2x_x(entityType.getName());
        return tableAnno == null ? x_x : tableAnno.name();
    }


    public static String columnsToQL(Object record, Class<?> type, boolean selective, String tagName) {
        Field[] fields = type.getDeclaredFields();
        List<String> columns = new ArrayList<>();
        List<String> valueNames = new ArrayList<>();
        Field pkField = null;
        for (Field field : fields) {
            if (field.isAnnotationPresent(Id.class)) {
                pkField = field;
            }
        }
        if (pkField == null) {
            throw new ParseException( String.format( "%s 必须有一个属性用 @Id 注解。（javax.persistence.Id）", type ));
        }

        Object id = setId( record, pkField, tagName );

        for (Field field: fields) {
            String name = null;
            if( selective ) {
                Object value = ReflectUtil.getValue(record, field);
                if( value != null ) {
                    name = field.getName();
                }
            } else {
                name = field.getName();
            }
            if( name != null ) {
                columns.add( name );
                // FIXME 使用 stmt 的方式，将实际值写入 nGQL 当中。
                //  在找到 executeWithParameter 通过参数替换的方法之后修改成 pstmt 的形式
                Object o = keyFormat( field, name, true);
                valueNames.add( String.valueOf( o ) );
            }
        }

        assert id != null;
        // INSERT VERTEX IF NOT EXISTS  tag [tag_props, [tag_props] ...] VALUES <vid>: ([prop_value_list])
        StringBuilder builder = new StringBuilder( " (  ");
        builder.append( Strings.join( columns, ',' ) );
        builder.append( " ) ");
        builder.append( " VALUES ");
        builder.append( valueFormat( pkField, id ) );
        builder.append( ":");
        builder.append( " ( ");
        builder.append( Strings.join( valueNames, ',' ) );
        builder.append( " ) ");
        return builder.toString();
    }


    public static Object setId(Object record, Field pkField, String tagName) {
        try {
            PkGenerator pkGenerator = MapperProxy.ENV.getPkGenerator();
            Object id = ReflectUtil.getValue( record, pkField );
            if(id == null && pkGenerator != null) {
                id = pkGenerator.generate( tagName, pkField.getType() );
                ReflectUtil.setValue( record, pkField, id );
            }
            return id;
        } catch (IllegalAccessException e) {
            log.error( e.getMessage() );
            return null;
        }
    }

   static  Map<Class<?>, String> valueFormat = new HashMap<Class<?>, String>() {{
        put( String.class , "'%s'");
    }};

    static Object valueFormat( Field field, Object name ) {
        Class<?> fieldType = field.getType();
        return valueFormat.containsKey(fieldType) ?
                String.format( valueFormat.get(fieldType) , name )
                : name;
    }

    static String keyFormat( Field field, String name, boolean asStmt ) {
        String format = asStmt ? "${ %s }" : "$%s";
        return valueFormat(field, String.format( format, name ) ).toString();
    }

    public static Class<?>[] entityTypeAndIdType(Class<?> currentType) {
        Class<?>[] result = null;
        Type[] genericInterfaces = currentType.getGenericInterfaces();
        for (Type genericInterface : genericInterfaces) {
            if( genericInterface.getClass() == ParameterizedTypeImpl.class ) {
                Type[] actualTypeArguments = ((ParameterizedTypeImpl) genericInterface).getActualTypeArguments();
                result = new Class<?>[] {
                        (Class<?>) actualTypeArguments[0], // T {@link NebulaDaoBasic }
                        (Class<?>) actualTypeArguments[1]  // ID {@link NebulaDaoBasic }
                };
            } else if ( genericInterface instanceof Class ){
                result = entityTypeAndIdType( (Class)genericInterface );
            }
        }
        return result;
    }

    public static Object proxy (Class<?> currentType, Class<?> returnType, String nGQL, Class<?>[] argTypes, Object ... args) {
        StackTraceElement stackTraceElement = Thread.currentThread().getStackTrace()[2];
        Method method = null;
        String methodName = stackTraceElement.getMethodName();
        try {
            method = currentType.getMethod( methodName, argTypes );
        } catch (NoSuchMethodException ignored) {}

        MethodModel methodModel = new MethodModel();
        methodModel.setMethod( method );
        methodModel.setResultType( returnType );
        methodModel.setText( nGQL );
        return MapperProxy.invoke( methodModel, args );
    }
}