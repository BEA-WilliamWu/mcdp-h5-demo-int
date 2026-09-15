package inboundbatchprocessor;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;

import model.QueryParam;
import model.validate_and_send_bounce.ValidateAndSendBounceModel;

public class ValidateAndSendBounceNotify extends GenericInBoundProcessor {

	ArrayList WMList;
	ArrayList SysList;

	public ValidateAndSendBounceNotify() {
		super();
	}

	boolean isExcptionOccured = false;

	public static void main(String[] args) {

		ValidateAndSendBounceNotify aio = new ValidateAndSendBounceNotify();
		Properties prop = aio.getDBProperties(args[0]);
		aio.writeLog(args[0], "Started batch ValidateAndSendBounceNotify at " + aio.getCurrentDateAndTime()
				+ System.getProperty("line.separator"), false);
		String status = "";

		String statusETS = "";
		try {
			status = aio.getDatanPrintReport(args, prop);
			// ETS bounce back handling
			statusETS = aio.getDatanPrintReportETS(args, prop);

		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			System.out.println("Exception exception occured while processing validate and bounce back processor");
		}

		if (status.equalsIgnoreCase("success") && statusETS.equalsIgnoreCase("success")) {
			aio.writeLog(args[0], System.getProperty("line.separator") + "success[ValidateAndSendBounceNotify].", true);
			System.out.println("batch processed records successfully");
		}
	}

	public String getDatanPrintReport(String[] args, Properties prop) throws Exception {
		Map<String, String> duplicateEmail = new HashMap<String, String>();
		Map<String, String> duplicateSMS = new HashMap<String, String>();
		String batchstatus = "success";
		ArrayList<String> refNumberList = new ArrayList<String>();
		ArrayList<ValidateAndSendBounceModel> recordList = new ArrayList<ValidateAndSendBounceModel>();
		WMList = new ArrayList<String>();
		SysList = new ArrayList<String>();
		int updatedRowsCount = 0;
		// No input file, arg[0] is logfilename
		String LOG_FILE_NAME = args[0];
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

			conn.setAutoCommit(true);
			Statement stmt = conn.createStatement();

			// *************************** BOUNCED EMAIL
			// **************************************

			// Retrieving high risk bounced emails.

			String subject = "";
			/*
			 * String refnumber = ""; String txntype = "";
			 * 
			 * String activityid = ""; String actionid = ""; String eventid = ""; String
			 * userid = ""; String partyid = ""; String org_ref_no = ""; String failed_mode
			 * = ""; String alt_sms = ""; String alt_email = ""; String alt_webmail = "";
			 * String alt_office_email = ""; String alt_acc_holder_ap = ""; String
			 * alt_sys_adm = ""; String webmail = ""; String reminder_afterLogin = "";
			 * String isProcessed = ""; String processedChannelsList = ""; String
			 * eventSendtoOfficeMail = ""; String officeEmail = "";
			 */
			String query = "SELECT M.REFNUMBER,\r\n" + "       M.TXNTYPE,\r\n" + "       M.SUBJECT,\r\n"
					+ "       M.CUSTOMERID,\r\n" + "       M.PARTYID,\r\n" + "       M.ACTIVITYID,\r\n"
					+ "       M.ACTIONID,\r\n" + "       M.EVENTID,\r\n" + "       M.ORG_REF_NO,\r\n"
					+ "       (select CASE\r\n" + "                 WHEN mobileno IS NOT NULL THEN\r\n"
					+ "                  mobileno\r\n" + "                 when mobileno is null then\r\n"
					+ "                  ''\r\n" + "               END\r\n" + "          from digx_um_userprofile\r\n"
					+ "         where email = M.RECIPIENTID\r\n"
					+ "           and u_name = M.CUSTOMERID) ALT_MOBILE_NO,\r\n" + "       (select OFFICE_EMAIL\r\n"
					+ "          from digx_pi_party_preferences\r\n"
					+ "         where partyid = M.PARTYID) ALT_OFFICE_EMAIL,\r\n"
					+ "         m.recipientid bounced_email\r\n"
					+ "  FROM DIGX_CZ_EMAIL_MNG M, DIGX_CZ_BATCH_BOUNCE_BACK_CCBEMAIL C\r\n" + " WHERE M.eventid in\r\n"
					+ "       (SELECT DISTINCT EVENTID FROM DIGX_CZ_BATCH_HIGH_ALERT_EVENT_LIST WHERE EVENTID NOT IN ('HTH_PROFILE_CONTACT_UPDATED_API','HTH_PROFILE_CONTACT_UPDATED_AP','HTH_PROFILE_EMAIL_UPDATED_API','HTH_PROFILE_EMAIL_UPDATED_AP','HTH_PROFILE_MOBILE_UPDATED_API','HTH_PROFILE_MOBILE_UPDATED_AP'))\r\n"
					+ "   AND M.REFNUMBER = C.SRC_SYS_REF_NUM\r\n"
					+ "   AND M.PARTYID=(select party_id from digx_um_userparty_relation  where user_id=M.CUSTOMERID and rownum=1) \r\n"
					+ "   AND C.IS_VALSENDBOUNCENOTIF_PROCESSED = 'N'";
			System.out.println(query);
			ValidateAndSendBounceModel record;
			ResultSet rs = stmt.executeQuery(query);
			refNumberList = new ArrayList<String>();
			while (rs.next()) {

				// refnumber = rs.getString(1);
				// txntype = rs.getString(2);
				// subject = rs.getString(3);
				// userid = rs.getString(4);
				// partyid = rs.getString(5);
				// activityid = rs.getString(6);
				// actionid = rs.getString(7);
				// eventid = rs.getString(8);
				// org_ref_no = rs.getString(9);
				// alt_sms = rs.getString(10);
				// alt_office_email = rs.getString(11);
				// officeEmail = rs.getString(11);

				record = new ValidateAndSendBounceModel();

				record.setRefnumber(rs.getString(1));
				record.setTxntype(rs.getString(2));
				subject = replaceSQuote(rs.getString(3));
				record.setSubject(subject);
				record.setUserid(rs.getString(4));
				record.setPartyid(rs.getString(5));
				record.setActivityid(rs.getString(6));
				record.setActionid(rs.getString(7));
				record.setEventid(rs.getString(8));
				record.setOrg_ref_no(rs.getString(9));
				record.setAlt_sms(rs.getString(10));
				record.setAlt_office_email(rs.getString(11));
				record.setBounced_email(rs.getString(12));
				record.setStr_date_last_updated(getCurrentDateAndTime("dd-MM-yy"));
				// record.setAlt_webmail(alt_webmail);
				// record.setReminder_afterLogin(reminder_afterLogin);
				// record.setAlt_acc_holder_ap(alt_acc_holder_ap);
				// record.setAlt_sys_adm(alt_sys_adm);

				record.setBounceType("EMAIL");
				boolean recordEligible4Processing = false;

				if (null != duplicateEmail.get(record.getUserid())) {
					if (duplicateEmail.get(record.getUserid()).equalsIgnoreCase(record.getPartyid())) {
						// same record already processed
						System.out.println("Skipped processing [** Email **] for " + record.getRefnumber()
								+ " the same userid : " + record.getUserid() + " and partyid : " + record.getPartyid()
								+ " is already processed !");
					} else {
						duplicateEmail.put(record.getUserid(), record.getPartyid());
						recordEligible4Processing = true;
						// recordList.add(record);
					}
				} else {

					duplicateEmail.put(record.getUserid(), record.getPartyid());
					recordEligible4Processing = true;
					// recordList.add(record);
				}

				if (recordEligible4Processing == true && null != record.getPartyid()) {
					System.out.println("        Processing [** Email **]     " + record.getRefnumber()
							+ "     with userid : " + record.getUserid() + "  and partyid : " + record.getPartyid());

					try {

						// CC Merchant phase 2 requirement, for Merchant bounce back only send webmail.
						if (record.getEventid().equalsIgnoreCase("E_STATEMENT_REMINDER_EMAIL_MERCHANT")) {
							PrepareEmailBounceMerchant(record, conn, LOG_FILE_NAME, args);
						} else {
							PrepareEmailBounceKundli(record, conn, LOG_FILE_NAME, args);
						}
						recordList.add(record);
					} catch (Exception e) {
						System.out.println("mobile_code and mobile number didn't match for user");
					}
				}
				refNumberList.add(record.getRefnumber());

				// TO DO : TO DECIDE IN KUNDLI METHOD reminder_afterLogin = "Y";

				// NO MORE ALT NOTIFICATION TO OFFICE IF IN HIGH RISK TRANSACTION
				// eventSendtoOfficeMail = rs.getString("IS_OFFICE_MAIL");

			}

			// Now adding batch table for email bounce records

			Iterator<ValidateAndSendBounceModel> it = recordList.iterator();

			ValidateAndSendBounceModel temp;
			while (it.hasNext()) {

				temp = it.next();
				if (null == temp.getAlt_sms()) {
					temp.setAlt_sms("");
				}
				query = "insert into DIGX_CZ_BATCH_VALIDATE_AND_SEND_BOUNCE(subject,org_ref_no,refnumber,activityid,actionid,txntype,eventid,userid,party_id,bounced_mode,alt_sms,alt_email,alt_webmail,alt_office_email,alt_acc_holder_ap,alt_sys_adm,reminder_afterLogin,RECORD_LAST_UPDATED) values('"
						+ temp.getSubject() + "','" + temp.getOrg_ref_no() + "','" + temp.getRefnumber() + "','"
						+ temp.getActivityid() + "','" + temp.getActionid() + "','" + temp.getTxntype() + "','"
						+ temp.getEventid() + "','" + temp.getUserid() + "','" + temp.getPartyid() + "','EMAIL','"
						+ temp.getAlt_sms() + "','" + temp.getAltrnate_email() + "','" + temp.getAlt_webmail() + "','"
						+ temp.getAlt_office_email_to_populate() + "','" + temp.getAlt_acc_holder_ap() + "','"
						+ temp.getAlt_sys_adm() + "','" + temp.getReminder_afterLogin() + "',to_date('"
						+ temp.getStr_date_last_updated() + "','DD-MM-YY')" + ")";

				// System.out.println(query);
				stmt.executeUpdate(query);
			}

			// Now updating DIGX_CZ_BATCH_BOUNCE_BACK_CCBEMAIL SRC_SYS_REF_NUM
			// IS_VALSENDBOUNCENOTIF_PROCESSED

			query = "Update DIGX_CZ_BATCH_BOUNCE_BACK_CCBEMAIL set IS_VALSENDBOUNCENOTIF_PROCESSED='Y' where IS_VALSENDBOUNCENOTIF_PROCESSED ='N'";
			// System.out.println(query);
			updatedRowsCount = stmt.executeUpdate(query);

			writeLog(LOG_FILE_NAME, System.getProperty("line.separator"), true);
			writeLog(LOG_FILE_NAME, "Bounced email's " + updatedRowsCount + " records have been processed.", true);

			StringBuffer out = new StringBuffer();
			out.append("Account;Action;Status;Error Code;Error Message;Email Address / SMS;Date");
			out.append(System.getProperty("line.separator"));

			record = new ValidateAndSendBounceModel();

			// **************************************
			// SMS*****************************************************************

			query = "SELECT M.REFNUMBER,\r\n" + "       M.TXNTYPE,\r\n" + "       M.SUBJECT,\r\n"
					+ "       M.CUSTOMERID,\r\n" + "       M.PARTYID,\r\n" + "       M.ACTIVITYID,\r\n"
					+ "       M.ACTIONID,\r\n" + "       M.EVENTID,\r\n" + "       M.ORG_REF_NO,\r\n"
					+ "       (select CASE\r\n" + "                 WHEN EMAIL IS NOT NULL THEN\r\n"
					+ "                  EMAIL\r\n" + "                 when EMAIL is null then\r\n"
					+ "                  ''\r\n" + "               END\r\n"
					+ "          from digx_um_userprofile u,digx_cz_um_extensiondata x\r\n"
					+ "         where x.user_id=u.u_name and NVL(x.mobile_code,'')||NVL(u.mobileno,'') = M.RECIPIENTID\r\n"
					+ "           and u_name = M.CUSTOMERID) ALT_EMAIL,\r\n" + "       (select OFFICE_EMAIL\r\n"
					+ "          from digx_pi_party_preferences\r\n"
					+ "         where partyid = M.PARTYID) ALT_OFFICE_EMAIL,  m.recipientid bounced_sms  FROM DIGX_CZ_EMAIL_MNG M, DIGX_CZ_BATCH_BOUNCE_BACK_CCBSMS C\r\n"
					+ " WHERE M.eventid in\r\n"
					+ "       (SELECT DISTINCT EVENTID FROM DIGX_CZ_BATCH_HIGH_ALERT_EVENT_LIST WHERE EVENTID NOT IN ('HTH_PROFILE_CONTACT_UPDATED_API','HTH_PROFILE_CONTACT_UPDATED_AP','HTH_PROFILE_EMAIL_UPDATED_API','HTH_PROFILE_EMAIL_UPDATED_AP','HTH_PROFILE_MOBILE_UPDATED_API','HTH_PROFILE_MOBILE_UPDATED_AP'))\r\n"
					+ "   AND M.REFNUMBER = C.SRC_SYS_REF_NUM\r\n"
					+ "   AND M.PARTYID=(select party_id from digx_um_userparty_relation  where user_id=M.CUSTOMERID and rownum=1) \r\n"
					+ "   AND C.IS_VALSENDBOUNCENOTIF_PROCESSED = 'N'";
			System.out.println(query);
			recordList = new ArrayList<ValidateAndSendBounceModel>();
			rs = stmt.executeQuery(query);
			refNumberList = new ArrayList<String>();
			while (rs.next()) {
				record = new ValidateAndSendBounceModel();
				record.setRefnumber(rs.getString(1));
				record.setTxntype(rs.getString(2));
				record.setSubject(rs.getString(3));
				record.setUserid(rs.getString(4));
				record.setPartyid(rs.getString(5));
				record.setActivityid(rs.getString(6));
				record.setActionid(rs.getString(7));
				record.setEventid(rs.getString(8));
				record.setOrg_ref_no(rs.getString(9));
				record.setAlt_email(rs.getString(10));
				record.setAlt_office_email(rs.getString(11));
				// record.setAlt_webmail(alt_webmail);
				// record.setReminder_afterLogin(reminder_afterLogin);
				record.setBounced_sms(rs.getString(12));
				String tmpOfficeEmailAddress = "";
				String tmpUserEmailAddress = "";
				String tmpMobileCode = "";
				record.setStr_date_last_updated(getCurrentDateAndTime("dd-MM-yy"));
				// Storing officeEmailAddress for comparison

				query = "SELECT P.OFFICE_EMAIL FROM DIGX_PI_PARTY_PREFERENCES P WHERE P.PARTYID = '"
						+ record.getPartyid() + "'";
				// System.out.println(query);

				Statement stmtk = conn.createStatement();
				ResultSet rsk = stmtk.executeQuery(query);

				while (rsk.next()) {

					tmpOfficeEmailAddress = rsk.getString(1);
				}

				query = "SELECT P.EMAIL FROM digx_um_userprofile P WHERE P.u_name = '" + record.getUserid() + "'";
				// System.out.println(query);

				// stmtk = conn.createStatement();
				rsk = stmtk.executeQuery(query);

				while (rsk.next()) {

					tmpUserEmailAddress = rsk.getString(1);

				}

				query = "select NVL(x.mobile_code,'') from digx_cz_um_extensiondata x where x.user_id='"
						+ record.getUserid() + "'";
				// System.out.println(query);

				// stmtk = conn.createStatement();
				rsk = stmtk.executeQuery(query);

				while (rsk.next()) {

					tmpMobileCode = rsk.getString(1);
					if (null == tmpMobileCode) {
						tmpMobileCode = "";
					}

				}

				record.setBounceType("SMS");

				boolean recordEligible4Processing = false;

				if (null != duplicateSMS.get(record.getUserid())) {
					if (duplicateSMS.get(record.getUserid()).equalsIgnoreCase(record.getPartyid())) {
						// same record already processed
						System.out.println("Skipped processing [** SMS **  ] for " + record.getRefnumber()
								+ " the same userid : " + record.getUserid() + " and partyid : " + record.getPartyid()
								+ " is already processed !");
					} else {
						duplicateSMS.put(record.getUserid(), record.getPartyid());
						recordEligible4Processing = true;

						// recordList.add(record);
					}
				} else {
					duplicateSMS.put(record.getUserid(), record.getPartyid());
					recordEligible4Processing = true;
					// recordList.add(record);
				}

				if (recordEligible4Processing == true && null != record.getPartyid()) {
					System.out.println("        Processing [** SMS **  ]     " + record.getRefnumber()
							+ "     with userid : " + record.getUserid() + "  and partyid : " + record.getPartyid());
					try {
						// CC Merchant phase 2 requirement, for Merchant bounce back only send webmail.
						if (record.getEventid().equalsIgnoreCase("BOUNCE_BACK_ESTMT_MERCHANT_SMS")) {
							processUserSMSMerchant(stmtk, rsk, record, conn, LOG_FILE_NAME, tmpOfficeEmailAddress,
									tmpUserEmailAddress, tmpMobileCode, args);
						} else {
							processUserSMS(stmtk, rsk, record, conn, LOG_FILE_NAME, tmpOfficeEmailAddress,
									tmpUserEmailAddress, tmpMobileCode, args);
						}
						recordList.add(record);
					} catch (Exception e) {
						// e.printStackTrace();
						System.out.println("mobile_code and mobile number didn't match for user");
					}
				}

				refNumberList.add(record.getRefnumber());

				if (null != stmtk) {
					stmtk.close();
				}

				if (null != rsk) {
					rsk.close();
				}

			}

			// Now adding batch table for SMS bounce records

			Iterator<ValidateAndSendBounceModel> it1 = recordList.iterator();

			while (it1.hasNext()) {

				temp = it1.next();
				if (null == temp.getAlt_sms()) {
					temp.setAlt_sms("");
				}
				query = "insert into DIGX_CZ_BATCH_VALIDATE_AND_SEND_BOUNCE(subject,org_ref_no,refnumber,activityid,actionid,txntype,eventid,userid,party_id,bounced_mode,alt_sms,alt_email,alt_webmail,alt_office_email,alt_acc_holder_ap,alt_sys_adm,reminder_afterLogin,RECORD_LAST_UPDATED) values('"
						+ temp.getSubject() + "','" + temp.getOrg_ref_no() + "','" + temp.getRefnumber() + "','"
						+ temp.getActivityid() + "','" + temp.getActionid() + "','" + temp.getTxntype() + "','"
						+ temp.getEventid() + "','" + temp.getUserid() + "','" + temp.getPartyid() + "','SMS','"
						+ temp.getAlt_sms() + "','" + temp.getAlt_email() + "','" + temp.getAlt_webmail() + "','"
						+ temp.getAlt_office_email_to_populate() + "','" + temp.getAlt_acc_holder_ap() + "','"
						+ temp.getAlt_sys_adm() + "','" + temp.getReminder_afterLogin() + "',to_date('"
						+ temp.getStr_date_last_updated() + "','DD-MM-YY')" + ")";

				// System.out.println(query);
				stmt.executeUpdate(query);
			}

			// Now updating DIGX_CZ_BATCH_BOUNCE_BACK_CCBSMS SRC_SYS_REF_NUM
			// IS_VALSENDBOUNCENOTIF_PROCESSED

			query = "Update DIGX_CZ_BATCH_BOUNCE_BACK_CCBSMS set IS_VALSENDBOUNCENOTIF_PROCESSED='Y' where IS_VALSENDBOUNCENOTIF_PROCESSED ='N'";
			// System.out.println(query);
			updatedRowsCount = stmt.executeUpdate(query);

			writeLog(LOG_FILE_NAME, System.getProperty("line.separator"), true);
			writeLog(LOG_FILE_NAME, "Bounced SMS's " + updatedRowsCount + " records have been processed.", true);

		} catch (

		SQLException e) {
			// TODO Auto-generated catch block
			batchstatus = "failure";
			writeLog(LOG_FILE_NAME, "exception " + e.getMessage());
			e.printStackTrace();
		}
		return batchstatus;
	}

	public String getDatanPrintReportETS(String[] args, Properties prop) throws Exception {
		Map<String, String> duplicateEmail = new HashMap<String, String>();
		String batchstatus = "success";
		ArrayList<String> refNumberList = new ArrayList<String>();
		ArrayList<ValidateAndSendBounceModel> recordList = new ArrayList<ValidateAndSendBounceModel>();
		WMList = new ArrayList<String>();
		SysList = new ArrayList<String>();
		int updatedRowsCount = 0;
		// No input file, arg[0] is logfilename
		String LOG_FILE_NAME = args[0];
		String username = prop.getProperty("username");
		String password = prop.getProperty("password");
		String hostname = prop.getProperty("hostname");
		String port = prop.getProperty("port");
		String servicename = prop.getProperty("servicename");
		String url = "jdbc:oracle:thin:@//" + hostname + ":" + port + "/" + servicename;
		try (Connection conn = DriverManager.getConnection(

				url, username, password)) {

			if (conn != null) {
				System.out.println("Connected to the database!");
			} else {
				System.out.println("Exception occured while making DB connection!");
				writeLog(LOG_FILE_NAME, "exception");
			}

			conn.setAutoCommit(true);
			Statement stmt = conn.createStatement();

			// *************************** BOUNCED EMAIL ETS
			// **************************************

			// Retrieving bounced emails sent by ETS .

			String query = "SELECT C.SRC_SYS_REF_NUM,\r\n" + "       C.RECORD_LAST_UPDATED,\r\n"
					+ "       C.IS_DELETED_RECORD,\r\n" + "       C.MSG_ID,\r\n" + "       C.SENDER_ADDR,\r\n"
					+ "       C.EMAIL_RECI_ADDR,\r\n" + "       C.BOUNCE_BACK_MSG,\r\n"
					+ "       C.BATCH_FILE_NAME,\r\n" + "       C.FILLER,\r\n" + "       P.PARTYID\r\n"
					+ "     FROM DIGX_CZ_BATCH_BOUNCE_BACK_ETSEMAIL C, \r\n"
					+ "     	 DIGX_PI_PARTY_PREFERENCES P \r\n"
					+ "     WHERE C.IS_VALSENDBOUNCENOTIF_PROCESSED = 'N' \r\n"
					+ "		AND P.OFFICE_EMAIL = C.EMAIL_RECI_ADDR "
					+ "		AND P.PARTYID = SUBSTR(trim(C.SRC_SYS_REF_NUM),3,14)";

			System.out.println(query);
			ValidateAndSendBounceModel record;
			ResultSet rs = stmt.executeQuery(query);
			refNumberList = new ArrayList<String>();
			while (rs.next()) {
				record = new ValidateAndSendBounceModel();
				record.setRefnumber(rs.getString(1));
				record.setPartyid(rs.getString(10));
				record.setActivityid("com.ofss.digx.cz.bea.domain.ets.dummyETSAlert");
				record.setActionid("A");
				record.setEventid("DUMMY_ETS_ALERT_EMAIL");
				record.setBounced_email(rs.getString(6));
				record.setStr_date_last_updated(getCurrentDateAndTime("dd-MM-yy"));
				record.setBounceType("EMAIL");
				record.setAlt_office_email_to_populate(rs.getString(6));

				boolean recordEligible4Processing = false;

				if (null != duplicateEmail.get(record.getPartyid() + record.getBounced_email())) {
					if (duplicateEmail.get(record.getPartyid() + record.getBounced_email())
							.equalsIgnoreCase(record.getPartyid() + record.getBounced_email())) {
						// same record already processed
						System.out.println("ETS Skipped processing [** Email **] for " + record.getRefnumber()
								+ " the same mail : " + record.getBounced_email() + " and partyid : "
								+ record.getPartyid() + " is already processed !");
					} else {
						duplicateEmail.put(record.getPartyid() + record.getBounced_email(),
								record.getPartyid() + record.getBounced_email());
						recordEligible4Processing = true;
					}
				} else {
					duplicateEmail.put(record.getPartyid() + record.getBounced_email(),
							record.getPartyid() + record.getBounced_email());
					recordEligible4Processing = true;
				}

				if (recordEligible4Processing == true && null != record.getPartyid() && "" != record.getPartyid()) {
					System.out.println(
							"       ETS Processing [** Email **]     " + record.getRefnumber() + "  and partyid : "
									+ record.getPartyid() + "  and office mail: " + record.getAlt_office_email());
					try {
						PrepareEmailBounceKundliETS(record, conn, LOG_FILE_NAME, args);
						recordList.add(record);
						System.out.println("ETS recordList size is: " + recordList.size());
					} catch (Exception e) {
						System.out.println("PrepareEmailBounceKundliETS exception...");
					}
				}
				refNumberList.add(record.getRefnumber());
				// System.out.println("refNumberList size is: " + refNumberList.size());
			}

			// Now adding batch table for email bounce records

			Iterator<ValidateAndSendBounceModel> it = recordList.iterator();

			ValidateAndSendBounceModel temp;
			while (it.hasNext()) {

				temp = it.next();
				System.out.println("ValidateAndSendBounceModel content is: " + temp.toString());
				if (null == temp.getAlt_sms()) {
					temp.setAlt_sms("");
				}
				query = "insert into DIGX_CZ_BATCH_VALIDATE_AND_SEND_BOUNCE(subject,org_ref_no,refnumber,activityid,actionid,txntype,eventid,userid,party_id,bounced_mode,alt_sms,alt_email,alt_webmail,alt_office_email,alt_acc_holder_ap,alt_sys_adm,reminder_afterLogin,RECORD_LAST_UPDATED) values('"
						+ temp.getSubject() + "','" + temp.getOrg_ref_no() + "','" + temp.getRefnumber() + "','"
						+ temp.getActivityid() + "','" + temp.getActionid() + "','" + temp.getTxntype() + "','"
						+ temp.getEventid() + "','" + temp.getUserid() + "','" + temp.getPartyid() + "','EMAIL','"
						+ temp.getAlt_sms() + "','" + temp.getAltrnate_email() + "','" + temp.getAlt_webmail() + "','"
						+ temp.getAlt_office_email_to_populate() + "','" + temp.getAlt_acc_holder_ap() + "','"
						+ temp.getAlt_sys_adm() + "','" + temp.getReminder_afterLogin() + "',to_date('"
						+ temp.getStr_date_last_updated() + "','DD-MM-YY')" + ")";

				System.out.println(query);
				stmt.executeUpdate(query);
			}

			// Now updating DIGX_CZ_BATCH_BOUNCE_BACK_ETSEMAIL
			// IS_VALSENDBOUNCENOTIF_PROCESSED

			query = "Update DIGX_CZ_BATCH_BOUNCE_BACK_ETSEMAIL set IS_VALSENDBOUNCENOTIF_PROCESSED='Y' where IS_VALSENDBOUNCENOTIF_PROCESSED ='N'";

			updatedRowsCount = stmt.executeUpdate(query);

			writeLog(LOG_FILE_NAME, System.getProperty("line.separator"), true);
			writeLog(LOG_FILE_NAME, "ETS Bounced email's " + updatedRowsCount + " records have been processed.", true);

			StringBuffer out = new StringBuffer();
			out.append("Account;Action;Status;Error Code;Error Message;Email Address / SMS;Date");
			out.append(System.getProperty("line.separator"));
		}
		return batchstatus;
	}

	public void sendWebMail(String wmUserId, String partyid, Connection conn, String LOG_FILE_NAME)
			throws SQLException {

		String qrybbNextSeq = "select 'BB'||BounceBackWMSeq.NEXTVAL id from dual";
		String bbNextSeq = "";
		String qryInsMBMUSER = "";
		String qryInsMBMSG = "";
		String qryInsMBMLR = "";

		Statement stmt = conn.createStatement();

		System.out.println("Sending webmail to user " + wmUserId);
		writeLog(LOG_FILE_NAME, System.getProperty("line.separator") + "Sending webmail to user " + wmUserId, true);

		String querySubject = "select prop_value from DIGX_CZ_FW_CONFIG_ALL_O where prop_id='MAILBOX_MESSAGE_BNCBK_SUBJ3'";
		String queryBody = "select prop_value from DIGX_CZ_FW_CONFIG_ALL_O where prop_id='MAILBOX_MESSAGE_BNCBK_BODY3'";

		String subject = "BEA Corporate Online: Registered Contact Information Update Reminder 東亞企業網上銀行：登記聯絡資料更新提醒";
		String body = "";

		ByteBuffer byteBuffer = StandardCharsets.UTF_8
				.encode("<p>Dear Valued Customer,</p><p> </p><p>BEA Corporate Online Account No.:");

		String bodyeng1 = new String(byteBuffer.array(), StandardCharsets.UTF_8);

		byteBuffer = StandardCharsets.UTF_8.encode(
				" </p><p> </p><p>According to our records, we could not deliver a recent transaction/service notification to your registered mobile no. and email address.</p><p> </p><p>To ensure you can receive our transaction/service notifications, please notify your system administrator to update your registered mobile no. and email address as soon as possible or the execution of the relevant transaction/service may be delayed without further notice.</p><p></p><p>If your contact information has been updated recently, please ignore this message.</p><p> </p><p>For enquiries, please call us on (852) 2211 1321.<br/>Thank you.</p><p> </p><p>Yours faithfully, <br/>The Bank of East Asia, Limited</p><p> </p><p>If there are any discrepancies between the English and Chinese versions of this email, the English version shall apply and prevail. This is a system-generated email – please do not reply to this email or via any hyperlinks in the message.</p><p> </p><p>(The Chinese version of this message is provided below.)</p>");

		String bodyeng2 = new String(byteBuffer.array(), StandardCharsets.UTF_8);

		byteBuffer = StandardCharsets.UTF_8.encode("<p> </p><p>尊貴的客戶：</p><p> </p><p>東亞企業網上銀行賬戶號碼：");

		String bodychn1 = new String(byteBuffer.array(), StandardCharsets.UTF_8);

		byteBuffer = StandardCharsets.UTF_8.encode(
				"  </p><p> </p><p>根據本行記錄，最近發出的交易／服務通知未能傳送至閣下已登記的流動電話號碼及電郵地址。</p><p> </p><p>為確保閣下能收到本行的交易／服務通知，請盡快向系統管理員更新閣下的登記流動電話號碼及電郵地址，否則有關交易／服務的執行可能有所延誤而未能另行通知。</p><p> </p><p>如閣下最近已更新聯絡資料，則毋需理會此訊息。</p><p> </p><p>如有垂詢，請致電本行：(852) 2211 1321。<br/>感謝使用本行服務。</p><p> </p><p>謹啓 <br/>東亞銀行有限公司</p><p> </p><p>中英文本文義如有歧異，概以英文本為準。此乃電腦系統發出之電郵，請勿回覆此電郵和電郵內的超連結。</p>");

		String bodychn2 = new String(byteBuffer.array(), StandardCharsets.UTF_8);

		ResultSet rs = stmt.executeQuery(qrybbNextSeq);
		while (rs.next()) {
			bbNextSeq = rs.getString("id");
		}

		conn.setAutoCommit(true);
		qryInsMBMUSER = "insert into DIGX_CO_MAILBOX_MAILER_USER (ID, MESSAGE_ID, USER_NAME, USER_ID, SUBJECT, PRIORITY, MSG_STATUS, RECEIVED_DATE, DISMISSED, CREATED_BY, CREATION_DATE, LAST_UPDATED_BY, LAST_UPDATED_DATE, VERSION, DETERMINANT_VALUE)"
				+ "		values ('" + bbNextSeq + "', '" + bbNextSeq + "', null, '" + wmUserId + "', '" + subject
				+ "', 'H', 'U', sysdate, 'N', 'batchuser', sysdate, 'batchuser', sysdate, 1, 'OBDX_BU')";

		System.out.println(qryInsMBMUSER);
		stmt.executeUpdate(qryInsMBMUSER);

		System.out.println("added record in DIGX_CO_MAILBOX_MAILER_USER");

		body = bodyeng1 + maskPartyId(partyid) + bodyeng2 + bodychn1 + maskPartyId(partyid) + bodychn2;

		System.out.println("Body : " + body);

		qryInsMBMLR = "insert into DIGX_CO_MAILBOX_MAILER (MESSAGEID, DESCRIPTION, CODE, PRIORITY, TRIGGER_TYPE, ACTIVATION_DATE, BANNER_BODY, DETERMINANT_VALUE) "
				+ "		values ('" + bbNextSeq
				+ "', 'BOUNCEBACK_WEB_MAIL', 'BOUNCEBACK_WEB_MAIL', 'H', 'MANUAL', sysdate, TO_CLOB(q'[" + body
				+ "]'), 'OBDX_BU')";

		System.out.println(qryInsMBMLR);
		stmt.executeUpdate(qryInsMBMLR);
		System.out.println("added record in DIGX_CO_MAILBOX_MAILER");

		qryInsMBMSG = "insert into digx_co_mailbox_message (CREATION_DATE, EXPIRY_DATE, MESSAGE_TYPE, SUBJECT, MESSAGEID, MESSAGE_BODY, SENDER_NAME, CREATED_BY, LAST_UPDATED_BY, LAST_UPDATED_DATE, OBJECT_STATUS, OBJECT_VERSION_NUMBER, DETERMINANT_VALUE) "
				+ "		values (sysdate, to_date('01-07-2099', 'dd-mm-yyyy'), 'B', '" + subject + "', '" + bbNextSeq
				+ "', TO_CLOB(q'[" + body + "]'), null, 'batchuser', 'batchuser', sysdate, null, 1, 'OBDX_BU')";
		System.out.println(qryInsMBMSG);
		stmt.executeUpdate(qryInsMBMSG);
		System.out.println("added record in digx_co_mailbox_message");

	}

	public void sendWebMailMerchant(String wmUserId, String partyid, Connection conn, String LOG_FILE_NAME)
			throws SQLException {

		String qrybbNextSeq = "select 'BBM'||BounceBackWMSeq.NEXTVAL id from dual";
		String bbNextSeq = "";
		String qryInsMBMUSER = "";
		String qryInsMBMSG = "";
		String qryInsMBMLR = "";

		Statement stmt = conn.createStatement();

		System.out.println("Sending webmail to user " + wmUserId);
		writeLog(LOG_FILE_NAME, System.getProperty("line.separator") + "Sending webmail to user " + wmUserId, true);

		String querySubject = "select prop_value from DIGX_CZ_FW_CONFIG_ALL_O where prop_id='MAILBOX_MESSAGE_BNCBK_SUBJ3'";
		String queryBody = "select prop_value from DIGX_CZ_FW_CONFIG_ALL_O where prop_id='MAILBOX_MESSAGE_BNCBK_BODY3'";

		String subject = "Reminder for updating your registered notification contact in BEA Corporate Online";
		String body = "";

		ByteBuffer byteBuffer = StandardCharsets.UTF_8.encode("<p>Dear Valued Customer,</p><p>");

		String bodyeng1 = new String(byteBuffer.array(), StandardCharsets.UTF_8);

		byteBuffer = StandardCharsets.UTF_8.encode(
				" </p><p> </p><p>According to our record, the transaction/ service notification sent to the registered mobile phone number and email address cannot be delivered recently.</p><p> </p><p>To ensure you can receive our transaction/ service notifications, please update your registered mobile phone number and email address as soon as possible or execution of the relevant transaction/ service may be delayed without further notice. </p><p> </p><p>If the contact information have already been updated, please ignore this message. </p><p> </p><p>If you have any enquiries, please call our customer service hotline on (852) 2211 1056.</p><p> </p><p>Yours faithfully, <br/>The Bank of East Asia, Limited</p><p> </p>");

		String bodyeng2 = new String(byteBuffer.array(), StandardCharsets.UTF_8);
//
//		byteBuffer = StandardCharsets.UTF_8.encode("<p> </p><p>尊貴的客戶：</p><p> </p><p>東亞企業網上銀行賬戶號碼：");
//
//		String bodychn1 = new String(byteBuffer.array(), StandardCharsets.UTF_8);
//
//		byteBuffer = StandardCharsets.UTF_8.encode(
//				"  </p><p> </p><p>根據本行記錄，最近發出的交易／服務通知未能傳送至閣下已登記的流動電話號碼及電郵地址。</p><p> </p><p>為確保閣下能收到本行的交易／服務通知，請盡快向系統管理員更新閣下的登記流動電話號碼及電郵地址，否則有關交易／服務的執行可能有所延誤而未能另行通知。</p><p> </p><p>如閣下最近已更新聯絡資料，則毋需理會此訊息。</p><p> </p><p>如有垂詢，請致電本行：(852) 2211 1321。<br/>感謝使用本行服務。</p><p> </p><p>謹啓 <br/>東亞銀行有限公司</p><p> </p><p>中英文本文義如有歧異，概以英文本為準。此乃電腦系統發出之電郵，請勿回覆此電郵和電郵內的超連結。</p>");
//
//		String bodychn2 = new String(byteBuffer.array(), StandardCharsets.UTF_8);

		ResultSet rs = stmt.executeQuery(qrybbNextSeq);
		while (rs.next()) {
			bbNextSeq = rs.getString("id");
		}

		conn.setAutoCommit(true);
		qryInsMBMUSER = "insert into DIGX_CO_MAILBOX_MAILER_USER (ID, MESSAGE_ID, USER_NAME, USER_ID, SUBJECT, PRIORITY, MSG_STATUS, RECEIVED_DATE, DISMISSED, CREATED_BY, CREATION_DATE, LAST_UPDATED_BY, LAST_UPDATED_DATE, VERSION, DETERMINANT_VALUE)"
				+ "		values ('" + bbNextSeq + "', '" + bbNextSeq + "', null, '" + wmUserId + "', '" + subject
				+ "', 'H', 'U', sysdate, 'N', 'batchuser', sysdate, 'batchuser', sysdate, 1, 'OBDX_BU')";

		System.out.println(qryInsMBMUSER);
		stmt.executeUpdate(qryInsMBMUSER);

		System.out.println("added record in DIGX_CO_MAILBOX_MAILER_USER");

		body = bodyeng1 + bodyeng2;

		System.out.println("Body : " + body);

		qryInsMBMLR = "insert into DIGX_CO_MAILBOX_MAILER (MESSAGEID, DESCRIPTION, CODE, PRIORITY, TRIGGER_TYPE, ACTIVATION_DATE, BANNER_BODY, DETERMINANT_VALUE) "
				+ "		values ('" + bbNextSeq
				+ "', 'BOUNCEBACK_WEB_MAIL', 'BOUNCEBACK_WEB_MAIL', 'H', 'MANUAL', sysdate, TO_CLOB(q'[" + body
				+ "]'), 'OBDX_BU')";

		System.out.println(qryInsMBMLR);
		stmt.executeUpdate(qryInsMBMLR);
		System.out.println("added record in DIGX_CO_MAILBOX_MAILER");

		qryInsMBMSG = "insert into digx_co_mailbox_message (CREATION_DATE, EXPIRY_DATE, MESSAGE_TYPE, SUBJECT, MESSAGEID, MESSAGE_BODY, SENDER_NAME, CREATED_BY, LAST_UPDATED_BY, LAST_UPDATED_DATE, OBJECT_STATUS, OBJECT_VERSION_NUMBER, DETERMINANT_VALUE) "
				+ "		values (sysdate, to_date('01-07-2099', 'dd-mm-yyyy'), 'B', '" + subject + "', '" + bbNextSeq
				+ "', TO_CLOB(q'[" + body + "]'), null, 'batchuser', 'batchuser', sysdate, null, 1, 'OBDX_BU')";
		System.out.println(qryInsMBMSG);
		stmt.executeUpdate(qryInsMBMSG);
		System.out.println("added record in digx_co_mailbox_message");

	}

	public static String maskPartyId(String partyid) {
		String retVal = partyid;
		try {
			if (partyid.length() >= 14) {
				System.out.println(partyid);
				partyid = partyid.substring(3, partyid.length());
				System.out.println("   " + partyid);

				partyid = partyid.substring(0, 3) + "-" + partyid.substring(3, 5) + "-***" + partyid.substring(8, 10)
						+ "-" + partyid.substring(10, 11);
				System.out.println(partyid);
				retVal = partyid;
			}

		} catch (Exception e) {

			System.out.println("Exception Occured while maskig partyid");
			return retVal;
		}

		return retVal;
	}

	public static String maskOfficeEmail(String officeEmail) {
		String retVal = officeEmail;
		try {
			String email = "";
			String domain = "";
			int iend = officeEmail.indexOf("@");

			String subString;
			if (iend != -1) {
				email = officeEmail.substring(0, iend);
				domain = officeEmail.substring(iend + 1, officeEmail.length());
				if (email.length() == 2) {
					email = email.substring(0, 1) + "*";

				}
				if (email.length() == 3) {
					email = email.substring(0, 1) + "*" + email.substring(2, 3);

				}
				if (email.length() == 4) {
					email = email.substring(0, 1) + "**" + email.substring(3, 4);

				}
				if (email.length() > 4) {
					StringBuffer sb = new StringBuffer();
					sb.append(email.substring(0, 1));
					sb.append(email.substring(1, 2));

					for (int i = 0; i < email.length() - 4; i++) {
						sb.append("*");
					}

					sb.append(email.substring(email.length() - 2, email.length() - 1));
					sb.append(email.substring(email.length() - 1, email.length()));

					email = sb.toString();

				}

				int yend = domain.indexOf(".");

				if (yend != -1) {
					String beforeDot = domain.substring(0, yend); // this will give abc
					// System.out.println(email);
					String AfterDot = domain.substring(yend + 1, domain.length());

					beforeDot = beforeDot.replaceAll("(?s).", "*");
					AfterDot = AfterDot.replaceAll("(?s).", "*");
					domain = beforeDot + "." + AfterDot;
				}

			}

			retVal = email + "@" + domain;
		}

		catch (Exception e) {

			System.out.println("Exception Occured while maskig partyid");
			return retVal;
		}

		return retVal;
	}

	public void getSysAdminAndSendWebMail(String OfficeEmail, String partyid, Connection conn, String LOG_FILE_NAME)
			throws SQLException {
		String sysAdminQuery = "select distinct username\r\n" + "  from digx_um_user_principal\r\n"
				+ " where principal like '%SYSADM%'\r\n" + "   and username in (select user_id\r\n"
				+ "                      from Digx_UM_userparty_relation\r\n"
				+ "                     where party_id = '" + partyid + "')";
		Statement stmt = conn.createStatement();
		ResultSet rs = stmt.executeQuery(sysAdminQuery);
		while (rs.next()) {
			sendSysAdminWebMail(OfficeEmail, partyid, conn, rs.getString("username"), LOG_FILE_NAME);
		}
	}

	public void sendSysAdminWebMail(String officeEmail, String partyid, Connection conn, String wmUserId,
			String LOG_FILE_NAME) throws SQLException {

		System.out.println("Sending webmail to sysadmin " + wmUserId);
		writeLog(LOG_FILE_NAME, System.getProperty("line.separator") + "Sending webmail to sysadmin " + wmUserId, true);
		String qrybbNextSeq = "select 'BB'||BounceBackWMSeq.NEXTVAL id from dual";
		String bbNextSeq = "";
		String qryInsMBMUSER = "";
		String qryInsMBMSG = "";
		String qryInsMBMLR = "";

		Statement stmt = conn.createStatement();

		// String querySubject = "select prop_value from DIGX_CZ_FW_CONFIG_ALL_O where
		// prop_id='MAILBOX_MESSAGE_BNCBK_SUBJ3'";
		// String queryBody = "select prop_value from DIGX_CZ_FW_CONFIG_ALL_O where
		// prop_id='MAILBOX_MESSAGE_BNCBK_BODY3'";

		String subject = "BEA Corporate Online: Registered Email Address Update Reminder 東亞企業網上銀行：登記電郵地址更新提醒";
		String body = "";

		ByteBuffer byteBuffer = StandardCharsets.UTF_8
				.encode("<p>Dear Valued Customer,</p><p> </p><p>BEA Corporate Online Account No.:");

		String bodyeng1 = new String(byteBuffer.array(), StandardCharsets.UTF_8);
		bodyeng1 = bodyeng1 + maskPartyId(partyid) + "</br> Company Email Address: " + maskOfficeEmail(officeEmail);

		byteBuffer = StandardCharsets.UTF_8.encode(
				" </p><p> </p><p>According to our records, we could not delivery a recent transaction/service notification to the registered email address.</p><p> </p><p>To ensure you can receive our transaction/service notifications, please update the registered email address by visiting any BEA branch as soon as possible or the execution of the relevant transaction/service may be delayed without further notice.</p><p></p><p>If your email address has been updated recently, please ignore this message.</p><p> </p><p>For enquiries, please call us on (852) 2211 1321.<br/>Thank you.</p><p> </p><p>Yours faithfully, <br/>The Bank of East Asia, Limited</p><p> </p><p>If there are any discrepancies between the English and Chinese versions of this email, the English version shall apply and prevail. This is a system-generated email – please do not reply to this email or via any hyperlinks in the message.</p><p> </p><p>(The Chinese version of this message is provided below.)</p>");

		String bodyeng2 = new String(byteBuffer.array(), StandardCharsets.UTF_8);

		byteBuffer = StandardCharsets.UTF_8.encode("<p> </p><p>尊貴的客戶：</p><p> </p><p>東亞企業網上銀行賬戶號碼：");
		// + maskPartyId(partyid) + "</br>" + "公司電郵地址： " +
		// maskOfficeEmail(officeEmail));

		String bodychn1 = new String(byteBuffer.array(), StandardCharsets.UTF_8);
		byteBuffer = StandardCharsets.UTF_8.encode("</br> 公司電郵地址： ");
		String bodychn11 = new String(byteBuffer.array(), StandardCharsets.UTF_8);
		bodychn1 = bodychn1 + maskPartyId(partyid) + bodychn11 + maskOfficeEmail(officeEmail);
		byteBuffer = StandardCharsets.UTF_8.encode(
				"  </p><p> </p><p>根據本行記錄，最近發出的交易／服務通知未能傳送至閣下已登記的電郵地址。</p><p> </p><p>為確保閣下適時收到本行的交易／服務通知，請盡快親臨東亞銀行任何一家分行更新登記的電郵地址，否則交易／服務的執行可能有所延誤而未能另行通知。</p><p> </p><p>如閣下最近已更新電郵地址，則毋需理會此訊息。</p><p> </p><p>如有垂詢，請致電本行：(852) 2211 1321。<br/>感謝使用本行服務。</p><p> </p><p>謹啓 <br/>東亞銀行有限公司</p><p> </p><p>中英文本文義如有歧異，概以英文本為準。此乃電腦系統發出之電郵，請勿回覆此電郵和電郵內的超連結。</p>");

		String bodychn2 = new String(byteBuffer.array(), StandardCharsets.UTF_8);

		ResultSet rs = stmt.executeQuery(qrybbNextSeq);
		while (rs.next()) {
			bbNextSeq = rs.getString("id");
		}

		conn.setAutoCommit(true);
		qryInsMBMUSER = "insert into DIGX_CO_MAILBOX_MAILER_USER (ID, MESSAGE_ID, USER_NAME, USER_ID, SUBJECT, PRIORITY, MSG_STATUS, RECEIVED_DATE, DISMISSED, CREATED_BY, CREATION_DATE, LAST_UPDATED_BY, LAST_UPDATED_DATE, VERSION, DETERMINANT_VALUE)"
				+ "		values ('" + bbNextSeq + "', '" + bbNextSeq + "', null, '" + wmUserId + "', '" + subject
				+ "', 'H', 'U', sysdate, 'N', 'batchuser', sysdate, 'batchuser', sysdate, 1, 'OBDX_BU')";

		// System.out.println(qryInsMBMUSER);
		stmt.executeUpdate(qryInsMBMUSER);

		System.out.println("added record in DIGX_CO_MAILBOX_MAILER_USER");

		body = bodyeng1 + bodyeng2 + bodychn1 + bodychn2;

		// System.out.println("Body : " + body);

		qryInsMBMLR = "insert into DIGX_CO_MAILBOX_MAILER (MESSAGEID, DESCRIPTION, CODE, PRIORITY, TRIGGER_TYPE, ACTIVATION_DATE, BANNER_BODY, DETERMINANT_VALUE) "
				+ "		values ('" + bbNextSeq
				+ "', 'BOUNCEBACK_WEB_MAIL', 'BOUNCEBACK_WEB_MAIL', 'H', 'MANUAL', sysdate, TO_CLOB(q'[" + body
				+ "]'), 'OBDX_BU')";

		// System.out.println(qryInsMBMLR);
		stmt.executeUpdate(qryInsMBMLR);
		System.out.println("added record in DIGX_CO_MAILBOX_MAILER");

		qryInsMBMSG = "insert into digx_co_mailbox_message (CREATION_DATE, EXPIRY_DATE, MESSAGE_TYPE, SUBJECT, MESSAGEID, MESSAGE_BODY, SENDER_NAME, CREATED_BY, LAST_UPDATED_BY, LAST_UPDATED_DATE, OBJECT_STATUS, OBJECT_VERSION_NUMBER, DETERMINANT_VALUE) "
				+ "		values (sysdate, to_date('01-07-2099', 'dd-mm-yyyy'), 'B', '" + subject + "', '" + bbNextSeq
				+ "', TO_CLOB(q'[" + body + "]'), null, 'batchuser', 'batchuser', sysdate, null, 1, 'OBDX_BU')";
		// System.out.println(qryInsMBMSG);
		stmt.executeUpdate(qryInsMBMSG);
		System.out.println("added record in digx_co_mailbox_message");

	}

	public void PrepareEmailBounceMerchant(ValidateAndSendBounceModel record, Connection conn, String LOG_FILE_NAME,
			String[] args) throws Exception {

		boolean conditionmate = false;
		boolean webMail30Days = false;
		String tmpUserEmailAddress = "";

		String query = "SELECT P.EMAIL FROM digx_um_userprofile P WHERE P.u_name = '" + record.getUserid() + "'";
		// System.out.println(query);

		Statement stmtk = conn.createStatement();
		ResultSet rsk = stmtk.executeQuery(query);

		while (rsk.next()) {

			tmpUserEmailAddress = rsk.getString(1);

		}
		// Checking if email bounced is user's email or office email.

		// Part one : if bounce back is for alert sent after transaction.

		// if user has not changed his/her mobile after bounce back

		if (record.getBounced_email().equalsIgnoreCase(tmpUserEmailAddress)) {
			record.setBouncedMailType("USER");
		}

		// for MERCHANT ONLY USER
		if (record.getBouncedMailType().equalsIgnoreCase("USER")) {
			processUserEmailMerchant(stmtk, rsk, record, conn, LOG_FILE_NAME, tmpUserEmailAddress, args);
			// TO DO SMS CHECK
		}
	}

	public void PrepareEmailBounceKundli(ValidateAndSendBounceModel record, Connection conn, String LOG_FILE_NAME,
			String[] args) throws Exception {

		boolean conditionmate = false;
		String tmpOfficeEmailAddress = "";
		boolean companyBounce30Days = false;
		boolean officeBounce30Days = false;
		boolean companyOfficeSame = false;
		String tmpUserEmailAddress = "";

		// Storing officeEmailAddress for comparison

		String query = "SELECT P.OFFICE_EMAIL FROM DIGX_PI_PARTY_PREFERENCES P WHERE P.PARTYID = '"
				+ record.getPartyid() + "'";
		// System.out.println(query);

		Statement stmtk = conn.createStatement();
		ResultSet rsk = stmtk.executeQuery(query);

		while (rsk.next()) {

			tmpOfficeEmailAddress = rsk.getString(1);
		}

		query = "SELECT P.EMAIL FROM digx_um_userprofile P WHERE P.u_name = '" + record.getUserid() + "'";
		// System.out.println(query);

		stmtk = conn.createStatement();
		rsk = stmtk.executeQuery(query);

		while (rsk.next()) {

			tmpUserEmailAddress = rsk.getString(1);

		}
		// Checking if email bounced is user's email or office email.

		// Part one : if bounce back is for alert sent after transaction.

		if (record.getBounced_email().equalsIgnoreCase(tmpUserEmailAddress)) {
			record.setBouncedMailType("USER");
		} else if (record.getBounced_email().equalsIgnoreCase(tmpOfficeEmailAddress)) {
			record.setBouncedMailType("OFFICE");
		}

		if (record.getBouncedMailType().equalsIgnoreCase("USER")) {

			processUserEmail(stmtk, rsk, record, conn, LOG_FILE_NAME, tmpOfficeEmailAddress, tmpUserEmailAddress, args);
			// TO DO SMS CHECK
		} else {// OFFIC EMAIL PROCESSING STARTS HERE
				// HERE DO OFFICE ALTERNATIVE NOTIFICATION CHECK

			if (record.getBounced_email().equalsIgnoreCase(tmpOfficeEmailAddress)) {
				companyOfficeSame = true;
				// NOW CHECKING IF updated(current) office email was ever bounced in last 30
				// days.

				query = "SELECT 'Y' " + " from DIGX_CZ_BATCH_VALIDATE_AND_SEND_BOUNCE B\r\n"
						+ " WHERE B.ALT_OFFICE_EMAIL ='" + tmpOfficeEmailAddress + "'\r\n"
						+ " AND to_date(to_char(sysdate - 30, 'DD-MON-YYYY')) <\r\n"
						+ " to_DATE(SUBSTR(B.ALT_PROCESS_DATETIME, 1, 9), 'DD-MON-YY') " + " and B.USERID='"
						+ record.getUserid() + "'";
				System.out.println(query);
				rsk = stmtk.executeQuery(query);
				boolean isbouncedback = false;
				while (rsk.next()) {

					if (rsk.getString(1).equalsIgnoreCase("Y")) {
						isbouncedback = true;
						record.setOfficeMail30Days(true);
						record.setAlt_Offic_Email_Eligible(false);
						record.setAlt_office_email_to_populate("");
						record.setAlt_Ap_Email_Eligible(false);
						record.setStory(record.getStory()
								+ " OFFICE BOUNCED MAIL IS SAME AS OFFICE EMAIL AND BOUNCED BACKED IN 30 DAYS : F5");
						System.out.println(record);
						conditionmate = true;

					}
				}
				if (isbouncedback == false) {
					String apIds = "";
					record.setOfficeMail30Days(false);
					// record.setAlt_office_email("");
					record.setAlt_Ap_Email_Eligible(false);
					record.setAlt_acc_holder_ap("Y");
					record.setAlt_Sys_Web_Mail_Eligible(true);
					record.setAlt_sys_adm("Y");

					String[] margs = new String[] { "", args[0] };
					// LOGIN PARAMETER SET HERE
					apIds = setBounce_Back_ReminderAP(conn, record, margs);

					record.setStory(record.getStory()
							+ " OFFICE BOUNCED MAIL IS SAME AS OFFICE EMAIL AND ITS NOT BOUNCED BACKED IN 30 DAYS : F5 AP login Reminder set= "
							+ apIds);
					System.out.println(record);

					getSysAdminAndSendWebMail(record.getAlt_office_email(), record.getPartyid(), conn, LOG_FILE_NAME);
					conditionmate = true;
				}

			} else {
				// bounce mode is office email and company email is not same as office email
				// NOW CHECKING IF updated(current) office email and company email both were
				// ever bounced in last 30 days.

				query = "select \r\n" + "(case when B.ALT_OFFICE_EMAIL ='" + record.getBounced_email()
						+ "' then 'C' else 'CN' END),\r\n" + "(case when B.ALT_OFFICE_EMAIL ='" + tmpOfficeEmailAddress
						+ "' then 'O' ELSE 'ON' END)\r\n" + "\r\n"
						+ "from DIGX_CZ_BATCH_VALIDATE_AND_SEND_BOUNCE B \r\n"
						+ "where to_date(to_char(sysdate - 30, 'DD-MON-YYYY')) <\r\n"
						+ "       to_DATE(SUBSTR(B.ALT_PROCESS_DATETIME, 1, 9), 'DD-MON-YY')\r\n" + "   and rownum = 1";

				rsk = stmtk.executeQuery(query);
				// System.out.println(query);
				while (rsk.next()) {
					if (rsk.getString(1).equalsIgnoreCase("CN") && rsk.getString(2).equalsIgnoreCase("ON")) {
						// both company and office mail ids are not bounced back before
						record.setOfficeMail30Days(false);
						record.setAlt_Offic_Email_Eligible(true);
						record.setAlt_office_email_to_populate(tmpOfficeEmailAddress);
						// record.setAlt_office_email(tmpOfficeEmailAddress);
						conditionmate = true;
					} else if (rsk.getString(1).equalsIgnoreCase("CN")) {
						record.setAlt_Sys_Web_Mail_Eligible(true);
						record.setAlt_sys_adm("Y");
						getSysAdminAndSendWebMail(record.getAlt_office_email(), record.getPartyid(), conn,
								LOG_FILE_NAME);
						record.setAlt_Offic_Email_Eligible(false);
						record.setAlt_office_email_to_populate("");
						// record.setAlt_office_email("");
						// TODO CALL SYSADMIN WEBMAIL HERE
						conditionmate = true;
						record.setStory(record.getStory()
								+ " OFFICE BOUNCED MAIL IS DIFFERENT THAN OFFICE EMAIL AND ITS NOT BOUNCED BACKED IN 30 DAYS : F6");
						System.out.println(record);
					}
				}
			}

			if (conditionmate == false) {
				checkEmailFromBounceTable(stmtk, rsk, record, conn, LOG_FILE_NAME, args);
			}

			if (null != stmtk) {
				stmtk.close();
			}

			if (null != rsk) {
				rsk.close();
			}
			// Part two : checking if officeEmail bounced and this was sent as alternative
			// notification

			// LAST 30 DAYS TO OFFICE_EMAIL.
//					+ "   AND S.EMAIL_RECI_ADDR = B.ALT_OFFICE_EMAIL\r\n" : In below query this line determines if bounce back mail is office mail or not

		} // office email processing done here
	}

	public void PrepareEmailBounceKundliETS(ValidateAndSendBounceModel record, Connection conn, String LOG_FILE_NAME,
			String[] args) throws Exception {

		boolean conditionmate = false;
		String tmpOfficeEmailAddress = "";

		// Storing officeEmailAddress for comparison

		String query = "SELECT P.OFFICE_EMAIL FROM DIGX_PI_PARTY_PREFERENCES P WHERE P.PARTYID = '"
				+ record.getPartyid() + "'";
		System.out.println("=============PrepareEmailBounceKundliETS================");

		Statement stmtk = conn.createStatement();
		ResultSet rsk = stmtk.executeQuery(query);

		while (rsk.next()) {

			tmpOfficeEmailAddress = rsk.getString(1);
		}

		if (record.getBounced_email().equalsIgnoreCase(tmpOfficeEmailAddress)) {

			query = "SELECT distinct 'Y' " + " from DIGX_CZ_BATCH_VALIDATE_AND_SEND_BOUNCE B,\r\n"
					+ "DIGX_CZ_BATCH_BOUNCE_BACK_ETSEMAIL C\r\n" + " WHERE b.refnumber = c.src_sys_ref_num \r\n"
					+ " AND to_date(to_char(sysdate - 30, 'DD-MON-YYYY')) <\r\n"
					+ " to_DATE(SUBSTR(B.ALT_PROCESS_DATETIME, 1, 9), 'DD-MON-YY') " + " and b.isprocessed = 'Y'\r\n"
					+ " and B.party_id='" + record.getPartyid() + "'";

			System.out.println("ETS checking if office email update?" + query);
			rsk = stmtk.executeQuery(query);
			boolean isbouncedback = false;
			while (rsk.next()) {

				if (rsk.getString(1).equalsIgnoreCase("Y")) {
					isbouncedback = true;
					record.setOfficeMail30Days(true);
					record.setAlt_Offic_Email_Eligible(false);
					record.setAlt_office_email_to_populate("");
					record.setAlt_Ap_Email_Eligible(false);
					record.setStory(record.getStory()
							+ "ETS OFFICE BOUNCED MAIL IS SAME AS OFFICE EMAIL AND BOUNCED BACKED IN 30 DAYS : F5");
					System.out.println(record);
					conditionmate = true;

				}
			}
			if (isbouncedback == false) {
				String apIds = "";
				record.setOfficeMail30Days(false);
				record.setAlt_Ap_Email_Eligible(false);
				record.setAlt_acc_holder_ap("Y");
				record.setAlt_Sys_Web_Mail_Eligible(true);
				record.setAlt_sys_adm("Y");

				String[] margs = new String[] { "", args[0] };
				// LOGIN PARAMETER SET HERE
				apIds = setBounce_Back_ReminderAP(conn, record, margs);

				record.setStory(record.getStory()
						+ "ETS OFFICE BOUNCED MAIL IS SAME AS OFFICE EMAIL AND ITS NOT BOUNCED BACKED IN 30 DAYS : F5 AP login Reminder set= "
						+ apIds);
				System.out.println(record);

				getSysAdminAndSendWebMail(record.getAlt_office_email(), record.getPartyid(), conn, LOG_FILE_NAME);
				conditionmate = true;
			}

		} else {
			// bounce mode is office email and company email is not same as office email
			// NOW CHECKING IF updated(current) office email and company email both were
			// ever bounced in last 30 days.

			query = "select \r\n" + "(case when C.EMAIL_RECI_ADDR ='" + record.getBounced_email()
					+ "' then 'C' else 'CN' END),\r\n" + "(case when C.EMAIL_RECI_ADDR ='" + tmpOfficeEmailAddress
					+ "' then 'O' ELSE 'ON' END)\r\n" + "\r\n" + "from DIGX_CZ_BATCH_VALIDATE_AND_SEND_BOUNCE B,\r\n"
					+ "DIGX_CZ_BATCH_BOUNCE_BACK_ETSEMAIL C \r\n" + "where B.refnumber = C.src_sys_ref_num"
					+ " and to_date(to_char(sysdate - 30, 'DD-MON-YYYY')) <\r\n"
					+ "  to_DATE(SUBSTR(B.ALT_PROCESS_DATETIME, 1, 9), 'DD-MON-YY')\r\n" + "   and rownum = 1";

			rsk = stmtk.executeQuery(query);
			System.out.println(query);
			while (rsk.next()) {
				if (rsk.getString(1).equalsIgnoreCase("CN") && rsk.getString(2).equalsIgnoreCase("ON")) {
					// both company and office mail ids are not bounced back before
					record.setOfficeMail30Days(false);
					record.setAlt_Offic_Email_Eligible(true);
					record.setAlt_office_email_to_populate(tmpOfficeEmailAddress);
					conditionmate = true;
				} else if (rsk.getString(1).equalsIgnoreCase("CN")) {
					record.setAlt_Sys_Web_Mail_Eligible(true);
					record.setAlt_sys_adm("Y");
					// CALL SYSADMIN WEBMAIL HERE
					getSysAdminAndSendWebMail(record.getAlt_office_email(), record.getPartyid(), conn, LOG_FILE_NAME);
					record.setAlt_Offic_Email_Eligible(false);
					record.setAlt_office_email_to_populate("");
					conditionmate = true;
					record.setStory(record.getStory()
							+ "ETS OFFICE BOUNCED MAIL IS DIFFERENT THAN OFFICE EMAIL AND ITS NOT BOUNCED BACKED IN 30 DAYS : F6");
					System.out.println(record);
				}
			}
		}

		if (conditionmate == false) {
			checkEmailFromBounceTableETS(stmtk, rsk, record, conn, LOG_FILE_NAME, args);
		}

		if (null != stmtk) {
			stmtk.close();
		}

		if (null != rsk) {
			rsk.close();
		}
		// Part two : checking if officeEmail bounced and this was sent as alternative
		// notification

		// LAST 30 DAYS TO OFFICE_EMAIL.
//					+ "   AND S.EMAIL_RECI_ADDR = B.ALT_OFFICE_EMAIL\r\n" : In below query this line determines if bounce back mail is office mail or not

	} // office email processing done here
		// } // email processing done here

	// NOT SENDING LOGIN REMINDER TO SYSADMINS BUT NOT TO AP AS PER REQUIREMENT
	// GIVEN IN
	// FSD.
	private String setBounce_Back_ReminderAP(Connection conn, ValidateAndSendBounceModel record, String[] args)
			throws Exception {
		ArrayList<QueryParam> ActionRecordQueryList = new ArrayList<QueryParam>(50);

		String query = "select distinct username from digx_um_user_principal where principal like '%SYSADM%' and username in (select user_id from Digx_UM_userparty_relation where party_id = '"
				+ record.getPartyid() + "') ";

		System.out.println(query);

		Statement stmtrmd = conn.createStatement();
		ResultSet rsrmd = stmtrmd.executeQuery(query);

		System.out.println("Setting login reminder for below AP's");

		String innerQuery = "";
		ArrayList<String> arList;
		int recordId = -1;
		while (rsrmd.next()) {
			innerQuery = "Update digx_cz_um_extensiondata set Bounce_Back_Reminder='AP' where user_id=?";
			recordId++;
			QueryParam qp = new QueryParam();
			qp.setQuery(innerQuery);
			// System.out.println(query);
			arList = new ArrayList<String>();
			// System.out.println(rsrmd.getString(1) + ",");
			arList.add(0, rsrmd.getString(1));
			qp.setParams(arList);
			ActionRecordQueryList.add(recordId, qp);
		}
		if (ActionRecordQueryList.size() > 0) {
			invokeDBPrepared(ActionRecordQueryList, args, conn, false);
		}
		if (null != stmtrmd) {
			stmtrmd.close();
		}

		if (null != rsrmd) {
			rsrmd.close();
		}

		return "success";
	}

	private String setBounce_Back_ReminderUSER(Connection conn, ValidateAndSendBounceModel record, String[] args)
			throws Exception {
		ArrayList<QueryParam> ActionRecordQueryList = new ArrayList<QueryParam>(1);
		ArrayList arList;

		String query = "Update digx_cz_um_extensiondata set Bounce_Back_Reminder='USER' where user_id=?";

		QueryParam qp = new QueryParam();
		qp.setQuery(query);
		System.out.println("Bounce back reminder send to user " + record.getUserid());
		arList = new ArrayList<String>();
		// System.out.println(rsrmd.getString(1) + ",");
		arList.add(0, record.getUserid());
		qp.setParams(arList);
		ActionRecordQueryList.add(0, qp);

		if (ActionRecordQueryList.size() > 0) {
			invokeDBPrepared(ActionRecordQueryList, args, conn, false);
		}

		return "success";
	}

	private void processUserEmail(Statement stmtk, ResultSet rsk, ValidateAndSendBounceModel record, Connection conn,
			String LOG_FILE_NAME, String alt_office_email, String usersEmail, String[] args) throws Exception {
		// TODO Auto-generated method stub

		// CONDITION WHEN USER'S MOBILE NUMBER EXIST

		boolean userEmailbounced = false;
		boolean officeEmailbounced = false;
		boolean smsbounced = false;

		String query = "  select NVL((case "
				+ "         when (B.ALT_EMAIL is not null or alt_webmail is not null or alt_office_email is not null )then "
				+ "          'S' " + "         else " + "          'SN' " + "       END),''),'','' " + "     "
				+ "  from DIGX_CZ_BATCH_VALIDATE_AND_SEND_BOUNCE B "
				+ " where  (to_date(to_char(sysdate - 30, 'DD-MON-YYYY')) <= "
				+ "       to_DATE(SUBSTR(B.ALT_PROCESS_DATETIME, 1, 9), 'DD-MON-YY') OR  " + "        "
				+ "       to_date(to_char(sysdate - 30, 'DD-MON-YYYY')) <= "
				+ "       to_DATE(SUBSTR(B.RECORD_LAST_UPDATED, 1, 9), 'DD-MON-YY') " + "        " + "       ) "
				+ "   and NVL(B.USERID, '') = '" + record.getUserid() + "' " + "   and BOUNCED_MODE = 'SMS' " + "    "
				+ "UNION " + " " + "select '',NVL((case "
				+ "         when (B.ALT_SMS is not null or alt_webmail is not null or alt_office_email is not null ) then "
				+ "          'E' " + "         else " + "          'EN' " + "       END),''),'' " + "     "
				+ "  from DIGX_CZ_BATCH_VALIDATE_AND_SEND_BOUNCE B "
				+ " where (to_date(to_char(sysdate - 30, 'DD-MON-YYYY')) <= "
				+ "       to_DATE(SUBSTR(B.ALT_PROCESS_DATETIME, 1, 9), 'DD-MON-YY') OR  " + "        "
				+ "       to_date(to_char(sysdate - 30, 'DD-MON-YYYY')) <= "
				+ "       to_DATE(SUBSTR(B.RECORD_LAST_UPDATED, 1, 9), 'DD-MON-YY') " + "        " + "       ) "
				+ "   and NVL(B.USERID, '') = '" + record.getUserid() + "' " + "   and BOUNCED_MODE = 'EMAIL' " + " "
				+ "UNION " + " " + "SELECT '','',NVL((case " + "         when B.ALT_OFFICE_EMAIL is not null then "
				+ "          'O' " + "         else " + "          'ON' " + "       END),'')    "
				+ "  from DIGX_CZ_BATCH_VALIDATE_AND_SEND_BOUNCE B "
				+ " where  (to_date(to_char(sysdate - 30, 'DD-MON-YYYY')) <= "
				+ "       to_DATE(SUBSTR(B.ALT_PROCESS_DATETIME, 1, 9), 'DD-MON-YY') OR  " + "        "
				+ "       to_date(to_char(sysdate - 30, 'DD-MON-YYYY')) <= "
				+ "       to_DATE(SUBSTR(B.RECORD_LAST_UPDATED, 1, 9), 'DD-MON-YY') " + "        " + "       ) "
				+ "   and NVL(B.USERID, '') = '" + record.getUserid() + "'";

		// System.out.println(query);
		rsk = stmtk.executeQuery(query);

		while (rsk.next()) {
			if (null != rsk.getString(2) && rsk.getString(2).equalsIgnoreCase("E")) {
				userEmailbounced = true;
			}
			if (null != rsk.getString(1) && rsk.getString(1).equalsIgnoreCase("S")) {
				smsbounced = true;
			}

			if (null != rsk.getString(3) && rsk.getString(3).equalsIgnoreCase("O")) {
				officeEmailbounced = true;
			}
		}

		// condition F2 user email is bounced back
		// || smsbounced == true - THIS CHECK ENABLEs CONDITION TO CHECK if BOTH EMAIL
		// AND SMS are bounced back.
		// condition when user's mobile number does not exist or
		// SMS is bounced back.
//would be correct implementation

//		if (record.getAlt_sms() == "" || smsbounced == true) {// check the excel shared by Elton , there won't be any
		// confusion :-)
//			System.out.println(record.getUserid() + "'s email and sms both bounced in past 30 days");
//			if (userEmailbounced == false) {// check the excel shared by Elton , there won't be any confusion :-)
//				String[] margs = new String[] { "", args[0] };
//				// SET USER LOGIN PARAMETER HERE
//				setBounce_Back_ReminderUSER(conn, record, margs);
//				sendWebMail(record.getUserid(), record.getPartyid(), conn, LOG_FILE_NAME);
//				record.setAlt_Web_Mail_Eligible(true);
//				record.setAlt_webmail("Y");
//				if (!"".equalsIgnoreCase(alt_office_email) && !alt_office_email.equalsIgnoreCase(usersEmail)
//						&& officeEmailbounced == false) {
//					record.setAlt_Offic_Email_Eligible(true);
//					record.setAlt_office_email_to_populate(alt_office_email);

//				}
//			}
//		}

		if (record.getAlt_sms() == "" || smsbounced == true) {// check the excel shared by Elton , there won't be any //
																// confusion :-)
			System.out.println(record.getUserid() + "'s email and sms both bounced in past 30 days");
			if (smsbounced == false) {// check the excel shared by Elton , there won't be any confusion :-)
				String[] margs = new String[] { "", args[0] }; // SET USER LOGIN PARAMETER HERE
				setBounce_Back_ReminderUSER(conn, record, margs);
				sendWebMail(record.getUserid(), record.getPartyid(), conn, LOG_FILE_NAME);
				if (!"".equalsIgnoreCase(alt_office_email) && !alt_office_email.equalsIgnoreCase(usersEmail)
						&& officeEmailbounced == false) {
					record.setAlt_Offic_Email_Eligible(true);
					record.setAlt_office_email_to_populate(alt_office_email); //

				}
			}
		}

		if (record.getAlt_sms() != "") {
			// checking user email and sms bounce back

			if (userEmailbounced == false && smsbounced == false) {
				record.setAlt_Sms_Eligible(true);
				System.out.println(
						record.getUserid() + "sending alternative notification to mobile " + record.getAlt_sms());
				String[] margs = new String[] { "", args[0] };
				// SET USER LOGIN PARAMETER HERE
				setBounce_Back_ReminderUSER(conn, record, margs);
				// TODO LOGIN PARAMETER SET HERE
			} else {
				record.setAlt_Sms_Eligible(false);
				record.setAlt_sms("");
			}

		}

	}

	private void processUserEmailMerchant(Statement stmtk, ResultSet rsk, ValidateAndSendBounceModel record,
			Connection conn, String LOG_FILE_NAME, String usersEmail, String[] args) throws Exception {

		// CONDITION WHEN USER'S MOBILE NUMBER EXIST

		boolean userEmailbounced = false;
		boolean webMailBounced = false;
		boolean smsbounced = false;

		String query = "  select NVL((case "
				+ "         when (B.ALT_EMAIL is not null or alt_webmail is not null )then " + "          'S' "
				+ "         else " + "          'SN' " + "       END),''),'','' " + "     "
				+ "  from DIGX_CZ_BATCH_VALIDATE_AND_SEND_BOUNCE B "
				+ " where  (to_date(to_char(sysdate - 30, 'DD-MON-YYYY')) <= "
				+ "       to_DATE(SUBSTR(B.ALT_PROCESS_DATETIME, 1, 9), 'DD-MON-YY') OR  " + "        "
				+ "       to_date(to_char(sysdate - 30, 'DD-MON-YYYY')) <= "
				+ "       to_DATE(SUBSTR(B.RECORD_LAST_UPDATED, 1, 9), 'DD-MON-YY') " + "        " + "       ) "
				+ "   and NVL(B.USERID, '') = '" + record.getUserid() + "' " + "   and BOUNCED_MODE = 'SMS' " + "    "
				+ "UNION " + " " + "select '',NVL((case "
				+ "         when (B.ALT_SMS is not null or alt_webmail is not null ) then " + "          'E' "
				+ "         else " + "          'EN' " + "       END),''),'' " + "     "
				+ "  from DIGX_CZ_BATCH_VALIDATE_AND_SEND_BOUNCE B "
				+ " where (to_date(to_char(sysdate - 30, 'DD-MON-YYYY')) <= "
				+ "       to_DATE(SUBSTR(B.ALT_PROCESS_DATETIME, 1, 9), 'DD-MON-YY') OR  " + "        "
				+ "       to_date(to_char(sysdate - 30, 'DD-MON-YYYY')) <= "
				+ "       to_DATE(SUBSTR(B.RECORD_LAST_UPDATED, 1, 9), 'DD-MON-YY') " + "        " + "       ) "
				+ "   and NVL(B.USERID, '') = '" + record.getUserid() + "' " + "   and BOUNCED_MODE = 'EMAIL' " + " "
				+ "UNION " + " " + "SELECT '','',NVL((case " + "         when B.ALT_WEBMAIL is not null then "
				+ "          'W' " + "         else " + "          'WN' " + "       END),'')    "
				+ "  from DIGX_CZ_BATCH_VALIDATE_AND_SEND_BOUNCE B "
				+ " where  (to_date(to_char(sysdate - 30, 'DD-MON-YYYY')) <= "
				+ "       to_DATE(SUBSTR(B.ALT_PROCESS_DATETIME, 1, 9), 'DD-MON-YY') OR  " + "        "
				+ "       to_date(to_char(sysdate - 30, 'DD-MON-YYYY')) <= "
				+ "       to_DATE(SUBSTR(B.RECORD_LAST_UPDATED, 1, 9), 'DD-MON-YY') " + "        " + "       ) "
				+ "   and NVL(B.USERID, '') = '" + record.getUserid() + "'";

		// System.out.println(query);
		rsk = stmtk.executeQuery(query);

		while (rsk.next()) {
			if (null != rsk.getString(2) && rsk.getString(2).equalsIgnoreCase("E")) {
				userEmailbounced = true;
			}
			if (null != rsk.getString(1) && rsk.getString(1).equalsIgnoreCase("S")) {
				smsbounced = true;
			}

			if (null != rsk.getString(3) && rsk.getString(3).equalsIgnoreCase("W")) {
				webMailBounced = true;
			}
		}

		if (record.getAlt_sms() == "" || smsbounced == true) {
			System.out.println(record.getUserid() + "'s email and sms both bounced in past 30 days");

			// Condition sms is already bounced and this is email bounce event, sending
			// webmail.
			if (webMailBounced == false) {
				sendWebMailMerchant(record.getUserid(), record.getPartyid(), conn, LOG_FILE_NAME);
			} else {
				System.out.println(record.getUserid()
						+ "'s email and sms both bounced in past 30 days also webmail bounced hence skipping record from processing");
			}

		}

		else if (record.getAlt_sms() != "") {
			// checking user email and sms bounce back

			if (userEmailbounced == false && smsbounced == false) {
				record.setAlt_Sms_Eligible(true);
				System.out.println(
						record.getUserid() + "sending alternative notification to mobile " + record.getAlt_sms());
			} else {
				record.setAlt_Sms_Eligible(false);
				record.setAlt_sms("");
			}

		}

	}

	private void processUserSMS(Statement stmtk, ResultSet rsk, ValidateAndSendBounceModel record, Connection conn,
			String LOG_FILE_NAME, String alt_office_email, String usersEmail, String mobileCode, String[] args)
			throws Exception {
		// TODO Auto-generated method stub

		// CONDITION WHEN USER'S EMAIL EXIST

		boolean userEmailbounced = false;
		boolean officeEmailbounced = false;
		boolean smsbounced = false;

		String query = "  select NVL((case "
				+ "         when (B.ALT_EMAIL is not null or alt_webmail is not null or alt_office_email is not null )then "
				+ "          'S' " + "         else " + "          'SN' " + "       END),''),'','' " + "     "
				+ "  from DIGX_CZ_BATCH_VALIDATE_AND_SEND_BOUNCE B "
				+ " where  (to_date(to_char(sysdate - 30, 'DD-MON-YYYY')) <= "
				+ "       to_DATE(SUBSTR(B.ALT_PROCESS_DATETIME, 1, 9), 'DD-MON-YY') OR  " + "        "
				+ "       to_date(to_char(sysdate - 30, 'DD-MON-YYYY')) <= "
				+ "       to_DATE(SUBSTR(B.RECORD_LAST_UPDATED, 1, 9), 'DD-MON-YY') " + "        " + "       ) "
				+ "   and NVL(B.USERID, '') = '" + record.getUserid() + "' " + "   and BOUNCED_MODE = 'SMS' " + "    "
				+ "UNION " + " " + "select '',NVL((case "
				+ "         when (B.ALT_SMS is not null or alt_webmail is not null or alt_office_email is not null ) then "
				+ "          'E' " + "         else " + "          'EN' " + "       END),''),'' " + "     "
				+ "  from DIGX_CZ_BATCH_VALIDATE_AND_SEND_BOUNCE B "
				+ " where (to_date(to_char(sysdate - 30, 'DD-MON-YYYY')) <= "
				+ "       to_DATE(SUBSTR(B.ALT_PROCESS_DATETIME, 1, 9), 'DD-MON-YY') OR  " + "        "
				+ "       to_date(to_char(sysdate - 30, 'DD-MON-YYYY')) <= "
				+ "       to_DATE(SUBSTR(B.RECORD_LAST_UPDATED, 1, 9), 'DD-MON-YY') " + "        " + "       ) "
				+ "   and NVL(B.USERID, '') = '" + record.getUserid() + "' " + "   and BOUNCED_MODE = 'EMAIL' " + " "
				+ "UNION " + " " + "SELECT '','',NVL((case " + "         when B.ALT_OFFICE_EMAIL is not null then "
				+ "          'O' " + "         else " + "          'ON' " + "       END),'')    "
				+ "  from DIGX_CZ_BATCH_VALIDATE_AND_SEND_BOUNCE B "
				+ " where  (to_date(to_char(sysdate - 30, 'DD-MON-YYYY')) <= "
				+ "       to_DATE(SUBSTR(B.ALT_PROCESS_DATETIME, 1, 9), 'DD-MON-YY') OR  " + "        "
				+ "       to_date(to_char(sysdate - 30, 'DD-MON-YYYY')) <= "
				+ "       to_DATE(SUBSTR(B.RECORD_LAST_UPDATED, 1, 9), 'DD-MON-YY') " + "        " + "       ) "
				+ "   and NVL(B.USERID, '') = '" + record.getUserid() + "'";

		// System.out.println(query);
		rsk = stmtk.executeQuery(query);

		while (rsk.next()) {
			if (null != rsk.getString(2) && rsk.getString(2).equalsIgnoreCase("E")) {
				userEmailbounced = true;
			}
			if (null != rsk.getString(1) && rsk.getString(1).equalsIgnoreCase("S")) {
				smsbounced = true;
			}

			if (null != rsk.getString(3) && rsk.getString(3).equalsIgnoreCase("O")) {
				officeEmailbounced = true;
			}
		}

		// || smsbounced == true - THIS CHECK ENABLEs CONDITION TO CHECK if BOTH EMAIL
		// AND SMS are bounced back.
		// condition when user's email number does not exist or
		// email bounced back
		if (record.getAlt_email() == "" || userEmailbounced == true) {

			if (smsbounced == false) {
				System.out.println("F2 SENDING WEBMAIL AND LOGIN REMINDER");
				sendWebMail(record.getUserid(), record.getPartyid(), conn, LOG_FILE_NAME);
				record.setAlt_Web_Mail_Eligible(true);
				record.setAlt_webmail("Y");
				String[] margs = new String[] { "", args[0] };
				// SET USER LOGIN PARAMETER HERE
				setBounce_Back_ReminderUSER(conn, record, margs);

				if (!"".equalsIgnoreCase(alt_office_email) && !alt_office_email.equalsIgnoreCase(usersEmail)
						&& officeEmailbounced == false) {
					record.setAlt_Offic_Email_Eligible(true);
					record.setAlt_office_email_to_populate(alt_office_email);
					// record.setAlt_office_email(alt_office_email);

				}
			}
		}

		if (record.getAlt_email() != "") {
			// checking user email and sms bounce back

			if (userEmailbounced == false && smsbounced == false) {
				System.out.println("No email bounced no sms bounds");
				System.out.println("Sending alternative email and login reminder");
				record.setAlt_Email_Eligible(true);
				record.setAlt_email(usersEmail);
				String[] margs = new String[] { "", args[0] };
				// SET USER LOGIN PARAMETER HERE
				setBounce_Back_ReminderUSER(conn, record, margs);
				// TODO LOGIN PARAMETER SET HERE
			} else {
				record.setAlt_email("");
			}

		}

	}

	private void processUserSMSMerchant(Statement stmtk, ResultSet rsk, ValidateAndSendBounceModel record,
			Connection conn, String LOG_FILE_NAME, String alt_office_email, String usersEmail, String mobileCode,
			String[] args) throws Exception {
		// TODO Auto-generated method stub

		// CONDITION WHEN USER'S EMAIL EXIST

		boolean userEmailbounced = false;
		boolean webMailbounced = false;
		boolean smsbounced = false;

		String query = "  select NVL((case "
				+ "         when (B.ALT_EMAIL is not null or alt_webmail is not null )then " + "          'S' "
				+ "         else " + "          'SN' " + "       END),''),'','' " + "     "
				+ "  from DIGX_CZ_BATCH_VALIDATE_AND_SEND_BOUNCE B "
				+ " where  (to_date(to_char(sysdate - 30, 'DD-MON-YYYY')) <= "
				+ "       to_DATE(SUBSTR(B.ALT_PROCESS_DATETIME, 1, 9), 'DD-MON-YY') OR  " + "        "
				+ "       to_date(to_char(sysdate - 30, 'DD-MON-YYYY')) <= "
				+ "       to_DATE(SUBSTR(B.RECORD_LAST_UPDATED, 1, 9), 'DD-MON-YY') " + "        " + "       ) "
				+ "   and NVL(B.USERID, '') = '" + record.getUserid() + "' " + "   and BOUNCED_MODE = 'SMS' " + "    "
				+ "UNION " + " " + "select '',NVL((case "
				+ "         when (B.ALT_SMS is not null or alt_webmail is not null  ) then " + "          'E' "
				+ "         else " + "          'EN' " + "       END),''),'' " + "     "
				+ "  from DIGX_CZ_BATCH_VALIDATE_AND_SEND_BOUNCE B "
				+ " where (to_date(to_char(sysdate - 30, 'DD-MON-YYYY')) <= "
				+ "       to_DATE(SUBSTR(B.ALT_PROCESS_DATETIME, 1, 9), 'DD-MON-YY') OR  " + "        "
				+ "       to_date(to_char(sysdate - 30, 'DD-MON-YYYY')) <= "
				+ "       to_DATE(SUBSTR(B.RECORD_LAST_UPDATED, 1, 9), 'DD-MON-YY') " + "        " + "       ) "
				+ "   and NVL(B.USERID, '') = '" + record.getUserid() + "' " + "   and BOUNCED_MODE = 'EMAIL' " + " "
				+ "UNION " + " " + "SELECT '','',NVL((case " + "         when B.ALT_WEBMAIL is not null then "
				+ "          'W' " + "         else " + "          'WN' " + "       END),'')    "
				+ "  from DIGX_CZ_BATCH_VALIDATE_AND_SEND_BOUNCE B "
				+ " where  (to_date(to_char(sysdate - 30, 'DD-MON-YYYY')) <= "
				+ "       to_DATE(SUBSTR(B.ALT_PROCESS_DATETIME, 1, 9), 'DD-MON-YY') OR  " + "        "
				+ "       to_date(to_char(sysdate - 30, 'DD-MON-YYYY')) <= "
				+ "       to_DATE(SUBSTR(B.RECORD_LAST_UPDATED, 1, 9), 'DD-MON-YY') " + "        " + "       ) "
				+ "   and NVL(B.USERID, '') = '" + record.getUserid() + "'";

		// System.out.println(query);
		rsk = stmtk.executeQuery(query);

		while (rsk.next()) {
			if (null != rsk.getString(2) && rsk.getString(2).equalsIgnoreCase("E")) {
				userEmailbounced = true;
			}
			if (null != rsk.getString(1) && rsk.getString(1).equalsIgnoreCase("S")) {
				smsbounced = true;
			}

			if (null != rsk.getString(3) && rsk.getString(3).equalsIgnoreCase("W")) {
				webMailbounced = true;
			}
		}

		// || smsbounced == true - THIS CHECK ENABLEs CONDITION TO CHECK if BOTH EMAIL
		// AND SMS are bounced back.
		// condition when user's email number does not exist or
		// email bounced back
		if (record.getAlt_email() == "" || userEmailbounced == true) {

			if (smsbounced == false) {
				System.out.println("F2 SENDING WEBMAIL");
				sendWebMailMerchant(record.getUserid(), record.getPartyid(), conn, LOG_FILE_NAME);
				record.setAlt_Web_Mail_Eligible(true);
				record.setAlt_webmail("Y");
			}
		}

		else if (record.getAlt_email() != "") {
			// checking user email and sms bounce back

			if (userEmailbounced == false && smsbounced == false) {
				System.out.println("No email bounced no sms bounds");
				System.out.println("Sending alternative email and login reminder");
				record.setAlt_Email_Eligible(true);
				record.setAlt_email(usersEmail);
			} else {
				record.setAlt_email("");
			}

		}

	}

	public void checkEmailFromBounceTable(Statement stmtk, ResultSet rsk, ValidateAndSendBounceModel record,
			Connection conn, String LOG_FILE_NAME, String[] args) throws Exception {
		String query = "SELECT 'Y',\r\n" + "       (CASE\r\n" + "         WHEN (B.ALT_OFFICE_EMAIL =\r\n"
				+ "              (SELECT P.OFFICE_EMAIL\r\n" + "                  FROM DIGX_PI_PARTY_PREFERENCES P\r\n"
				+ "                 WHERE P.PARTYID = '" + record.getPartyid() + "')) THEN\r\n" + "          'SAME'\r\n"
				+ "         ELSE\r\n" + "          'DIFFERENT'\r\n" + "       END) COMPARE_TO_OFFICEMAIL,\r\n"
				+ "       B.REFNUMBER\r\n" + "  FROM DIGX_CZ_BATCH_BOUNCE_BACK_CCBEMAIL     S,\r\n"
				+ "       DIGX_CZ_EMAIL_MNG                      M,\r\n"
				+ "       DIGX_CZ_BATCH_VALIDATE_AND_SEND_BOUNCE B\r\n" + " WHERE S.SRC_SYS_REF_NUM = M.REFNUMBER\r\n"
				+ "   AND M.CUSTOMERID = B.USERID\r\n" + "   AND S.EMAIL_RECI_ADDR = B.ALT_OFFICE_EMAIL\r\n"
				+ "   AND M.CUSTOMERID = '" + record.getUserid() + "'\r\n" + "   AND B.ALT_OFFICE_EMAIL IS NOT NULL\r\n"
				+ "   AND B.ALT_OFFICE_EMAIL <> 'NA'\r\n" + "   AND to_date(to_char(sysdate - 30, 'DD-MON-YYYY')) <\r\n"
				+ "       to_DATE(SUBSTR(B.ALT_PROCESS_DATETIME, 1, 9), 'DD-MON-YY')\r\n" + "   and rownum = 1";
		// System.out.println(query);
		// stmtk = conn.createStatement();
		rsk = stmtk.executeQuery(query);

		while (rsk.next()) {

			// This is the condition when company email was bounce back and this is same as
			// current office email.
			if (rsk.getString(1).equalsIgnoreCase("Y") && rsk.getString(2).equalsIgnoreCase("SAME")) {
				record.setBouncedMailType("OFFICE");
				record.setOfficeMail30Days(true);
				record.setAlt_Offic_Email_Eligible(false);
				record.setAlt_office_email_to_populate("");
				record.setAlt_Ap_Email_Eligible(false);
				// record.setAlt_office_email("");
				record.setStory(record.getStory()
						+ " OFFICE BOUNCED MAIL(C) IS SAME AS OFFICE EMAIL AND BOUNCED BACKED IN 30 DAYS : F6");
				System.out.println(record);

				// SINCE COMPANY AND OFFICE BOTH ARE SAME AND HAVE BEEN BOUNCED BACK IN 30
				// DAYS... NOTHING TO DO HERE...
			}

			// This is the condition when company email is bounce back and this is not same
			// as current office email.
			if (rsk.getString(1).equalsIgnoreCase("Y") && rsk.getString(2).equalsIgnoreCase("DIFFERENT")) {
				record.setBouncedMailType("OFFICE");

				// NOW CHECKING IF COMPANY EMAIL BOUNCED BACK IN PAST 30 DAYS

				query = "select \r\n" + "(case when B.ALT_OFFICE_EMAIL ='" + record.getBounced_email()
						+ "' then 'C' else 'CN' END) from DIGX_CZ_BATCH_VALIDATE_AND_SEND_BOUNCE B \r\n"
						+ "where to_date(to_char(sysdate - 30, 'DD-MON-YYYY')) <\r\n"
						+ "       to_DATE(SUBSTR(B.ALT_PROCESS_DATETIME, 1, 9), 'DD-MON-YY')\r\n" + "   and rownum = 1";

				rsk = stmtk.executeQuery(query);

				while (rsk.next()) {
					// COMPANY EMAIL ID IS NOT BOUNCED BACK IN PAST 30 DAYS
					if (rsk.getString(1).equalsIgnoreCase("CN")) {

						String[] margs = new String[] { "", args[0] };
						// TODO LOGIN PARAMETER SET HERE
						setBounce_Back_ReminderAP(conn, record, margs);

						// TODO LOGIN PROMPT PARAMETER SET HERE
						record.setOfficeMail30Days(false);
						record.setAlt_Offic_Email_Eligible(false);
						record.setAlt_office_email_to_populate("");
						// record.setAlt_office_email("");
						record.setAlt_Sys_Web_Mail_Eligible(true);
						record.setAlt_sys_adm("Y");
						getSysAdminAndSendWebMail(record.getAlt_office_email(), record.getPartyid(), conn,
								LOG_FILE_NAME);
						// TODO send sys webmail
					} else {
						record.setOfficeMail30Days(false);
						record.setAlt_Offic_Email_Eligible(false);
						record.setAlt_office_email_to_populate("");
						// record.setAlt_office_email("");
						record.setAlt_Sys_Web_Mail_Eligible(false);
						record.setAlt_sys_adm("");
						record.setStory(record.getStory()
								+ " OFFICE BOUNCED MAIL(C) IS DIFFERENT THAN OFFICE EMAIL AND C BOUNCED BACKED IN 30 DAYS : F6");
						System.out.println(record);
					}
				}
			}

		}
	}

	public void checkEmailFromBounceTableETS(Statement stmtk, ResultSet rsk, ValidateAndSendBounceModel record,
			Connection conn, String LOG_FILE_NAME, String[] args) throws Exception {
		String query = "SELECT 'Y',\r\n" + "       (CASE\r\n" + "         WHEN (B.ALT_OFFICE_EMAIL =\r\n"
				+ "              (SELECT P.OFFICE_EMAIL\r\n" + "                  FROM DIGX_PI_PARTY_PREFERENCES P\r\n"
				+ "                 WHERE P.PARTYID = '" + record.getPartyid() + "')) THEN\r\n" + "          'SAME'\r\n"
				+ "         ELSE\r\n" + "          'DIFFERENT'\r\n" + "       END) COMPARE_TO_OFFICEMAIL,\r\n"
				+ "       B.REFNUMBER\r\n" + "  FROM DIGX_CZ_BATCH_BOUNCE_BACK_ETSEMAIL     S,\r\n"
				+ "       DIGX_CZ_EMAIL_MNG                      M,\r\n"
				+ "       DIGX_CZ_BATCH_VALIDATE_AND_SEND_BOUNCE B\r\n" + " WHERE S.SRC_SYS_REF_NUM = M.REFNUMBER\r\n"
				+ "   AND S.EMAIL_RECI_ADDR = B.ALT_OFFICE_EMAIL\r\n" + "   AND B.ALT_OFFICE_EMAIL IS NOT NULL\r\n"
				+ "   AND B.ALT_OFFICE_EMAIL <> 'NA'\r\n" + "   AND to_date(to_char(sysdate - 30, 'DD-MON-YYYY')) <\r\n"
				+ "       to_DATE(SUBSTR(B.ALT_PROCESS_DATETIME, 1, 9), 'DD-MON-YY')\r\n" + "   and rownum = 1";
		System.out.println("=====================checkEmailFromBounceTableETS==================");
		// stmtk = conn.createStatement();
		rsk = stmtk.executeQuery(query);

		while (rsk.next()) {

			// This is the condition when company email was bounce back and this is same as
			// current office email.
			if (rsk.getString(1).equalsIgnoreCase("Y") && rsk.getString(2).equalsIgnoreCase("SAME")) {
				record.setBouncedMailType("OFFICE");
				record.setOfficeMail30Days(true);
				record.setAlt_Offic_Email_Eligible(false);
				record.setAlt_office_email_to_populate("");
				record.setAlt_Ap_Email_Eligible(false);
				// record.setAlt_office_email("");
				record.setStory(record.getStory()
						+ "ETS OFFICE BOUNCED MAIL(C) IS SAME AS OFFICE EMAIL AND BOUNCED BACKED IN 30 DAYS : F6");
				System.out.println(record);

				// SINCE COMPANY AND OFFICE BOTH ARE SAME AND HAVE BEEN BOUNCED BACK IN 30
				// DAYS... NOTHING TO DO HERE...
			}

			// This is the condition when company email is bounce back and this is not same
			// as current office email.
			if (rsk.getString(1).equalsIgnoreCase("Y") && rsk.getString(2).equalsIgnoreCase("DIFFERENT")) {
				record.setBouncedMailType("OFFICE");

				// NOW CHECKING IF COMPANY EMAIL BOUNCED BACK IN PAST 30 DAYS

				query = "select \r\n" + "(case when B.ALT_OFFICE_EMAIL ='" + record.getBounced_email()
						+ "' then 'C' else 'CN' END) from DIGX_CZ_BATCH_VALIDATE_AND_SEND_BOUNCE B \r\n"
						+ "where to_date(to_char(sysdate - 30, 'DD-MON-YYYY')) <\r\n"
						+ "       to_DATE(SUBSTR(B.ALT_PROCESS_DATETIME, 1, 9), 'DD-MON-YY')\r\n" + "   and rownum = 1";

				rsk = stmtk.executeQuery(query);

				while (rsk.next()) {
					// COMPANY EMAIL ID IS NOT BOUNCED BACK IN PAST 30 DAYS
					if (rsk.getString(1).equalsIgnoreCase("CN")) {

						String[] margs = new String[] { "", args[0] };
						// LOGIN PARAMETER SET HERE
						setBounce_Back_ReminderAP(conn, record, margs);

						// LOGIN PROMPT PARAMETER SET HERE
						record.setOfficeMail30Days(false);
						record.setAlt_Offic_Email_Eligible(false);
						record.setAlt_office_email_to_populate("");
						record.setAlt_Sys_Web_Mail_Eligible(true);
						record.setAlt_sys_adm("Y");
						getSysAdminAndSendWebMail(record.getAlt_office_email(), record.getPartyid(), conn,
								LOG_FILE_NAME);
						// TODO send sys webmail
					} else {
						record.setOfficeMail30Days(false);
						record.setAlt_Offic_Email_Eligible(false);
						record.setAlt_office_email_to_populate("");
						// record.setAlt_office_email("");
						record.setAlt_Sys_Web_Mail_Eligible(false);
						record.setAlt_sys_adm("");
						record.setStory(record.getStory()
								+ "ETS OFFICE BOUNCED MAIL(C) IS DIFFERENT THAN OFFICE EMAIL AND C BOUNCED BACKED IN 30 DAYS : F6");
						System.out.println(record);
					}
				}

			}

		}
	}

}
