package com.example.workfloworchestrator.engine.executor;

import com.example.workfloworchestrator.exception.TaskExecutionException;
import com.example.workfloworchestrator.model.ExecutionContext;
import com.example.workfloworchestrator.model.TaskDefinition;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Task executor for database operations
 * Supports SELECT, INSERT, UPDATE, DELETE operations
 */
@Slf4j
@Component
public class DatabaseTaskExecutor extends AbstractTaskExecutor {
    
    private static final String TASK_TYPE = "database";
    
    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    
    // Pattern to identify named parameters in SQL
    private static final Pattern NAMED_PARAMETER_PATTERN = Pattern.compile(":(\\w+)");
    
    // Pattern to identify positional parameters in SQL
    private static final Pattern POSITIONAL_PARAMETER_PATTERN = Pattern.compile("\\?");
    
    public DatabaseTaskExecutor(DataSource dataSource, JdbcTemplate jdbcTemplate) {
        this.dataSource = dataSource;
        this.jdbcTemplate = jdbcTemplate;
        this.namedParameterJdbcTemplate = new NamedParameterJdbcTemplate(dataSource);
    }
    
    @Override
    public String getTaskType() {
        return TASK_TYPE;
    }
    
    @Override
    protected void validateTaskConfig(TaskDefinition taskDefinition) {
        validateTaskConfig(taskDefinition, "query");
    }
    
    @Override
    protected Map<String, Object> doExecute(TaskDefinition taskDefinition, ExecutionContext context) throws Exception {
        Map<String, String> config = processConfigVariables(taskDefinition.getConfiguration(), context);
        
        // Get required configuration
        String query = getRequiredConfig(config, "query");
        
        // Optional configuration
        String connectionName = config.get("connectionName"); // For multiple datasource support
        boolean returnGeneratedKeys = Boolean.parseBoolean(config.getOrDefault("returnGeneratedKeys", "false"));
        String generatedKeyColumn = config.get("generatedKeyColumn");
        
        // Determine query type
        String queryType = determineQueryType(query);
        
        // Execute query based on type
        Map<String, Object> result = executeQuery(query, queryType, context, returnGeneratedKeys, generatedKeyColumn);
        
        return result;
    }
    
    private String determineQueryType(String query) {
        String normalizedQuery = query.trim().toUpperCase();
        
        if (normalizedQuery.startsWith("SELECT")) {
            return "SELECT";
        } else if (normalizedQuery.startsWith("INSERT")) {
            return "INSERT";
        } else if (normalizedQuery.startsWith("UPDATE")) {
            return "UPDATE";
        } else if (normalizedQuery.startsWith("DELETE")) {
            return "DELETE";
        } else {
            return "UNKNOWN";
        }
    }
    
    private Map<String, Object> executeQuery(String query, String queryType, ExecutionContext context,
                                            boolean returnGeneratedKeys, String generatedKeyColumn) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            if ("SELECT".equals(queryType)) {
                // For SELECT queries, return the results
                List<Map<String, Object>> rows = executeSelectQuery(query, context);
                result.put("rows", rows);
                result.put("rowCount", rows.size());
            } else {
                // For non-SELECT queries, execute update and get affected rows
                int affectedRows;
                
                if (returnGeneratedKeys) {
                    // Execute with generated keys
                    Map<String, Object> updateResult = executeUpdateWithGeneratedKeys(query, context, generatedKeyColumn);
                    affectedRows = (int) updateResult.get("affectedRows");
                    result.put("generatedKeys", updateResult.get("generatedKeys"));
                } else {
                    // Execute without generated keys
                    affectedRows = executeUpdateQuery(query, context);
                }
                
                result.put("affectedRows", affectedRows);
            }
            
            result.put("success", true);
            result.put("queryType", queryType);
            
        } catch (Exception e) {
            log.error("Error executing database query: {}", query, e);
            result.put("success", false);
            result.put("error", e.getMessage());
            throw new TaskExecutionException("Error executing database query: " + e.getMessage(), e);
        }
        
        return result;
    }
    
    private List<Map<String, Object>> executeSelectQuery(String query, ExecutionContext context) {
        if (hasNamedParameters(query)) {
            // Use named parameters
            MapSqlParameterSource paramSource = createNamedParameters(query, context);
            return namedParameterJdbcTemplate.queryForList(query, paramSource);
        } else if (hasPositionalParameters(query)) {
            // Use positional parameters
            Object[] params = extractPositionalParams(context);
            return jdbcTemplate.queryForList(query, params);
        } else {
            // No parameters
            return jdbcTemplate.queryForList(query);
        }
    }
    
    private int executeUpdateQuery(String query, ExecutionContext context) {
        if (hasNamedParameters(query)) {
            // Use named parameters
            MapSqlParameterSource paramSource = createNamedParameters(query, context);
            return namedParameterJdbcTemplate.update(query, paramSource);
        } else if (hasPositionalParameters(query)) {
            // Use positional parameters
            Object[] params = extractPositionalParams(context);
            return jdbcTemplate.update(query, params);
        } else {
            // No parameters
            return jdbcTemplate.update(query);
        }
    }
    
    private Map<String, Object> executeUpdateWithGeneratedKeys(String query, ExecutionContext context, String generatedKeyColumn) {
        Map<String, Object> result = new HashMap<>();
        KeyHolder keyHolder = new GeneratedKeyHolder();
        int affectedRows;
        
        if (hasNamedParameters(query)) {
            // Use named parameters with KeyHolder
            MapSqlParameterSource paramSource = createNamedParameters(query, context);
            affectedRows = namedParameterJdbcTemplate.update(query, paramSource, keyHolder, 
                    generatedKeyColumn != null ? new String[]{generatedKeyColumn} : null);
        } else {
            // Use positional parameters with KeyHolder
            Object[] params = extractPositionalParams(context);
            affectedRows = executeUpdateWithKeyHolder(query, params, keyHolder, generatedKeyColumn);
        }
        
        List<Map<String, Object>> generatedKeys = new ArrayList<>();
        if (keyHolder.getKeys() != null) {
            if (keyHolder.getKeys().size() > 1) {
                // Multiple keys generated
                generatedKeys.add(keyHolder.getKeys());
            } else if (!keyHolder.getKeys().isEmpty()) {
                // Single key, get the value
                Object key = keyHolder.getKeys().values().iterator().next();
                Map<String, Object> keyMap = new HashMap<>();
                keyMap.put(generatedKeyColumn != null ? generatedKeyColumn : "GENERATED_KEY", key);
                generatedKeys.add(keyMap);
            }
        } else if (keyHolder.getKeyList() != null && !keyHolder.getKeyList().isEmpty()) {
            // Multiple rows, multiple keys
            generatedKeys.addAll(keyHolder.getKeyList());
        }
        
        result.put("affectedRows", affectedRows);
        result.put("generatedKeys", generatedKeys);
        
        return result;
    }
    
    private int executeUpdateWithKeyHolder(String query, Object[] params, KeyHolder keyHolder, String generatedKeyColumn) {
        // We need to use raw JDBC for positional parameters with KeyHolder
        Connection con = DataSourceUtils.getConnection(dataSource);
        try {
            PreparedStatement ps = generatedKeyColumn != null ?
                    con.prepareStatement(query, new String[]{generatedKeyColumn}) :
                    con.prepareStatement(query, Statement.RETURN_GENERATED_KEYS);
            
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            
            int rows = ps.executeUpdate();
            
            ResultSet generatedKeys = ps.getGeneratedKeys();
            if (generatedKeys.next()) {
                Map<String, Object> keyMap = new HashMap<>();
                ResultSetMetaData metaData = generatedKeys.getMetaData();
                for (int i = 1; i <= metaData.getColumnCount(); i++) {
                    String columnName = metaData.getColumnName(i);
                    Object columnValue = generatedKeys.getObject(i);
                    keyMap.put(columnName, columnValue);
                }
                keyHolder.getKeyList().add(keyMap);
            }
            
            return rows;
        } catch (SQLException e) {
            throw new TaskExecutionException("Error executing update with generated keys", e);
        } finally {
            DataSourceUtils.releaseConnection(con, dataSource);
        }
    }
    
    private boolean hasNamedParameters(String query) {
        Matcher matcher = NAMED_PARAMETER_PATTERN.matcher(query);
        return matcher.find();
    }
    
    private boolean hasPositionalParameters(String query) {
        Matcher matcher = POSITIONAL_PARAMETER_PATTERN.matcher(query);
        return matcher.find();
    }
    
    private MapSqlParameterSource createNamedParameters(String query, ExecutionContext context) {
        MapSqlParameterSource paramSource = new MapSqlParameterSource();
        
        // Extract all named parameters from the query
        Matcher matcher = NAMED_PARAMETER_PATTERN.matcher(query);
        while (matcher.find()) {
            String paramName = matcher.group(1);
            if (context.hasVariable(paramName)) {
                paramSource.addValue(paramName, context.getVariable(paramName));
            } else {
                log.warn("Named parameter :{} not found in context", paramName);
            }
        }
        
        return paramSource;
    }
    
    private Object[] extractPositionalParams(ExecutionContext context) {
        // Two options for handling positional parameters:
        // 1. Look for param_0, param_1, etc. in the context
        // 2. Look for a params array in the context
        
        // Check for params array first
        if (context.hasVariable("params") && context.getVariable("params") instanceof List) {
            List<?> paramsList = context.getVariable("params", List.class);
            return paramsList.toArray();
        }
        
        // Otherwise look for param_0, param_1, etc.
        List<Object> params = new ArrayList<>();
        int index = 0;
        
        while (true) {
            String paramKey = "param_" + index;
            if (context.hasVariable(paramKey)) {
                params.add(context.getVariable(paramKey));
                index++;
            } else {
                break;
            }
        }
        
        return params.toArray();
    }
    
    @Override
    protected Map<String, Object> postProcessResult(Map<String, Object> result, ExecutionContext context) {
        // Add execution timestamp
        result.put("executionTimestamp", System.currentTimeMillis());
        
        // Store query result in context for future tasks
        if (result.containsKey("rows")) {
            context.setVariable("queryResult", result.get("rows"));
        }
        
        if (result.containsKey("generatedKeys")) {
            context.setVariable("generatedKeys", result.get("generatedKeys"));
        }
        
        // Log execution summary
        String queryType = (String) result.get("queryType");
        Boolean success = (Boolean) result.get("success");
        
        if (queryType != null && success != null && success) {
            if ("SELECT".equals(queryType)) {
                int rowCount = (int) result.get("rowCount");
                log.info("Database query executed successfully. Retrieved {} rows", rowCount);
            } else {
                int affectedRows = (int) result.get("affectedRows");
                log.info("Database {} executed successfully. Affected {} rows", queryType, affectedRows);
            }
        }
        
        return result;
    }
}
