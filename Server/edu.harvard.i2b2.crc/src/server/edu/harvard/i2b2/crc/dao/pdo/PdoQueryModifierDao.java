/*
 * Copyright (c) 2006-2007 Massachusetts General Hospital 
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the i2b2 Software License v1.0 
 * which accompanies this distribution. 
 * 
 * Contributors: 
 *     Rajesh Kuttan
 */
package edu.harvard.i2b2.crc.dao.pdo;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import javax.sql.DataSource;

import oracle.sql.ArrayDescriptor;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jboss.resource.adapter.jdbc.WrappedConnection;

import edu.harvard.i2b2.common.exception.I2B2DAOException;
import edu.harvard.i2b2.common.util.db.JDBCUtil;
import edu.harvard.i2b2.crc.dao.CRCDAO;
import edu.harvard.i2b2.crc.dao.DAOFactoryHelper;
import edu.harvard.i2b2.crc.dao.pdo.input.FactRelatedQueryHandler;
import edu.harvard.i2b2.crc.dao.pdo.input.IInputOptionListHandler;
import edu.harvard.i2b2.crc.dao.pdo.input.SQLServerFactRelatedQueryHandler;
import edu.harvard.i2b2.crc.dao.pdo.output.ConceptFactRelated;
import edu.harvard.i2b2.crc.dao.pdo.output.ModifierFactRelated;
import edu.harvard.i2b2.crc.datavo.db.DataSourceLookup;
import edu.harvard.i2b2.crc.datavo.pdo.ConceptSet;
import edu.harvard.i2b2.crc.datavo.pdo.ConceptType;
import edu.harvard.i2b2.crc.datavo.pdo.ModifierSet;
import edu.harvard.i2b2.crc.datavo.pdo.ModifierType;
import edu.harvard.i2b2.crc.datavo.pdo.PatientDataType;

/**
 * This class handles Concept dimension query's related to PDO request $Id:
 * PdoQueryConceptDao.java,v 1.11 2008/03/19 22:42:08 rk903 Exp $
 * 
 * @author rkuttan
 */
public class PdoQueryModifierDao extends CRCDAO implements IPdoQueryModifierDao {

	private DataSourceLookup dataSourceLookup = null;

	public PdoQueryModifierDao(DataSourceLookup dataSourceLookup,
			DataSource dataSource) {
		this.dataSourceLookup = dataSourceLookup;
		setDataSource(dataSource);
		setDbSchemaName(dataSourceLookup.getFullSchema());
	}

	/** log * */
	protected final Log log = LogFactory.getLog(getClass());

	/**
	 * Get concepts detail from concept code list
	 * 
	 * @param conceptCdList
	 * @param detailFlag
	 * @param blobFlag
	 * @param statusFlag
	 * @return {@link PatientDataType.ConceptDimensionSet}
	 * @throws I2B2DAOException
	 */
	public ModifierSet getModifierByModifierCd(List<String> modifierCdList,
			boolean detailFlag, boolean blobFlag, boolean statusFlag)
			throws I2B2DAOException {

		ModifierSet modifierDimensionSet = new ModifierSet();
		log.debug("Size of input modifier cd list " + modifierCdList.size());
		Connection conn = null;
		PreparedStatement query = null;
		String tempTableName = "";
		try {
			conn = getDataSource().getConnection();
			ModifierFactRelated modifierFactRelated = new ModifierFactRelated(
					buildOutputOptionType(detailFlag, blobFlag, statusFlag));

			String selectClause = modifierFactRelated.getSelectClause();
			String serverType = dataSourceLookup.getServerType();
			if (serverType.equalsIgnoreCase(DAOFactoryHelper.ORACLE)) {
				// get oracle connection from jboss wrapped connection
				// Otherwise Jboss wrapped connection fails when using oracle
				// Arrays
				oracle.jdbc.driver.OracleConnection conn1 = (oracle.jdbc.driver.OracleConnection) ((WrappedConnection) conn)
						.getUnderlyingConnection();
				String finalSql = "SELECT "
						+ selectClause
						+ "  FROM "
						+ getDbSchemaName()
						+ "modifier_dimension modifier WHERE modifier.modifier_cd IN (SELECT * FROM TABLE (?))";
				log.debug("Pdo modifier sql [" + finalSql + "]");
				query = conn1.prepareStatement(finalSql);

				ArrayDescriptor desc = ArrayDescriptor.createDescriptor(
						"QT_PDO_QRY_STRING_ARRAY", conn1);

				oracle.sql.ARRAY paramArray = new oracle.sql.ARRAY(desc, conn1,
						modifierCdList.toArray(new String[] {}));
				query.setArray(1, paramArray);
			} else if (serverType.equalsIgnoreCase(DAOFactoryHelper.SQLSERVER)) {
				log.debug("creating temp table");
				java.sql.Statement tempStmt = conn.createStatement();
				tempTableName = SQLServerFactRelatedQueryHandler.TEMP_PDO_INPUTLIST_TABLE;
				try {
					tempStmt.executeUpdate("drop table " + tempTableName);
				} catch (SQLException sqlex) {
					;
				}

				uploadTempTable(tempStmt, tempTableName, modifierCdList);
				String finalSql = "SELECT "
						+ selectClause
						+ " FROM "
						+ getDbSchemaName()
						+ "modifier_dimension modifier WHERE modifier.modifier_cd IN (select distinct char_param1 FROM "
						+ tempTableName + ") order by modifier_path";
				log.debug("Executing [" + finalSql + "]");

				query = conn.prepareStatement(finalSql);

			} else if (serverType.equalsIgnoreCase(DAOFactoryHelper.POSTGRES)) {
				log.debug("creating temp table");
				java.sql.Statement tempStmt = conn.createStatement();
				tempTableName = FactRelatedQueryHandler.TEMP_PDO_INPUTLIST_TABLE;
				try {
					tempStmt.executeUpdate("drop table if exists " + tempTableName);
				} catch (SQLException sqlex) {
					;
				}

				uploadTempTable(tempStmt, tempTableName, modifierCdList);
				String finalSql = "SELECT "
						+ selectClause
						+ " FROM "
						+ getDbSchemaName()
						+ "modifier_dimension modifier WHERE modifier.modifier_cd IN (select distinct char_param1 FROM "
						+ tempTableName + ") order by modifier_path";
				log.debug("Executing [" + finalSql + "]");

				query = conn.prepareStatement(finalSql);
			}
			ResultSet resultSet = query.executeQuery();

			I2B2PdoFactory.ModifierBuilder modifierBuilder = new I2B2PdoFactory().new ModifierBuilder(
					detailFlag, blobFlag, statusFlag);
			while (resultSet.next()) {
				ModifierType modifierDimensionType = modifierBuilder
						.buildModifierSet(resultSet);
				modifierDimensionSet.getModifier().add(modifierDimensionType);
			}

		} catch (SQLException sqlEx) {
			log.error("", sqlEx);
			throw new I2B2DAOException("", sqlEx);
		} catch (IOException ioEx) {
			log.error("", ioEx);
			throw new I2B2DAOException("", ioEx);
		} finally {
			if (dataSourceLookup.getServerType().equalsIgnoreCase(
					DAOFactoryHelper.SQLSERVER)) {
				PdoTempTableUtil tempUtil = new PdoTempTableUtil();
				tempUtil.deleteTempTableSqlServer(conn, tempTableName);
			}
			try {
				JDBCUtil.closeJdbcResource(null, query, conn);
			} catch (SQLException sqlEx) {
				sqlEx.printStackTrace();
			}
		}
		return modifierDimensionSet;
	}

	

	private void uploadTempTable(Statement tempStmt, String tempTable,
			List<String> patientNumList) throws SQLException {
		
		// smuniraju: Extended to include POSTGRES 
		String serverType = dataSourceLookup.getServerType();
		String createTempInputListTable = "";
		if (serverType.equalsIgnoreCase(DAOFactoryHelper.POSTGRES)) {
			createTempInputListTable = "create temporary table " + tempTable
					+ " ( char_param1 varchar(100) )";
		} else if (serverType.equalsIgnoreCase(DAOFactoryHelper.SQLSERVER)) {
			createTempInputListTable = "create table " + tempTable
					+ " ( char_param1 varchar(100) )";
		}
		// smuniraju: Extended to include POSTGRES 
		// String createTempInputListTable = "create table " + tempTable
		//		+ " ( char_param1 varchar(100) )";
		tempStmt.executeUpdate(createTempInputListTable);
		log.debug("created temp table" + tempTable);
		PreparedStatement preparedStmt = tempStmt.getConnection()
				.prepareStatement("insert into " + tempTable + " values (?)");
		// load to temp table
		// TempInputListInsert inputListInserter = new
		// TempInputListInsert(dataSource,TEMP_PDO_INPUTLIST_TABLE);
		// inputListInserter.setBatchSize(100);
		int i = 0;
		for (String singleValue : patientNumList) {
			preparedStmt.setString(1, singleValue);
			preparedStmt.addBatch();
			log.debug("adding batch [" + i + "] " + singleValue);
			i++;
			if (i % 100 == 0) {
				log.debug("batch insert [" + i + "]");
				preparedStmt.executeBatch();

			}
		}
		log.debug("batch insert [" + i + "]");
		preparedStmt.executeBatch();
	}

	public ModifierSet getModifierByFact(List<String> panelSqlList,
			List<Integer> sqlParamCountList,
			IInputOptionListHandler inputOptionListHandler, boolean detailFlag,
			boolean blobFlag, boolean statusFlag) throws I2B2DAOException {

		ModifierSet modifierSet = new ModifierSet();
		I2B2PdoFactory.ModifierBuilder modifierBuilder = new I2B2PdoFactory().new ModifierBuilder(
				detailFlag, blobFlag, statusFlag);
		ModifierFactRelated modifierFactRelated = new ModifierFactRelated(
				buildOutputOptionType(detailFlag, blobFlag, statusFlag));
		String selectClause = modifierFactRelated.getSelectClause();
		String serverType = dataSourceLookup.getServerType();
		String tempTable = "";
		Connection conn = null;
		PreparedStatement query = null;
		try {
			conn = dataSource.getConnection();
			if (serverType.equalsIgnoreCase(DAOFactoryHelper.ORACLE)) {
				tempTable = FactRelatedQueryHandler.TEMP_FACT_PARAM_TABLE;
			} else if (serverType.equalsIgnoreCase(DAOFactoryHelper.SQLSERVER)) {
				log.debug("creating temp table");
				java.sql.Statement tempStmt = conn.createStatement();
				tempTable = SQLServerFactRelatedQueryHandler.TEMP_FACT_PARAM_TABLE;
				try {
					tempStmt.executeUpdate("drop table " + tempTable);
				} catch (SQLException sqlex) {
					;
				}
				String createTempInputListTable = "create table " + tempTable
						+ " ( set_index int, char_param1 varchar(500) )";
				tempStmt.executeUpdate(createTempInputListTable);
				log.debug("created temp table" + tempTable);
			} else if (serverType.equalsIgnoreCase(DAOFactoryHelper.POSTGRES)) {
				log.debug("creating temp table");
				java.sql.Statement tempStmt = conn.createStatement();
				tempTable = FactRelatedQueryHandler.TEMP_FACT_PARAM_TABLE;
				try {
					tempStmt.executeUpdate("drop table if exists " + tempTable);
				} catch (SQLException sqlex) {
					;
				}
				String createTempInputListTable = "create temporary table " + tempTable
						+ " ( set_index int, char_param1 varchar(500) )";
				tempStmt.executeUpdate(createTempInputListTable);
				log.debug("created temp table" + tempTable);
			}
			// if the inputlist is enumeration, then upload the enumerated input
			// to temp table.
			// the uploaded enumerated input will be used in the fact join.
			if (inputOptionListHandler.isEnumerationSet()) {
				inputOptionListHandler.uploadEnumerationValueToTempTable(conn);
			}
			String insertSql = "";
			int i = 0;
			int sqlParamCount = 0;
			ResultSet resultSet = null;
			for (String panelSql : panelSqlList) {
				insertSql = " insert into "
						+ tempTable
						+ "(char_param1) select distinct obs_modifier_cd from ( "
						+ panelSql + ") b";

				log.debug("Executing SQL [ " + insertSql + "]");
				sqlParamCount = sqlParamCountList.get(i++);
				// conn.createStatement().executeUpdate(insertSql);
				executeTotalSql(insertSql, conn, sqlParamCount,
						inputOptionListHandler);

			}

			String finalSql = "SELECT "
					+ selectClause
					+ " FROM "
					+ getDbSchemaName()
					+ "modifier_dimension modifier where modifier_cd in (select distinct char_param1 from "
					+ tempTable + ") order by modifier_path";
			log.debug("Executing SQL [" + finalSql + "]");
			System.out.println("Final Sql " + finalSql);

			query = conn.prepareStatement(finalSql);

			resultSet = query.executeQuery();

			while (resultSet.next()) {
				ModifierType modifier = modifierBuilder.buildModifierSet(resultSet);
				modifierSet.getModifier().add(modifier);
			}
		} catch (SQLException sqlEx) {
			log.error("", sqlEx);
			throw new I2B2DAOException("sql exception", sqlEx);
		} catch (IOException ioEx) {
			log.error("", ioEx);
			throw new I2B2DAOException("IO exception", ioEx);
		} finally {
			PdoTempTableUtil tempUtil = new PdoTempTableUtil();
			tempUtil.clearTempTable(dataSourceLookup.getServerType(), conn, tempTable);
			
			if (inputOptionListHandler != null
					&& inputOptionListHandler.isEnumerationSet()) {
				try {
					inputOptionListHandler.deleteTempTable(conn);
				} catch (SQLException e) {

					e.printStackTrace();
				}
			}
			try {

				JDBCUtil.closeJdbcResource(null, query, conn);
			} catch (SQLException sqlEx) {
				sqlEx.printStackTrace();
			}
		}
		return modifierSet;
	}

	private void executeTotalSql(String totalSql, Connection conn,
			int sqlParamCount, IInputOptionListHandler inputOptionListHandler)
			throws SQLException {

		PreparedStatement stmt = conn.prepareStatement(totalSql);

		System.out.println(totalSql + " [ " + sqlParamCount + " ]");
		if (inputOptionListHandler.isCollectionId()) {
			for (int i = 1; i <= sqlParamCount; i++) {
				stmt.setInt(i, Integer.parseInt(inputOptionListHandler
						.getCollectionId()));
			}
		}

		stmt.executeUpdate();

	}

}
