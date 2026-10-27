#!/usr/bin/env python3
"""SIT-only check of the 1293 CSV layout; no database access or credentials."""

import csv
import datetime
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


CELL = r'"(?:[^"\\]|\\[\\"rn])*"'
CSV_LINE = re.compile(CELL + r'(?:,' + CELL + r')*')


def text_line(raw, line_number):
    if not raw.endswith(b"\n") or b"\r" in raw or b"\x00" in raw:
        raise ValueError("line %d is missing or not LF-only text" % line_number)
    return raw[:-1].decode("utf-8")


def csv_record(line, line_number):
    if not CSV_LINE.fullmatch(line):
        raise ValueError("line %d is not fully quoted MTB CSV" % line_number)
    record = next(csv.reader([line], delimiter=",", quotechar='"',
                            escapechar="\\", doublequote=False, strict=True))
    if len(record) != len(EXPECTED):
        raise ValueError("line %d has %d fields, expected 61" % (line_number, len(record)))
    return record


def verify(path, expected_date=None):
    path = pathlib.Path(path)
    if not path.is_file():
        raise ValueError("file does not exist")
    with path.open("rb") as source:
        header = text_line(source.readline(), 1)
        if not re.fullmatch(r"00[0-9]{8}HTHMTB {84}", header):
            raise ValueError("header must be 100 characters: 00, yyyyMMdd, HTH, MTB, 84 spaces")
        batch_date = header[2:10]
        datetime.date(int(batch_date[:4]), int(batch_date[4:6]), int(batch_date[6:8]))
        if expected_date is not None and batch_date != expected_date:
            raise ValueError("header date differs from expected extraction date")
        # Check the documented default filename; custom patterns may pass an explicit date.
        match = re.fullmatch(r"hthtomtb_mvp1apis_D([0-9]{6})\.csv", path.name)
        if match and match.group(1) != batch_date[2:]:
            raise ValueError("filename date differs from header date")
        labels = csv_record(text_line(source.readline(), 2), 2)
        if tuple(labels) != EXPECTED:
            raise ValueError("column names differ from the 61-column HTH API mapping")
        # Keep one line pending: only the final line may be a trailer.
        line_number = 3
        pending = source.readline()
        count = 0
        for following in source:
            record = csv_record(text_line(pending, line_number), line_number)
            if record[0] != "50" or record[1] != "1":
                raise ValueError("line %d has invalid record_type or filler" % line_number)
            if record[4] and not re.fullmatch(r"[0-9]{2}:[0-9]{2}:[0-9]{2}", record[4]):
                raise ValueError("line %d Event_Time is not HH:MM:SS" % line_number)
            count += 1
            line_number += 1
            pending = following
        trailer = text_line(pending, line_number)
        if not re.fullmatch(r"99[0-9]{30} {68}", trailer):
            raise ValueError("trailer must be 100 characters: 99, count(15), checksum(15), 68 spaces")
        if int(trailer[2:17]) != count:
            raise ValueError("trailer count differs from data rows (column names and controls excluded)")
        if int(trailer[17:32]) != int(batch_date) + count:
            raise ValueError("trailer checksum differs from batch_date + data rows")
    print("OK: UTF-8, LF, 61 fields, 100-character controls; date=%s data rows=%d total lines=%d"
          % (batch_date, count, count + 3))
    return count


if __name__ == "__main__":
    if len(sys.argv) not in (2, 3):
        raise SystemExit("Usage: verify_hth_crm_csv.py FILE.csv [yyyyMMdd]")
    try:
        verify(sys.argv[1], sys.argv[2] if len(sys.argv) == 3 else None)
    except (OSError, UnicodeError, ValueError, csv.Error) as error:
        raise SystemExit("INVALID: %s" % error)
