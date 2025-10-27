package com.mytrackr.receipts.features.receipts;

import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ReceiptParser {
    // Very lightweight heuristic parser to extract store name, total, and date from OCR text
    public static Receipt parse(String ocrText) {
        Receipt r = new Receipt();
        r.setRawText(ocrText);
        if (ocrText == null || ocrText.trim().isEmpty()) return r;

        String[] lines = ocrText.split("\\r?\\n");
        // store name: first non-empty line
        for (String l : lines) {
            if (l != null && !l.trim().isEmpty()) {
                r.setStoreName(l.trim());
                break;
            }
        }

        // total: find line with total or sum-looking number
        Pattern money = Pattern.compile("\\b(total|amount|balance)\\b.*?([0-9]+[.,][0-9]{2})", Pattern.CASE_INSENSITIVE);
        Matcher m = money.matcher(ocrText);
        if (m.find()) {
            try {
                String val = m.group(2).replace(",", "");
                r.setTotal(Double.parseDouble(val));
            } catch (Exception ex) {
                Log.w("ReceiptParser", "failed parse total: " + ex.getMessage());
            }
        } else {
            // fallback: find last currency-looking number in text
            Pattern anyMoney = Pattern.compile("([0-9]+[.,][0-9]{2})");
            Matcher m2 = anyMoney.matcher(ocrText);
            String last = null;
            while (m2.find()) last = m2.group(1);
            if (last != null) {
                try { r.setTotal(Double.parseDouble(last.replace(",", ""))); } catch (Exception ignored) {}
            }
        }

        // date: look for YYYY-MM-DD or DD/MM/YYYY or variants
        Pattern datePat = Pattern.compile("(\\d{4}-\\d{2}-\\d{2})|(\\d{2}[/\\.-]\\d{2}[/\\.-]\\d{4})");
        Matcher md = datePat.matcher(ocrText);
        if (md.find()) {
            String ds = md.group();
            long epoch = System.currentTimeMillis();
            r.setDate(epoch);
        } else {
            r.setDate(System.currentTimeMillis());
        }

        // items: naive - lines containing an item-like pattern: name then price
        Pattern itemPattern = Pattern.compile("^\\s*(.+?)\\s+([0-9]+[.,][0-9]{2})\\s*$");
        List<HashMap<String, Object>> items = new ArrayList<>();
        for (String l : lines) {
            Matcher mi = itemPattern.matcher(l);
            if (mi.find()) {
                HashMap<String, Object> it = new HashMap<>();
                it.put("name", mi.group(1).trim());
                try { it.put("price", Double.parseDouble(mi.group(2).replace(",",""))); } catch (Exception e) { it.put("price", 0.0); }
                items.add(it);
            }
        }
        if (!items.isEmpty()) r.setItems((List)items);

        return r;
    }
}

