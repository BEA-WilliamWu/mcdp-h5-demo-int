# Usage: LC_ALL=C awk -v day=yyyyMMdd -f verify_hth_crm_csv.awk FILE.csv
{ last_record = $0 }

NR == 1 {
    header_length = length($0)
    header_ok = (header_length == 100 && substr($0, 1, 16) == ("00" day "HTHMTB"))
    next
}

NR == 2 {
    labels_ok = (index($0, "\"record_type\",\"Filler_01\",") == 1)
    next
}

index($0, "\"50\",\"1\",") == 1 { rows++ }

END {
    trailer_length = length(last_record)
    trailer_ok = (trailer_length == 100 && substr(last_record, 1, 2) == "99")
    count = substr(last_record, 3, 15)
    checksum = substr(last_record, 18, 15)
    count_ok = (count ~ /^[0-9]+$/ && count + 0 == rows)
    checksum_ok = (checksum ~ /^[0-9]+$/ && checksum + 0 == day + rows)
    if (!header_ok || !labels_ok || NR != rows + 3 || !trailer_ok || !count_ok || !checksum_ok) {
        print "INVALID: MTB control records or row count"
        printf "header_ok=%d header_length=%d labels_ok=%d\n", header_ok, header_length, labels_ok
        printf "total_lines=%d data_rows=%d expected_lines=%d\n", NR, rows, rows + 3
        printf "trailer_ok=%d trailer_length=%d count_ok=%d checksum_ok=%d\n", trailer_ok, trailer_length, count_ok, checksum_ok
        exit 1
    }
    printf "MTB data rows=%d\n", rows
}
