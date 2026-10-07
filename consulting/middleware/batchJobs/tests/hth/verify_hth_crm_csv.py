#!/usr/bin/env python3
"""SIT-only check of the 1293 CSV layout; no database access or credentials."""

import csv
import pathlib
import re
import sys


EXPECTED = (
    "record_type", "Filler_01", "Event_Id", "Event_Dte", "Event_Time",
    "Chnl_Id", "Chnl_Type_Code", "Event_Status_Code", "Cr_Dr_Ind",
    "Fee_Chrg_Code", "Event_Actv_Type_Code", "Fin_Ind", "User_Id",
    "Event_Country_Code", "Acct_Nbr", "Elect_Add", "FPS_ID", "FPS_Acct_Nbr",
    "FPS_Req_Result", "Debit_Acct_Nbr", "Event_Ccy_Code", "Event_Amt",
    "Fee_Chrg_Amt", "Fee_Ccy_Code", "Trf_Dte", "Trf_Freq",
    "Proxy_ID_Type", "Proxy_ID", "Payee_Name", "Payee_Bank_Code",
    "Event_Rem", "Ref_Nbr", "From_Dte", "To_Dte", "Mandate_Id",
    "eDDA_Maint_Action", "Source_Trx_Ref_Nbr", "Pay_Cat_Purp_Code",
    "Pay_Purp_Code", "IP_Address", "Event_Amt_Hke", "Event_Ex_Rate",
    "Mrch_Id", "Acct_Ccy_Code", "Acct_Amt", "Suspicious_Activity",
    "Country_Name", "Region", "Suspicious_Ind", "Party_Int_Nbr",
    "Event_Credit_Acct_Type", "Event_Credit_Acct_Nbr", "FPS_Bus_Service_Cd",
    "Event_Remitter_Name", "Adv_Freq_Type", "Adv_Type", "API_URL",
    "Message_ID", "No_Financial_Transactions", "Transaction_Status", "Report_Type",
)


def verify(path):
    if not pathlib.Path(path).is_file():
        raise ValueError("file does not exist")
    line_count = 0
    with open(path, "rb") as source:
        for line_count, line in enumerate(source, start=1):
            if not line.endswith(b"\n") or b"\r" in line or b"\x00" in line:
                raise ValueError("line %d is not LF-only text" % line_count)
            line.decode("utf-8")
            if not line.startswith(b'"') or not line.rstrip(b"\n").endswith(b'"'):
                raise ValueError("line %d is not fully quoted" % line_count)
    if line_count == 0:
        raise ValueError("file is empty")
    record_count = 0
    with open(path, encoding="utf-8", newline="") as source:
        reader = csv.reader(source, delimiter=",", quotechar='"',
                            escapechar="\\", doublequote=False, strict=True)
        for record_count, record in enumerate(reader, start=1):
            if record_count == 1 and tuple(record) != EXPECTED:
                raise ValueError("header differs from the 61-column HTH API mapping")
            if len(record) != len(EXPECTED):
                raise ValueError("record %d has %d fields, expected 61"
                                 % (record_count, len(record)))
            if record_count > 1 and (record[0] != "50" or record[1] != "1"):
                raise ValueError("record %d has invalid record_type or filler" % record_count)
            if record_count > 1 and record[4] and not re.fullmatch(r"\d{2}:\d{2}:\d{2}", record[4]):
                raise ValueError("record %d Event_Time is not HH:MM:SS" % record_count)
    if record_count != line_count:
        raise ValueError("record contains an unescaped physical line break")
    print("OK: UTF-8, LF, 61 fields; data rows=%d" % (record_count - 1))


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("Usage: verify_hth_crm_csv.py FILE.csv")
    try:
        verify(sys.argv[1])
    except (OSError, UnicodeError, ValueError, csv.Error) as error:
        raise SystemExit("INVALID: %s" % error)
