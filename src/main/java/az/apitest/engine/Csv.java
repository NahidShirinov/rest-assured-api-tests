package az.apitest.engine;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * dataFile üçün sadə CSV oxuyucu (RFC 4180: dırnaqlı xanalar, "" escape, xana içində vergül/sətir).
 * Birinci sətir başlıqlardır. Dəyərlərin tipi təxmin olunur: 42 -> ədəd, true/false -> boolean, null -> null.
 * Boş mətn üçün "" yaz (dırnaqsız boş xana da "" olur).
 */
public final class Csv {

    private Csv() {
    }

    public static List<Map<String, Object>> parse(String text) {
        List<List<String>> rows = rows(text.startsWith("﻿") ? text.substring(1) : text);
        if (rows.isEmpty()) {
            return List.of();
        }
        List<String> header = rows.get(0);
        List<Map<String, Object>> result = new ArrayList<>();
        for (int r = 1; r < rows.size(); r++) {
            List<String> row = rows.get(r);
            if (row.size() == 1 && row.get(0).isEmpty()) {
                continue; // boş sətir
            }
            if (row.size() != header.size()) {
                throw new IllegalArgumentException("CSV sətir " + (r + 1) + ": " + row.size()
                        + " xana var, başlıqda isə " + header.size());
            }
            Map<String, Object> map = new LinkedHashMap<>();
            for (int c = 0; c < header.size(); c++) {
                map.put(header.get(c).trim(), typed(row.get(c)));
            }
            result.add(map);
        }
        return result;
    }

    private static Object typed(String value) {
        if (value.equals("null")) return null;
        if (value.equals("true")) return true;
        if (value.equals("false")) return false;
        if (value.matches("-?\\d+")) {
            BigInteger n = new BigInteger(value);
            return n.bitLength() < 32 ? (Object) n.intValue() : n.bitLength() < 64 ? (Object) n.longValue() : n;
        }
        if (value.matches("-?\\d+\\.\\d+")) return new BigDecimal(value);
        return value;
    }

    private static List<List<String>> rows(String text) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (quoted) {
                if (ch == '"' && i + 1 < text.length() && text.charAt(i + 1) == '"') {
                    cell.append('"');
                    i++;
                } else if (ch == '"') {
                    quoted = false;
                } else {
                    cell.append(ch);
                }
            } else if (ch == '"') {
                quoted = true;
            } else if (ch == ',') {
                row.add(cell.toString());
                cell.setLength(0);
            } else if (ch == '\n' || ch == '\r') {
                if (ch == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') {
                    i++;
                }
                row.add(cell.toString());
                cell.setLength(0);
                rows.add(row);
                row = new ArrayList<>();
            } else {
                cell.append(ch);
            }
        }
        if (cell.length() > 0 || !row.isEmpty()) {
            row.add(cell.toString());
            rows.add(row);
        }
        return rows;
    }
}
