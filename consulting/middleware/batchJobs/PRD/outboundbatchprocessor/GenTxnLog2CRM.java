package outboundbatchprocessor;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class GenTxnLog2CRM extends GenericOutBoundProcessor {

	public static String status;
	public static String sql = "select '50' REC_TYPE," + "       '1' Filler_01," + "       EVENT_ID Event_Id,"
			+ "       Event_Dte Event_Dte,"
			+ "        (substr(EVENT_TIME, 1, 2) || ':' || substr(EVENT_TIME, 3,2) || ':' || substr(EVENT_TIME, 5,2)) Event_Time,"
			+ "       CHNL_ID Chnl_Id," + "       CHNL_TYPE_CODE Chnl_Type_Code,"
			+ "       EVENT_STATUS_CODE Event_Status_Code," + "       CR_DR_IND Cr_Dr_Ind,"
			+ "       FEE_CHRG_CODE Fee_Chrg_Code," + "       EVENT_ACTV_TYPE_CODE Event_Actv_Type_Code,"
			+ "       FIN_IND Fin_Ind," + "       SELF_SRV_IND Self_Srv_Ind," + "       USER_ID User_Id,"
			+ "       EVENT_COUNTRY_CODE Event_Country_Code," + "       ACCT_NBR Acct_Nbr,"
			+ "       PHONE_NBR Phone_Nbr," + "       PHONE_NBR_ACCT_NBR Phone_Nbr_Acct_Nbr,"
			+ "       PHONE_NBR_REQ_RESULT Phone_Nbr_Req_Result," + "       PHONE_DEFAULT_IND Phone_Default_Ind,"
			+ "       ELECT_ADD Elect_Add," + "       ELECT_ADD_ACCT_NBR Elect_Add_Acct_Nbr,"
			+ "       ELECT_ADD_REQ_RESULT  Elect_Add_Req_Result,"
			+ "       ELECT_ADD_DEFAULT_IND  Elect_Add_Default_Ind," + "       FPS_ID FPS_ID,"
			+ "       FPS_ACCT_NBR FPS_Acct_Nbr," + "       FPS_REQ_RESULT FPS_Req_Result,"
			+ "       REGEXP_SUBSTR(DEBIT_ACCT_NBR,'[^~]+',1,1) Debit_Acct_Nbr,"
			+ "       EVENT_CCY_CODE Event_Ccy_Code," + "       EVENT_AMT Event_Amt,"
			+ "       FEE_CHRG_AMT Fee_Chrg_Amt," + "       FEE_CCY_CODE Fee_Ccy_Code," + "       TRF_DTE Trf_Dte,"
			+ "       Trf_Freq Trf_Freq," + "       PROXY_ID_TYPE Proxy_ID_Type," + "       PROXY_ID Proxy_ID,"
			+ "       PAYEE_NAME Payee_Name," + "       PAYEE_BANK_CODE Payee_Bank_Code,"
			+ "       EVENT_REM Event_Rem," + "       REF_NBR Ref_Nbr," + "       FROM_DTE  From_Dte,"
			+ "       TO_DTE To_Dte," + "       MANDATE_ID Mandate_Id," + "       EDDA_MAINT_ACTION eDDA_Maint_Action,"
			+ "       SOURCE_TRX_REF_NBR Source_Trx_Ref_Nbr," + "       PAY_CAT_PURP_CODE Pay_Cat_Purp_Code,"
			+ "       PAY_PURP_CODE Pay_Purp_Code," + "       DEVICE_ID Device_Id,"
			+ "       MOBILE_BRAND Mobile_Brand," + "       PLATFORM_CODE Platform_Code,"
			+ "       DEVICE_MODEL Device_Model," + "       DEVICE_OS_VERSION Device_OS_Version,"
			+ "       IP_Address,Event_Amt_Hke,Event_Ex_Rate,Mrch_Id,Acct_Ccy_Code,Acct_Amt,Screen_Id,OMB_FLAG,"
			+ "       Multi_App_Rej_Txn_Cnt,Coupon_Code,Actl_Int_Rate,TT_Auto_Route,Suspicious_Activity,Country_Name,Region,Suspicious_Ind,"
			+ "       With_Mrch,Cmpy_Name,Doc_Id,Doc_Type,Doc_Country_Code,Mrch_User_Id,Mrch_User_Name,Doc_Cmpy_Name,Doc_Acct_Nbr,Sle_Mrch_Id,Sle_Mrch_Name,Format,Txn_Count,Adv_Freq_Type,Adv_Type,AR_TOKEN,TREASURY_REF,LM_SWEEP_INSTRUCTION_TYPE,LM_FREQUENCY_EXECUTIONDAY,LM_EFFECTIVEDATE,LM_RULE_SETUPDATE,LM_SWEEPING_AMOUNT_THRESHOLD,LM_FPXTXN_NBR,LM_INSTRUCTION_NBR,FPS_REF_NBR,PARTY_INT_NBR,EVENT_CREDIT_ACCT_TYPE,EVENT_CREDIT_ACCT_NBR,FPS_BUS_SERVICE_CD,EVENT_REMITTER_NAME,"
			+ "       '  ' filler_02"
			+ "  from digx_cz_crm_event3_details WHERE BATCH_PROCESSED_DATE = to_date('1990-01-01 11:11:11','yyyy-MM-dd hh24:mi:ss') and EVENT_ACTV_TYPE_CODE is not null and user_id  <> 'anonymous' and (CHNL_ID is null or CHNL_ID <> 'ELE-HTH')";

	String OutFileType = "FL";

	public static Map<Integer, ColumnDetails> mapColumnDetails = new HashMap();
	{
		mapColumnDetails.put(1, new ColumnDetails("REC_TYPE", 2));
		mapColumnDetails.put(2, new ColumnDetails("Filler_01", 1));
		mapColumnDetails.put(3, new ColumnDetails("Event_Id", 20));
		mapColumnDetails.put(4, new ColumnDetails("Event_Dte", 8));
		mapColumnDetails.put(5, new ColumnDetails("Event_Time", 8));
		mapColumnDetails.put(6, new ColumnDetails("Chnl_Id", 14));
		mapColumnDetails.put(7, new ColumnDetails("Chnl_Type_Code", 3));
		mapColumnDetails.put(8, new ColumnDetails("Event_Status_Code", 3));
		mapColumnDetails.put(9, new ColumnDetails("Cr_Dr_Ind", 1));
		mapColumnDetails.put(10, new ColumnDetails("Fee_Chrg_Code", 5));
		mapColumnDetails.put(11, new ColumnDetails("Event_Actv_Type_Code", 7));
		mapColumnDetails.put(12, new ColumnDetails("Fin_Ind", 1));
		mapColumnDetails.put(13, new ColumnDetails("Self_Srv_Ind", 1));
		mapColumnDetails.put(14, new ColumnDetails("User_Id", 8, "SHORT"));
		mapColumnDetails.put(15, new ColumnDetails("Event_Country_Code", 2));
		mapColumnDetails.put(16, new ColumnDetails("Acct_Nbr", 20));
		mapColumnDetails.put(17, new ColumnDetails("Phone_Nbr", 20));
		mapColumnDetails.put(18, new ColumnDetails("Phone_Nbr_Acct_Nbr", 20));
		mapColumnDetails.put(19, new ColumnDetails("Phone_Nbr_Req_Result", 1));
		mapColumnDetails.put(20, new ColumnDetails("Phone_Default_Ind", 1));
		mapColumnDetails.put(21, new ColumnDetails("Elect_Add", 50));
		mapColumnDetails.put(22, new ColumnDetails("Elect_Add_Acct_Nbr", 20));
		mapColumnDetails.put(23, new ColumnDetails("Elect_Add_Req_Result", 1));
		mapColumnDetails.put(24, new ColumnDetails("Elect_Add_Default_Ind", 1));
		mapColumnDetails.put(25, new ColumnDetails("FPS_ID", 20));
		mapColumnDetails.put(26, new ColumnDetails("FPS_Acct_Nbr", 20));
		mapColumnDetails.put(27, new ColumnDetails("FPS_Req_Result", 1));
		mapColumnDetails.put(28, new ColumnDetails("Debit_Acct_Nbr", 20));
		mapColumnDetails.put(29, new ColumnDetails("Event_Ccy_Code", 3));
		mapColumnDetails.put(30, new ColumnDetails("Event_Amt", 17));
		mapColumnDetails.put(31, new ColumnDetails("Fee_Chrg_Amt", 17));
		mapColumnDetails.put(32, new ColumnDetails("Fee_Ccy_Code", 3));
		mapColumnDetails.put(33, new ColumnDetails("Trf_Dte", 8));
		mapColumnDetails.put(34, new ColumnDetails("Trf_Freq", 1));
		mapColumnDetails.put(35, new ColumnDetails("Proxy_ID_Type", 1));
		mapColumnDetails.put(36, new ColumnDetails("Proxy_ID", 34));
		mapColumnDetails.put(37, new ColumnDetails("Payee_Name", 50));
		mapColumnDetails.put(38, new ColumnDetails("Payee_Bank_Code", 3));
		mapColumnDetails.put(39, new ColumnDetails("Event_Rem", 40));
		mapColumnDetails.put(40, new ColumnDetails("Ref_Nbr", 9));
		mapColumnDetails.put(41, new ColumnDetails("From_Dte", 8));
		mapColumnDetails.put(42, new ColumnDetails("To_Dte", 8));
		mapColumnDetails.put(43, new ColumnDetails("Mandate_Id", 35));
		mapColumnDetails.put(44, new ColumnDetails("eDDA_Maint_Action", 1));
		mapColumnDetails.put(45, new ColumnDetails("Source_Trx_Ref_Nbr", 23));
		mapColumnDetails.put(46, new ColumnDetails("Pay_Cat_Purp_Code", 6));
		mapColumnDetails.put(47, new ColumnDetails("Pay_Purp_Code", 6));
		mapColumnDetails.put(48, new ColumnDetails("Device_Id", 64));
		mapColumnDetails.put(49, new ColumnDetails("Mobile_Brand", 64));
		mapColumnDetails.put(50, new ColumnDetails("Platform_Code", 128));
		mapColumnDetails.put(51, new ColumnDetails("Device_Model", 128));
		mapColumnDetails.put(52, new ColumnDetails("Device_OS_Version", 128));
		mapColumnDetails.put(53, new ColumnDetails("IP_Address", 200));
		mapColumnDetails.put(54, new ColumnDetails("Event_Amt_Hke", 17));
		mapColumnDetails.put(55, new ColumnDetails("Event_Ex_Rate", 14));
		mapColumnDetails.put(56, new ColumnDetails("Mrch_Id", 35));
		mapColumnDetails.put(57, new ColumnDetails("Acct_Ccy_Code", 3));
		mapColumnDetails.put(58, new ColumnDetails("Acct_Amt", 17));
		mapColumnDetails.put(59, new ColumnDetails("CDC_Screen_Id", 64));
		mapColumnDetails.put(60, new ColumnDetails("OMB_txn", 1));
		mapColumnDetails.put(61, new ColumnDetails("Multi_App_Rej_Txn_Cnt", 3));
		mapColumnDetails.put(62, new ColumnDetails("Coupon_Code", 20));
		mapColumnDetails.put(63, new ColumnDetails("Actl_Int_Rate", 10));
		mapColumnDetails.put(64, new ColumnDetails("TT_Auto_Route", 1));
		mapColumnDetails.put(65, new ColumnDetails("Suspicious_Activity", 1));
		mapColumnDetails.put(66, new ColumnDetails("Country_Name", 64));
		mapColumnDetails.put(67, new ColumnDetails("Region", 128));
		mapColumnDetails.put(68, new ColumnDetails("Suspicious_Ind", 1));
		mapColumnDetails.put(69, new ColumnDetails("With_Mrch", 1));
		mapColumnDetails.put(70, new ColumnDetails("Cmpy_Name", 100));
		mapColumnDetails.put(71, new ColumnDetails("Doc_Id", 12));
		mapColumnDetails.put(72, new ColumnDetails("Doc_Type", 1));
		mapColumnDetails.put(73, new ColumnDetails("Doc_Country_Code", 2));
		mapColumnDetails.put(74, new ColumnDetails("Mrch_user_Id", 10));
		mapColumnDetails.put(75, new ColumnDetails("Mrch_user_Name", 140));
		mapColumnDetails.put(76, new ColumnDetails("Doc_Cmpy_Name", 100));
		mapColumnDetails.put(77, new ColumnDetails("Doc_Acct_Nbr", 20));
		mapColumnDetails.put(78, new ColumnDetails("Sle_Mrch_Id", 12));
		mapColumnDetails.put(79, new ColumnDetails("Sle_Mrch_Name", 100));
		mapColumnDetails.put(80, new ColumnDetails("Format", 1));
		mapColumnDetails.put(81, new ColumnDetails("Txn_Count", 6));
		mapColumnDetails.put(82, new ColumnDetails("Adv_Freq_Type", 1));
		mapColumnDetails.put(83, new ColumnDetails("Adv_Type", 1));
		mapColumnDetails.put(84, new ColumnDetails("AR_TOKEN", 6));
		mapColumnDetails.put(85, new ColumnDetails("TREASURY_REF", 7));
		mapColumnDetails.put(86, new ColumnDetails("LM_SWEEP_INSTRUCTION_TYPE", 1));
		mapColumnDetails.put(87, new ColumnDetails("LM_FREQUENCY_EXECUTIONDAY", 1));
		mapColumnDetails.put(88, new ColumnDetails("LM_EFFECTIVEDATE", 8));
		mapColumnDetails.put(89, new ColumnDetails("LM_RULE_SETUPDATE", 8));
		mapColumnDetails.put(90, new ColumnDetails("LM_SWEEPING_AMOUNT_THRESHOLD", 17));
		mapColumnDetails.put(91, new ColumnDetails("LM_FPXTXN_NBR", 35));
		mapColumnDetails.put(92, new ColumnDetails("LM_INSTRUCTION_NBR", 20));
		mapColumnDetails.put(93, new ColumnDetails("FPS_REF_NBR", 35));
		mapColumnDetails.put(94, new ColumnDetails("PARTY_INT_NBR", 16));
		mapColumnDetails.put(95, new ColumnDetails("EVENT_CREDIT_ACCT_TYPE", 3));
		mapColumnDetails.put(96, new ColumnDetails("EVENT_CREDIT_ACCT_NBR", 15));
		mapColumnDetails.put(97, new ColumnDetails("FPS_BUS_SERVICE_CD", 10));
		mapColumnDetails.put(98, new ColumnDetails("EVENT_REMITTER_NAME", 140));
		mapColumnDetails.put(99, new ColumnDetails("filler_02", 128));

	}

	@SuppressWarnings("unused")
	public static void main(String[] args) {

		ArrayList<String> eventIdList = new ArrayList<String>();
		int updateCount = 0;
		GenTxnLog2CRM gtl = new GenTxnLog2CRM();
		writeLog(args[1],
				"Started batch GenTxnLog2CRM at " + gtl.getCurrentDateAndTime() + System.getProperty("line.separator")
						+ "Outfile is :" + args[0] + System.getProperty("line.separator"),
				false);
		writeLog(args[1], "Fetching encrypted db credential from properties at " + gtl.getCurrentDateAndTime()
				+ System.getProperty("line.separator"), true);
		Properties prop = gtl.getDBProperties(args[1]);

		writeLog(args[1], "Fetching eligible records from db at " + gtl.getCurrentDateAndTime()
				+ System.getProperty("line.separator"), true);

		int deltaRecordsFound = gtl.setDeltaRecords(args, prop);
		writeLog(args[1], deltaRecordsFound + "Records to be processed found at " + gtl.getCurrentDateAndTime()
				+ System.getProperty("line.separator"), true);
		/* eventIdList = gtl.getDeltaRecords(args, prop); */
		// System.out.println(sql);

		writeLog(args[1],
				"Writing records to out file at " + gtl.getCurrentDateAndTime() + System.getProperty("line.separator"),
				true);

		String result = gtl.process("FL", sql, mapColumnDetails, args[0], args[1], args[2]);

		if (deltaRecordsFound > 0 && "success".equalsIgnoreCase(result)) {

			updateCount = gtl.updateDeltaRecords(args, prop, eventIdList);
			writeLog(args[1], " " + updateCount + " records have been processed...", true);

		}

	}

	private int updateDeltaRecords(String[] args, Properties prop, ArrayList<String> eventIdList) {

		int updatedRowsCount = 0;
		ArrayList<String> eventIds = new ArrayList<String>();

		// No input file, arg[0] is logfilename and arg[1] is outfilename.
		String LOG_FILE_NAME = args[1];
		String username = prop.getProperty("username");
		String password = prop.getProperty("password");
		String hostname = prop.getProperty("hostname");
		String port = prop.getProperty("port");
		String servicename = prop.getProperty("servicename");
		String url = "jdbc:oracle:thin:@//" + hostname + ":" + port + "/" + servicename;
		// System.out.println(prop.toString());
		try (Connection conn = DriverManager.getConnection(

				url, username, password)) {

			if (conn != null) {
				System.out.println("Connected to the database!");
			} else {
				System.out.println("Exception occured while making DB connection!");
				writeLog(LOG_FILE_NAME, " exception occured while getting connection", true);

			}
			conn.setAutoCommit(true);

			String query = "update digx_cz_crm_event3_details set BATCH_PROCESSED_DATE=sysdate where BATCH_PROCESSED_DATE = to_date('1990-01-01 11:11:11','yyyy-MM-dd hh24:mi:ss') and (CHNL_ID is null or CHNL_ID <> 'ELE-HTH')";

			Statement stmt = conn.createStatement();
			updatedRowsCount = stmt.executeUpdate(query);
		}

		catch (Exception e) {
			// TODO Auto-generated catch block
			writeLog(LOG_FILE_NAME, "exception occured while fetching records in genTxnLog2CRM batch" + e.getMessage(),
					true);
			e.printStackTrace();
		}
		return updatedRowsCount;

	}

	@Override
	public void prepareOutFileTrailer(ArrayList bodyList, String outFileType, String outFileName,
			Map<Integer, ColumnDetails> mapColumnDetails, String batchDate) {
		StringBuffer sb = new StringBuffer();
		String text = "20" + batchDate;
		int batchdateint = Integer.parseInt(text);
		int allRecordCount = bodyList.size();
		int checksum = batchdateint + allRecordCount;
		String checksumStr = String.valueOf(checksum);
		sb.append("99");
		sb.append(adjustLengthPreFiller(String.valueOf(bodyList.size()), 15, '0'));
		// sb.append(adjustLengthPostFiller(text, 8, ' '));
		sb.append(adjustLengthPreFiller(checksumStr, 15, '0'));
		sb.append(whitespaces(2268));
		WriteOutFileTrailer(outFileName, sb.toString());

	}

	@Override
	public void prepareOutFileHeader(ResultSet rs, String outFileType, String outFileName,
			Map<Integer, ColumnDetails> mapColumnDetails, String batchDate) {
		StringBuffer sb = new StringBuffer();
		String text = "20" + batchDate;
		sb.append("00");
		sb.append(adjustLengthPostFiller(text, 8, ' '));
		sb.append("CDCCRM00030003");
		sb.append(whitespaces(2276));
		WriteOutFileHeader(outFileName, sb.toString());

	}

	public int setDeltaRecords(String[] args, Properties prop) {
		int updatedRowsCount = 0;
		ArrayList<String> eventIds = new ArrayList<String>();

		// No input file, arg[0] is logfilename and arg[1] is outfilename.
		String LOG_FILE_NAME = args[1];
		String username = prop.getProperty("username");
		String password = prop.getProperty("password");
		String hostname = prop.getProperty("hostname");
		String port = prop.getProperty("port");
		String servicename = prop.getProperty("servicename");
		String url = "jdbc:oracle:thin:@//" + hostname + ":" + port + "/" + servicename;
		// System.out.println(prop.toString());
		try (Connection conn = DriverManager.getConnection(

				url, username, password)) {

			if (conn != null) {
				System.out.println("Connected to the database!");
			} else {
				System.out.println("Exception occured while making DB connection!");
				writeLog(LOG_FILE_NAME, "exception");
			}
			int index = 0;

			String query = "update digx_cz_crm_event3_details set BATCH_PROCESSED_DATE = to_date('1990-01-01 11:11:11','yyyy-MM-dd hh24:mi:ss') where  BATCH_PROCESSED_DATE  is null and EVENT_ACTV_TYPE_CODE is not null and user_id  <> 'anonymous' and (CHNL_ID is null or CHNL_ID <> 'ELE-HTH') ";
			System.out.println("set Delta done...");

			Statement stmt = conn.createStatement();
			updatedRowsCount = stmt.executeUpdate(query);

			if (updatedRowsCount == 0) {
				writeLog(LOG_FILE_NAME,
						System.getProperty("line.separator")
								+ " NO new Delta records found in digx_cz_crm_event3_details "
								+ System.getProperty("line.separator"),
						true);
			} else {
				writeLog(LOG_FILE_NAME, System.getProperty("line.separator") + updatedRowsCount
						+ " new Delta Records Identified... " + System.getProperty("line.separator"), true);
			}

		}

		catch (Exception e) {
			// TODO Auto-generated catch block
			writeLog(LOG_FILE_NAME, "exception occured while fetching records in genTxnLog2CRM batch" + e.getMessage(),
					true);
			e.printStackTrace();
		}
		return updatedRowsCount;
	}

	/*
	 * public ArrayList<String> getDeltaRecords(String[] args, Properties prop) {
	 * int updatedRowsCount; ArrayList<String> eventIds = new ArrayList<String>();
	 * 
	 * // No input file, arg[0] is logfilename and arg[1] is outfilename. String
	 * LOG_FILE_NAME = args[1]; String username = prop.getProperty("username");
	 * String password = prop.getProperty("password"); String hostname =
	 * prop.getProperty("hostname"); String port = prop.getProperty("port"); String
	 * servicename = prop.getProperty("servicename"); String url =
	 * "jdbc:oracle:thin:@//" + hostname + ":" + port + "/" + servicename; //
	 * System.out.println(prop.toString()); try (Connection conn =
	 * DriverManager.getConnection(
	 * 
	 * url, username, password)) {
	 * 
	 * if (conn != null) { System.out.println("Connected to the database!"); } else
	 * { System.out.println("Exception occured while making DB connection!");
	 * writeLog(LOG_FILE_NAME, "exception"); } int index = 0;
	 * 
	 * String query =
	 * "select count(*) from  digx_cz_crm_event3_details WHERE BATCH_PROCESSED_DATE = to_date('1990-01-01 11:11:11','yyyy-MM-dd hh24:mi:ss') and EVENT_ACTV_TYPE_CODE is not null and user_id  <> 'anonymous' "
	 * ; System.out.println(query);
	 * 
	 * Statement stmt = conn.createStatement(); ResultSet rs =
	 * stmt.executeQuery(query);
	 * 
	 * while (rs.next()) {
	 * 
	 * index = rs.getInt(1);
	 * 
	 * }
	 * 
	 * if (index == 0) { writeLog(LOG_FILE_NAME,
	 * System.getProperty("line.separator") +
	 * " NO new Delta records found in digx_cz_crm_event3_details " +
	 * System.getProperty("line.separator"), true); } }
	 * 
	 * catch (Exception e) { // TODO Auto-generated catch block
	 * writeLog(LOG_FILE_NAME,
	 * "exception occured while fetching records in genTxnLog2CRM batch" +
	 * e.getMessage(), true); e.printStackTrace(); } return eventIds; }
	 */

}
