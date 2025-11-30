package com.mytrackr.receipts.features.receipts;

import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.mytrackr.receipts.data.models.Receipt;
import com.mytrackr.receipts.data.models.ReceiptItem;

public class ReceiptParser {
    public static Receipt parse(String ocrText) {
        Receipt r = new Receipt();
        if (ocrText == null || ocrText.trim().isEmpty()) {
            Receipt.ReceiptMetadata metadata = new Receipt.ReceiptMetadata();
            metadata.setOcrText(ocrText != null ? ocrText : "");
            metadata.setProcessedBy("parser");
            r.setMetadata(metadata);
            return r;
        }

        Receipt.ReceiptMetadata metadata = new Receipt.ReceiptMetadata();
        metadata.setOcrText(ocrText);
        metadata.setProcessedBy("parser");
        r.setMetadata(metadata);

        Receipt.StoreInfo store = new Receipt.StoreInfo();
        String[] lines = ocrText.split("\\r?\\n");
        for (String l : lines) {
            if (l != null && !l.trim().isEmpty()) {
                store.setName(l.trim());
                break;
            }
        }
        r.setStore(store);

        Receipt.ReceiptInfo receiptInfo = new Receipt.ReceiptInfo();

        Pattern money = Pattern.compile("\\b(total|amount|balance)\\b.*?([0-9]+[.,][0-9]{2})", Pattern.CASE_INSENSITIVE);
        Matcher m = money.matcher(ocrText);
        if (m.find()) {
            try {
                String val = m.group(2).replace(",", "");
                receiptInfo.setTotal(Double.parseDouble(val));
            } catch (Exception ex) {
                Log.w("ReceiptParser", "failed parse total: " + ex.getMessage());
            }
        } else {
            Pattern anyMoney = Pattern.compile("([0-9]+[.,][0-9]{2})");
            Matcher m2 = anyMoney.matcher(ocrText);
            String last = null;
            while (m2.find()) last = m2.group(1);
            if (last != null) {
                try { receiptInfo.setTotal(Double.parseDouble(last.replace(",", ""))); } catch (Exception ignored) {}
            }
        }

        long timestamp = System.currentTimeMillis();
        receiptInfo.setDateTimestamp(timestamp);
        r.setReceipt(receiptInfo);

        Pattern itemPattern = Pattern.compile("^\\s*(.+?)\\s+([0-9]+[.,][0-9]{2})\\s*$");
        List<ReceiptItem> items = new ArrayList<>();
        for (String l : lines) {
            Matcher mi = itemPattern.matcher(l);
            if (mi.find()) {
                ReceiptItem item = new ReceiptItem();
                item.setName(mi.group(1).trim());
                try {
                    double price = Double.parseDouble(mi.group(2).replace(",", ""));
                    item.setTotalPrice(price);
                    item.setQuantity(1);
                } catch (Exception e) {
                    item.setTotalPrice(0.0);
                    item.setQuantity(1);
                }
                items.add(item);
            }
        }
        if (!items.isEmpty()) r.setItems(items);

        return r;
    }
}