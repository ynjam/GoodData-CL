package com.gooddata.connector.driver;

import com.gooddata.exception.ModelException;
import com.gooddata.integration.model.Column;
import com.gooddata.integration.model.DLIPart;
import com.gooddata.connector.model.PdmColumn;
import com.gooddata.connector.model.PdmSchema;
import com.gooddata.connector.model.PdmTable;
import com.gooddata.naming.N;
import com.gooddata.util.JdbcUtil;
import com.gooddata.util.StringUtil;
import org.apache.log4j.Logger;
import org.gooddata.connector.driver.AbstractSqlDriver;
import org.gooddata.connector.driver.SqlDriver;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * GoodData Derby SQL driver. Generates the DDL (tables and indexes), DML (transformation SQL) and other
 * SQL statements necessary for the data normalization (lookup generation)
 * @author zd <zd@gooddata.com>
 * @version 1.0
 */
public class DerbySqlDriver extends AbstractSqlDriver implements SqlDriver {
    //TODO: refactor
    private static Logger l = Logger.getLogger(DerbySqlDriver.class);

    /**
     * Default constructor
     */
    public DerbySqlDriver() {
        // autoincrement syntax
        SYNTAX_AUTOINCREMENT = "GENERATED BY DEFAULT AS IDENTITY (START WITH 1, INCREMENT BY 1)";
        SYNTAX_CONCAT_FUNCTION_PREFIX = "";
        SYNTAX_CONCAT_FUNCTION_SUFFIX = "";
        SYNTAX_CONCAT_OPERATOR = " || '" + HASH_SEPARATOR + "' || ";
    }

    /**
     * Executes the system DDL initialization
     * @param c JDBC connection
     * @throws ModelException if there is a problem with the PDM schema (e.g. multiple source or fact tables)
     * @throws SQLException in case of db problems
     */
    public void executeSystemDdlSql(Connection c) throws ModelException, SQLException {
        super.executeSystemDdlSql(c);
        createFunctions(c);
    }

    
    /**
     * Executes the Derby SQL that extracts the data from a CSV file to the normalization database
     * @param c JDBC connection
     * @param schema the PDM schema
     * @throws ModelException in case when there is a problem with the PDM model
     * @throws SQLException in case of db problems
     */
    public void executeExtractSql(Connection c, PdmSchema schema, String file) throws ModelException, SQLException {
        PdmTable sourceTable = schema.getSourceTable();
        String source = sourceTable.getName();
        String cols = getNonAutoincrementColumns(sourceTable);
        JdbcUtil.executeUpdate(c,
            "CALL SYSCS_UTIL.SYSCS_IMPORT_DATA " +
            "(NULL, '" + source.toUpperCase() + "', '" + cols.toUpperCase() +
            "', null, '" + file + "', null, null, 'utf-8',0)"
        );
        
    }

    /**
     * Executes the Derby SQL that unloads the data from the normalization database to a CSV
     * @param c JDBC connection
     * @param part DLI part
     * @param dir target directory
     * @param snapshotIds specific snapshots IDs that will be integrated
     * @throws SQLException in case of db problems 
     */
    public void executeLoadSql(Connection c, PdmSchema schema, DLIPart part, String dir, int[] snapshotIds)
            throws ModelException, SQLException {
        String file = dir + System.getProperty("file.separator") + part.getFileName();
        String cols = getLoadColumns(part, schema);
        String whereClause = getLoadWhereClause(part, schema, snapshotIds);
        String dliTable = getTableNameFromPart(part);
        JdbcUtil.executeUpdate(c,
            "CALL SYSCS_UTIL.SYSCS_EXPORT_QUERY " +
            "('SELECT " + cols + " FROM " + dliTable.toUpperCase() + whereClause + "', '" + file +
            "', null, null, 'utf-8')"
        );
    }

    private void createFunctions(Connection c) throws SQLException {
        JdbcUtil.executeUpdate(c,
            "CREATE FUNCTION ATOD(str VARCHAR(255)) RETURNS DOUBLE\n" +
            " PARAMETER STYLE JAVA NO SQL LANGUAGE JAVA" +
            " EXTERNAL NAME 'com.gooddata.derby.extension.DerbyExtensions.atod'"
        );

        JdbcUtil.executeUpdate(c,
            "CREATE FUNCTION DTTOI(str VARCHAR(255), fmt VARCHAR(30)) RETURNS INT\n" +
            " PARAMETER STYLE JAVA NO SQL LANGUAGE JAVA" +
            " EXTERNAL NAME 'com.gooddata.derby.extension.DerbyExtensions.dttoi'"
        );
    }

    protected String decorateFactColumnForLoad(String cols, Column cl, String table) {
        if (cols.length() > 0)
            cols += ",ATOD(" + table.toUpperCase() + "." +
                    StringUtil.formatShortName(cl.getName())+")";
        else
            cols +=  "ATOD(" + table.toUpperCase() + "." +
                    StringUtil.formatShortName(cl.getName())+")";
        return cols;
    }

    protected String decorateLookupColumnForLoad(String cols, Column cl, String table) {
        if (cols != null && cols.length() > 0)
            cols += ",CAST(" + table.toUpperCase() + "." + StringUtil.formatShortName(cl.getName())+
                    " AS VARCHAR(128))";
        else
            cols +=  "CAST("+table.toUpperCase() + "." + StringUtil.formatShortName(cl.getName())+
                    " AS VARCHAR(128))";
        return cols;
    }

    protected void insertFactsToFactTable(Connection c, PdmSchema schema) throws ModelException, SQLException {
        PdmTable factTable = schema.getFactTable();
        PdmTable sourceTable = schema.getSourceTable();
        String fact = factTable.getName();
        String source = sourceTable.getName();
        String factColumns = "";
        String sourceColumns = "";
        for(PdmColumn column : factTable.getFactColumns()) {
            factColumns += "," + column.getName();
            sourceColumns += "," + column.getSourceColumn();
        }

        for(PdmColumn column : factTable.getDateColumns()) {
            factColumns += "," + column.getName();
            sourceColumns += ",DTTOI(" + column.getSourceColumn() + ",'"+column.getFormat()+"')";
        }
        JdbcUtil.executeUpdate(c,
            "INSERT INTO "+fact+"("+N.ID+factColumns+") SELECT "+N.SRC_ID + sourceColumns +
            " FROM " + source + " WHERE "+N.SRC_ID+" > (SELECT MAX(lastid) FROM snapshots WHERE name='"+fact+"')"
        );
    }
    
}
