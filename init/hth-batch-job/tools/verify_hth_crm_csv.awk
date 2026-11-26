# Usage: LC_ALL=C awk -v day=yyyyMMdd -f verify_hth_crm_csv.awk FILE.csv
NR == 1 {
    header_ok = (length($0) == 100 && substr($0, 1, 16) == ("00" day "HTHMTB"))
    next
}

NR == 2 {
    labels_ok = (index($0, "\"record_type\",\"Filler_01\",") == 1)
    next
}

index($0, "\"50\",\"1\",") == 1 { rows++ }

END {
    count = substr($0, 3, 15)
    checksum = substr($0, 18, 15)
    if (!header_ok || !labels_ok || NR != rows + 3 || length($0) != 100 ||
        substr($0, 1, 2) != "99" || count !~ /^[0-9]+$/ || count + 0 != rows ||
        checksum !~ /^[0-9]+$/ || checksum + 0 != day + rows) {
        print "INVALID: MTB control records or row count"
        exit 1
    }
    printf "MTB data rows=%d\n", rows
}
