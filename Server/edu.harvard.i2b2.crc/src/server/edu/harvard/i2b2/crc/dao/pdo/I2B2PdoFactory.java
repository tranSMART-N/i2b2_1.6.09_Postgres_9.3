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
import java.sql.Clob;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import edu.harvard.i2b2.common.util.db.JDBCUtil;
import edu.harvard.i2b2.common.util.jaxb.DTOFactory;
import edu.harvard.i2b2.crc.dao.DAOFactoryHelper;
import edu.harvard.i2b2.crc.datavo.pdo.BlobType;
import edu.harvard.i2b2.crc.datavo.pdo.ConceptType;
import edu.harvard.i2b2.crc.datavo.pdo.EventType;
import edu.harvard.i2b2.crc.datavo.pdo.ModifierType;
import edu.harvard.i2b2.crc.datavo.pdo.ObservationType;
import edu.harvard.i2b2.crc.datavo.pdo.ObserverType;
import edu.harvard.i2b2.crc.datavo.pdo.ParamType;
import edu.harvard.i2b2.crc.datavo.pdo.PatientIdType;
import edu.harvard.i2b2.crc.datavo.pdo.PatientType;
import edu.harvard.i2b2.crc.loader.dao.DataSourceLookupDAOFactory;

/**
 * Class to build individual sections of plain pdo xml like
 * patient,concept,observationfact from the given {@link java.sql.ResultSet}
 * 
 * $Id: I2B2PdoFactory.java,v 1.19 2009/11/14 16:53:58 rk903 Exp $
 * 
 * @author rkuttan
 */
public class I2B2PdoFactory {

	private DTOFactory dtoFactory = new DTOFactory();

	/**
	 * Inner class to build observation fact in Plain PDO format
	 */
	public class ObservationFactBuilder {
		/** detail flag **/
		boolean obsFactDetailFlag = false;
		/** blob flag **/
		boolean obsFactBlobFlag = false;
		/** status flag **/
		boolean obsFactStatusFlag = false;

		/**
		 * Parameter constructor
		 * 
		 * @param detailFlag
		 * @param blobFlag
		 * @param statusFlag
		 */
		public ObservationFactBuilder(boolean detailFlag, boolean blobFlag,
				boolean statusFlag) {
			this.obsFactDetailFlag = detailFlag;
			this.obsFactBlobFlag = blobFlag;
			this.obsFactStatusFlag = statusFlag;
		}

		/**
		 * Read one record from resultset and build observation fact
		 * 
		 * @param rowSet
		 *            resultset
		 * @return Observation fact set
		 * @throws SQLException
		 * @throws IOException
		 */
		public ObservationType buildObservationSet(ResultSet rowSet)
				throws SQLException, IOException {
			ObservationType observationFactType = new ObservationType();
			PatientIdType patientIdType = new PatientIdType();
			patientIdType.setValue(rowSet.getString("obs_patient_num"));

			observationFactType.setPatientId(patientIdType);
			ObservationType.EventId eventId = new ObservationType.EventId();
			eventId.setValue(rowSet.getString("obs_encounter_num"));
			observationFactType.setEventId(eventId);
			ObservationType.ConceptCd conceptCd = new ObservationType.ConceptCd();
			conceptCd.setValue(rowSet.getString("obs_concept_cd"));
			observationFactType.setConceptCd(conceptCd);

			ObservationType.ModifierCd modifierCd = new ObservationType.ModifierCd();
			modifierCd.setValue(rowSet.getString("obs_modifier_cd"));
			observationFactType.setModifierCd(modifierCd);

			ObservationType.InstanceNum instanceNum = new ObservationType.InstanceNum();
			instanceNum.setValue(rowSet.getString("obs_instance_num"));
			observationFactType.setInstanceNum(instanceNum);

			Date startDate = rowSet.getTimestamp("obs_start_date");

			if (startDate != null) {
				observationFactType.setStartDate(dtoFactory
						.getXMLGregorianCalendar(startDate.getTime()));
			}

			ObservationType.ObserverCd observerCd = new ObservationType.ObserverCd();
			observerCd
					.setValue(((rowSet.getString("obs_provider_id") != null) ? rowSet
							.getString("obs_provider_id")
							: ""));
			observationFactType.setObserverCd(observerCd);

			if (obsFactDetailFlag) {
				Date endDate = rowSet.getTimestamp("obs_end_date");

				if (endDate != null) {
					observationFactType.setEndDate(dtoFactory
							.getXMLGregorianCalendar(endDate.getTime()));
				}

				observationFactType.setValuetypeCd(rowSet
						.getString("obs_valtype_cd"));
				observationFactType.setTvalChar((rowSet
						.getString("obs_tval_char") != null) ? rowSet
						.getString("obs_tval_char") : "");

				ObservationType.NvalNum valNum = new ObservationType.NvalNum();
				valNum.setValue(rowSet.getBigDecimal("obs_nval_num"));
				observationFactType.setNvalNum(valNum);

				ObservationType.ValueflagCd valueFlagCd = new ObservationType.ValueflagCd();
				valueFlagCd.setValue(rowSet.getString("obs_valueflag_cd"));
				observationFactType.setValueflagCd(valueFlagCd);

				observationFactType.setQuantityNum(rowSet
						.getBigDecimal("obs_quantity_num"));

				observationFactType
						.setUnitsCd(rowSet.getString("obs_units_cd"));

				ObservationType.LocationCd locationCd = new ObservationType.LocationCd();
				locationCd.setValue(rowSet.getString("obs_location_cd"));
				observationFactType.setLocationCd(locationCd);
				observationFactType.setConfidenceNum(rowSet
						.getBigDecimal("obs_confidence_num"));
				// Double confidenceNum =
				// rowSet.getDouble("obs_confidence_num");
			}

			if (obsFactBlobFlag) {
				// smuniraju: Postgres dosen't support Clob datatypes, using TEXT instead.
				String serverType = DAOFactoryHelper.dataSourceLookup.getServerType();
				String obsObservationBlob = null;
				if(serverType.equalsIgnoreCase(DataSourceLookupDAOFactory.POSTGRES)) {
					obsObservationBlob = rowSet.getString("obs_observation_blob");
				} else {
					Clob observationClob = rowSet.getClob("obs_observation_blob");	
					obsObservationBlob = (observationClob != null) ? JDBCUtil.getClobString(observationClob) : "";
				}				
				if(obsObservationBlob != null) {
					BlobType blobType = new BlobType();
					blobType.getContent().add(obsObservationBlob);
					observationFactType.setObservationBlob(blobType);
				}
				/*
				Clob observationClob = rowSet.getClob("obs_observation_blob");			
				if (observationClob != null) {
					BlobType blobType = new BlobType();
					blobType.getContent().add(
							JDBCUtil.getClobString(observationClob));
					observationFactType.setObservationBlob(blobType);
				}
				*/
			}

			if (obsFactStatusFlag) {
				if (rowSet.getTimestamp("obs_update_date") != null) {
					observationFactType.setUpdateDate(dtoFactory
							.getXMLGregorianCalendar(rowSet.getTimestamp(
									"obs_update_date").getTime()));
				}

				if (rowSet.getTimestamp("obs_download_date") != null) {
					observationFactType.setDownloadDate(dtoFactory
							.getXMLGregorianCalendar(rowSet.getTimestamp(
									"obs_download_date").getTime()));
				}

				if (rowSet.getTimestamp("obs_import_date") != null) {
					observationFactType.setImportDate(dtoFactory
							.getXMLGregorianCalendar(rowSet.getTimestamp(
									"obs_import_date").getTime()));
				}

				observationFactType.setSourcesystemCd(rowSet
						.getString("obs_sourcesystem_cd"));
				observationFactType.setUploadId(rowSet
						.getString("obs_upload_id"));
			}

			return observationFactType;
		}
	}

	/**
	 * Inner class to build Patient dimension in Plain PDO format
	 */
	public class PatientBuilder {
		boolean patientDetailFlag = false;
		boolean patientBlobFlag = false;
		boolean patientStatusFlag = false;

		/**
		 * Patameter constructor
		 * 
		 * @param detailFlag
		 * @param blobFlag
		 * @param statusFlag
		 */
		public PatientBuilder(boolean detailFlag, boolean blobFlag,
				boolean statusFlag) {
			this.patientDetailFlag = detailFlag;
			this.patientBlobFlag = blobFlag;
			this.patientStatusFlag = statusFlag;
		}

		/**
		 * Function reads single row from the resultset and builds patient
		 * dimension
		 * 
		 * @param rowSet
		 * @return Patient dimension type
		 * @throws SQLException
		 * @throws IOException
		 */
		public PatientType buildPatientSet(ResultSet rowSet, List<ParamType> metaDataParamList)
				throws SQLException, IOException {
			PatientType patientDimensionType = new PatientType();
			PatientIdType patientIdType = new PatientIdType();
			patientIdType.setValue(rowSet.getString("patient_patient_num"));
			patientDimensionType.setPatientId(patientIdType);

			List<ParamType> paramTypeList = patientDimensionType.getParam();
			ParamType paramType = null;
			if (patientDetailFlag) {
				

				
				for (Iterator<ParamType> metaParamIterator = metaDataParamList.iterator(); metaParamIterator.hasNext();) { 
					ParamType metaParamType = metaParamIterator.next();
					ParamTypeValueBuilder paramValBuilder = new ParamTypeValueBuilder();
					paramTypeList.add(paramValBuilder.buildParamType(metaParamType,"patient_",null,rowSet));
				}
			}
			

			if (patientBlobFlag) {
				// smuniraju: Postgres doesn't support Clob datatypes, using TEXT instaed.				
				String serverType = DAOFactoryHelper.dataSourceLookup.getServerType();
				String patientBlob = null;
				if(serverType.equalsIgnoreCase(DataSourceLookupDAOFactory.POSTGRES)) {
					patientBlob = rowSet.getString("patient_patient_blob");
				} else {
					Clob patientClob = rowSet.getClob("patient_patient_blob");	
					patientBlob = (patientClob != null) ? JDBCUtil.getClobString(patientClob) : "";
				}				
				if(patientBlob != null) {
					BlobType blobType = new BlobType();
					blobType.getContent().add(patientBlob);
					patientDimensionType.setPatientBlob(blobType);
				}
			}

			if (patientStatusFlag) {
				if (rowSet.getTimestamp("patient_update_date") != null) {
					patientDimensionType.setUpdateDate(dtoFactory
							.getXMLGregorianCalendar(rowSet.getTimestamp(
									"patient_update_date").getTime()));
				}

				if (rowSet.getTimestamp("patient_download_date") != null) {
					patientDimensionType.setDownloadDate(dtoFactory
							.getXMLGregorianCalendar(rowSet.getTimestamp(
									"patient_download_date").getTime()));
				}

				if (rowSet.getTimestamp("patient_import_date") != null) {
					patientDimensionType.setImportDate(dtoFactory
							.getXMLGregorianCalendar(rowSet.getTimestamp(
									"patient_import_date").getTime()));
				}

				patientDimensionType.setSourcesystemCd(rowSet
						.getString("patient_sourcesystem_cd"));
				patientDimensionType.setUploadId(rowSet
						.getString("patient_upload_id"));
			}

			return patientDimensionType;
		}
	}

	/**
	 * Inner class to build provider dimension in plain pdo format
	 */
	public class ProviderBuilder {
		boolean providerDetailFlag = false;
		boolean providerBlobFlag = false;
		boolean providerStatusFlag = false;

		/**
		 * Parameter constructor
		 * 
		 * @param detailFlag
		 * @param blobFlag
		 * @param statusFlag
		 */
		public ProviderBuilder(boolean detailFlag, boolean blobFlag,
				boolean statusFlag) {
			this.providerDetailFlag = detailFlag;
			this.providerBlobFlag = blobFlag;
			this.providerStatusFlag = statusFlag;
		}

		/**
		 * Reads single row from resultset and builds provider dimension
		 * 
		 * @param rowSet
		 * @return
		 * @throws SQLException
		 * @throws IOException
		 */
		public ObserverType buildObserverSet(ResultSet rowSet)
				throws SQLException, IOException {
			ObserverType providerDimensionType = new ObserverType();
			providerDimensionType.setObserverCd(rowSet
					.getString("provider_provider_id"));
			providerDimensionType.setObserverPath(rowSet
					.getString("provider_provider_path"));

			if (providerDetailFlag) {
				providerDimensionType.setNameChar(rowSet
						.getString("provider_name_char"));
			}

			if (providerBlobFlag) {
				// smuniraju: Postgres doesn't support Clob datatypes, using TEXT instaed.
				String serverType = DAOFactoryHelper.dataSourceLookup.getServerType();
				String providerBlobString = null;
				if(serverType.equalsIgnoreCase(DataSourceLookupDAOFactory.POSTGRES)) {
					providerBlobString = rowSet.getString("provider_provider_blob");
				} else {
					Clob conceptClob = rowSet.getClob("provider_provider_blob");
					if(conceptClob != null) {
						providerBlobString = JDBCUtil.getClobString(conceptClob);
					}
				}
				if (providerBlobString != null) {
					BlobType providerBlobType = new BlobType();
					providerBlobType.getContent().add(providerBlobString);
					providerDimensionType.setObserverBlob(providerBlobType);
				}
			}

			if (providerStatusFlag) {
				if (rowSet.getTimestamp("provider_update_date") != null) {
					providerDimensionType.setUpdateDate(dtoFactory
							.getXMLGregorianCalendar(rowSet.getTimestamp(
									"provider_update_date").getTime()));
				}

				if (rowSet.getTimestamp("provider_download_date") != null) {
					providerDimensionType.setDownloadDate(dtoFactory
							.getXMLGregorianCalendar(rowSet.getTimestamp(
									"provider_download_date").getTime()));
				}

				if (rowSet.getTimestamp("provider_import_date") != null) {
					providerDimensionType.setImportDate(dtoFactory
							.getXMLGregorianCalendar(rowSet.getTimestamp(
									"provider_import_date").getTime()));
				}

				providerDimensionType.setSourcesystemCd(rowSet
						.getString("provider_sourcesystem_cd"));
				providerDimensionType.setUploadId(rowSet
						.getString("provider_upload_id"));
			}

			return providerDimensionType;
		}
	}

	/**
	 * Inner class to build concept dimension in plain pdo format
	 */
	public class ConceptBuilder {
		boolean conceptDetailFlag = false;
		boolean conceptBlobFlag = false;
		boolean conceptStatusFlag = false;

		/**
		 * Parameter Constuctor
		 * 
		 * @param detailFlag
		 * @param blobFlag
		 * @param statusFlag
		 */
		public ConceptBuilder(boolean detailFlag, boolean blobFlag,
				boolean statusFlag) {
			this.conceptDetailFlag = detailFlag;
			this.conceptBlobFlag = blobFlag;
			this.conceptStatusFlag = statusFlag;
		}

		/**
		 * Reads one row from result set and builds concept dimension
		 * 
		 * @param rowSet
		 * @return
		 * @throws SQLException
		 * @throws IOException
		 */
		public ConceptType buildConceptSet(ResultSet rowSet)
				throws SQLException, IOException {
			ConceptType conceptDimensionType = new ConceptType();

			conceptDimensionType.setConceptCd(rowSet
					.getString("concept_concept_cd"));

			if (conceptDetailFlag) {
				conceptDimensionType.setConceptCd(rowSet
						.getString("concept_concept_cd"));
				conceptDimensionType.setConceptPath(rowSet
						.getString("concept_concept_path"));
				conceptDimensionType.setNameChar(rowSet
						.getString("concept_name_char"));
			}

			if (conceptBlobFlag) {
				// smuniraju: Postgres dosen't support Clob datatypes, using TEXT instead.
				String serverType = DAOFactoryHelper.dataSourceLookup.getServerType();
				String conceptBlobString = null;
				if(serverType.equalsIgnoreCase(DataSourceLookupDAOFactory.POSTGRES)) {
					conceptBlobString = rowSet.getString("concept_concept_blob");
				} else {
					Clob conceptClob = rowSet.getClob("concept_concept_blob");
					if(conceptClob != null) {
						conceptBlobString = JDBCUtil.getClobString(conceptClob);
					}
				}
				if(conceptBlobString != null) {
					BlobType conceptBlobType = new BlobType();
					conceptBlobType.getContent().add(conceptBlobString);
					conceptDimensionType.setConceptBlob(conceptBlobType);
				}	
			}

			if (conceptStatusFlag) {
				if (rowSet.getTimestamp("concept_update_date") != null) {
					conceptDimensionType.setUpdateDate(dtoFactory
							.getXMLGregorianCalendar(rowSet.getTimestamp(
									"concept_update_date").getTime()));
				}

				if (rowSet.getTimestamp("concept_download_date") != null) {
					conceptDimensionType.setDownloadDate(dtoFactory
							.getXMLGregorianCalendar(rowSet.getTimestamp(
									"concept_download_date").getTime()));
				}

				if (rowSet.getTimestamp("concept_import_date") != null) {
					conceptDimensionType.setImportDate(dtoFactory
							.getXMLGregorianCalendar(rowSet.getTimestamp(
									"concept_import_date").getTime()));
				}

				conceptDimensionType.setSourcesystemCd(rowSet
						.getString("concept_sourcesystem_cd"));
				conceptDimensionType.setUploadId(rowSet
						.getString("concept_upload_id"));
			}

			return conceptDimensionType;
		}
	}


	/**
	 * Inner class to build concept dimension in plain pdo format
	 */
	public class ModifierBuilder {
		boolean modifierDetailFlag = false;
		boolean modifierBlobFlag = false;
		boolean modifierStatusFlag = false;

		/**
		 * Parameter Constuctor
		 * 
		 * @param detailFlag
		 * @param blobFlag
		 * @param statusFlag
		 */
		public ModifierBuilder(boolean detailFlag, boolean blobFlag,
				boolean statusFlag) {
			this.modifierDetailFlag = detailFlag;
			this.modifierBlobFlag = blobFlag;
			this.modifierStatusFlag = statusFlag;
		}

		/**
		 * Reads one row from result set and builds concept dimension
		 * 
		 * @param rowSet
		 * @return
		 * @throws SQLException
		 * @throws IOException
		 */
		public ModifierType buildModifierSet(ResultSet rowSet)
				throws SQLException, IOException {
			ModifierType modifierDimensionType = new ModifierType();

			modifierDimensionType.setModifierCd(rowSet
					.getString("modifier_modifier_cd"));

			if (modifierDetailFlag) {
				modifierDimensionType.setModifierCd(rowSet
						.getString("modifier_modifier_cd"));
				modifierDimensionType.setModifierPath(rowSet
						.getString("modifier_modifier_path"));
				modifierDimensionType.setNameChar(rowSet
						.getString("modifier_name_char"));
			}

			if (modifierBlobFlag) {
				// smuniraju: Postgres doesn't support Clob datatypes, using TEXT instaed.
				String serverType = DAOFactoryHelper.dataSourceLookup.getServerType();
				String modifierBlobString = null;
				if(serverType.equalsIgnoreCase(DataSourceLookupDAOFactory.POSTGRES)) {
					modifierBlobString = rowSet.getString("modifier_modifier_blob");
				} else {
					Clob conceptClob = rowSet.getClob("modifier_modifier_blob");
					if(conceptClob != null) {
						modifierBlobString = JDBCUtil.getClobString(conceptClob);
					}
				}
				if (modifierBlobString != null) {
					BlobType modifierBlobType = new BlobType();
					modifierBlobType.getContent().add(modifierBlobString);
					modifierDimensionType.setModifierBlob(modifierBlobType);
				}
			}

			if (modifierStatusFlag) {
				if (rowSet.getTimestamp("modifier_update_date") != null) {
					modifierDimensionType.setUpdateDate(dtoFactory
							.getXMLGregorianCalendar(rowSet.getTimestamp(
									"modifier_update_date").getTime()));
				}

				if (rowSet.getTimestamp("modifier_download_date") != null) {
					modifierDimensionType.setDownloadDate(dtoFactory
							.getXMLGregorianCalendar(rowSet.getTimestamp(
									"modifier_download_date").getTime()));
				}

				if (rowSet.getTimestamp("modifier_import_date") != null) {
					modifierDimensionType.setImportDate(dtoFactory
							.getXMLGregorianCalendar(rowSet.getTimestamp(
									"modifier_import_date").getTime()));
				}

				modifierDimensionType.setSourcesystemCd(rowSet
						.getString("modifier_sourcesystem_cd"));
				modifierDimensionType.setUploadId(rowSet
						.getString("modifier_upload_id"));
			}

			return modifierDimensionType;
		}
	}
	
	/**
	 * Inner class to build visit dimension in plain pdo format
	 */
	public class EventBuilder {
		boolean eventDetailFlag = false;
		boolean eventBlobFlag = false;
		boolean eventStatusFlag = false;

		public EventBuilder(boolean detailFlag, boolean blobFlag,
				boolean statusFlag) {
			this.eventDetailFlag = detailFlag;
			this.eventBlobFlag = blobFlag;
			this.eventStatusFlag = statusFlag;
		}

		/**
		 * Reads one row from result set and builds visit/event dimension
		 * 
		 * @param rowSet
		 * @return
		 * @throws SQLException
		 * @throws IOException
		 */
		public EventType buildEventSet(ResultSet rowSet,List<ParamType> metaDataParamList) throws SQLException,
				IOException {
			EventType visitDimensionType = new EventType();

			PatientIdType patientIdType = new PatientIdType();
			patientIdType.setValue(rowSet.getString("visit_patient_num"));
			visitDimensionType.setPatientId(patientIdType);
			EventType.EventId eventId = new EventType.EventId();
			eventId.setValue(rowSet.getString("visit_encounter_num"));
			visitDimensionType.setEventId(eventId);

			if (eventDetailFlag) {

				Date startDate = rowSet.getTimestamp("visit_start_date");

				if (startDate != null) {
					visitDimensionType.setStartDate(dtoFactory
							.getXMLGregorianCalendar(startDate.getTime()));
				}

				Date endDate = rowSet.getTimestamp("visit_end_date");

				if (endDate != null) {
					visitDimensionType.setEndDate(dtoFactory
							.getXMLGregorianCalendar(endDate.getTime()));
				}
				for (Iterator<ParamType> metaParamIterator = metaDataParamList.iterator(); metaParamIterator.hasNext();) { 
					ParamType metaParamType = metaParamIterator.next();
					ParamTypeValueBuilder paramValBuilder = new ParamTypeValueBuilder();
					visitDimensionType.getParam().add(paramValBuilder.buildParamType(metaParamType,"visit_",null,rowSet));
				}
				
			}

			if (eventBlobFlag) {
				// smuniraju: Postgres doesn't support Clob datatypes, using TEXT instaed.
				String serverType = DAOFactoryHelper.dataSourceLookup.getServerType();
				String visitBlobString = null;
				if(serverType.equalsIgnoreCase(DataSourceLookupDAOFactory.POSTGRES)) {
					visitBlobString = rowSet.getString("visit_visit_blob");
				} else {
					Clob conceptClob = rowSet.getClob("visit_visit_blob");
					if(conceptClob != null) {
						visitBlobString = JDBCUtil.getClobString(conceptClob);
					}
				}
				if (visitBlobString != null) {
					BlobType visitBlobType = new BlobType();
					visitBlobType.getContent().add(visitBlobString);
					visitDimensionType.setEventBlob(visitBlobType);
				}				
			}

			if (eventStatusFlag) {
				if (rowSet.getTimestamp("visit_update_date") != null) {
					visitDimensionType.setUpdateDate(dtoFactory
							.getXMLGregorianCalendar(rowSet.getTimestamp(
									"visit_update_date").getTime()));
				}

				if (rowSet.getTimestamp("visit_download_date") != null) {
					visitDimensionType.setDownloadDate(dtoFactory
							.getXMLGregorianCalendar(rowSet.getTimestamp(
									"visit_download_date").getTime()));
				}

				if (rowSet.getTimestamp("visit_import_date") != null) {
					visitDimensionType.setImportDate(dtoFactory
							.getXMLGregorianCalendar(rowSet.getTimestamp(
									"visit_import_date").getTime()));
				}

				visitDimensionType.setSourcesystemCd(rowSet
						.getString("visit_sourcesystem_cd"));
				visitDimensionType.setUploadId(rowSet
						.getString("visit_upload_id"));
			}

			return visitDimensionType;
		}
	}
}
