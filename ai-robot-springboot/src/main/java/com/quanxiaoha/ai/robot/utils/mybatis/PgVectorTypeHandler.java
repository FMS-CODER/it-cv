package com.quanxiaoha.ai.robot.utils.mybatis;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.postgresql.util.PGobject;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * PostgreSQL pgvector 类型处理器。
 * 说明：若将 Java String 直接绑定到 vector 列，JDBC 会按 varchar 传参，导致
 * "column embedding is of type vector but expression is of type character varying"；
 * 通过 PGobject 指定类型为 vector，与 JsonbTypeHandler 同理。
 */
public class PgVectorTypeHandler extends BaseTypeHandler<String> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, String parameter, JdbcType jdbcType) throws SQLException {
        PGobject pg = new PGobject();
        pg.setType("vector");
        pg.setValue(parameter);
        ps.setObject(i, pg);
    }

    @Override
    public String getNullableResult(ResultSet rs, String columnName) throws SQLException {
        Object obj = rs.getObject(columnName);
        return obj == null ? null : obj.toString();
    }

    @Override
    public String getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        Object obj = rs.getObject(columnIndex);
        return obj == null ? null : obj.toString();
    }

    @Override
    public String getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        Object obj = cs.getObject(columnIndex);
        return obj == null ? null : obj.toString();
    }
}
