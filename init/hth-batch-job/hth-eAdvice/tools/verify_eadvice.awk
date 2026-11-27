# Usage: LC_ALL=C awk -v day=yyyyMMdd -f verify_eadvice.awk FILE.TXT
{ last_record = $0 }
NR == 1 { header_ok = (substr($0, 1, 10) == ("01" day)) }
substr($0, 1, 2) == "02" { rows++ }

END {
    count = substr(last_record, 3, 8)
    checksum = substr(last_record, 11, 10)
    if (!header_ok || NR != rows + 2 || substr(last_record, 1, 2) != "99" ||
        count !~ /^[0-9]+$/ || length(count) != 8 || count + 0 != rows ||
        checksum !~ /^[0-9]+$/ || length(checksum) != 10 || checksum + 0 != day + rows) {
        print "INVALID: eAdvice control records or row count"
        exit 1
    }
    printf "eAdvice data rows=%d\n", rows
}
