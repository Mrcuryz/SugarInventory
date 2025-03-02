package com.Laibin.SugarInventory.util;

import com.Laibin.SugarInventory.domain.po.Coordinates;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;
import org.apache.ibatis.type.TypeHandler;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

@MappedTypes(Coordinates.class)
@MappedJdbcTypes(JdbcType.VARCHAR)
public class JsonTypeHandler implements TypeHandler<Coordinates> {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void setParameter(PreparedStatement ps, int i, Coordinates parameter, JdbcType jdbcType) throws SQLException {
        if (parameter != null) {
            try {
                ps.setString(i, objectMapper.writeValueAsString(parameter));
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        } else {
            ps.setString(i, null);
        }
    }

    @Override
    public Coordinates getResult(ResultSet rs, String columnName) throws SQLException {
        String json = rs.getString(columnName);
        try {
            return objectMapper.readValue(json, Coordinates.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Coordinates getResult(ResultSet rs, int columnIndex) throws SQLException {
        String json = rs.getString(columnIndex);
        try {
            return objectMapper.readValue(json, Coordinates.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Coordinates getResult(CallableStatement cs, int columnIndex) throws SQLException {
        String json = cs.getString(columnIndex);
        try {
            return objectMapper.readValue(json, Coordinates.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
