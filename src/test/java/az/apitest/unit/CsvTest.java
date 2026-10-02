package az.apitest.unit;

import az.apitest.engine.Csv;
import az.apitest.engine.Resources;
import org.testng.annotations.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.expectThrows;

public class CsvTest {

    @Test
    public void parsesQuotedCellsCommasAndEscapedQuotes() {
        List<Map<String, Object>> rows = Csv.parse(Resources.read("unit/users.csv"));
        assertEquals(rows.size(), 3);
        assertEquals(rows.get(0).get("case"), "düzgün email");
        assertEquals(rows.get(1).get("email"), "");
        assertEquals(rows.get(2).get("case"), "vergül, dırnaq \"x\"");
    }

    @Test
    public void infersTypes() {
        Map<String, Object> row = Csv.parse("i,l,d,b,n,s\n42,9999999999,1.50,true,null,abc\n").get(0);
        assertEquals(row.get("i"), 42);
        assertEquals(row.get("l"), 9_999_999_999L);
        assertEquals(row.get("d"), new BigDecimal("1.50"));
        assertEquals(row.get("b"), true);
        assertNull(row.get("n"));
        assertEquals(row.get("s"), "abc");
    }

    @Test
    public void handlesCrLfBomAndBlankLines() {
        List<Map<String, Object>> rows = Csv.parse("﻿a,b\r\n1,x\r\n\r\n2,y\r\n");
        assertEquals(rows.size(), 2);
        assertEquals(rows.get(1).get("b"), "y");
    }

    @Test
    public void wrongCellCountFailsWithLineNumber() {
        IllegalArgumentException e = expectThrows(IllegalArgumentException.class, () -> Csv.parse("a,b\n1,2,3\n"));
        assertEquals(e.getMessage(), "CSV sətir 2: 3 xana var, başlıqda isə 2");
    }
}
