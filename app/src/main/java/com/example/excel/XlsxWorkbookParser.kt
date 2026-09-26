package com.example.excel

import android.util.Log
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/**
 * Ελαφρύς και αξιόπιστος αναλυτής αρχείων .xlsx (Excel)
 * Χρησιμοποιεί standard ZipInputStream και XmlPullParser του Android.
 * Δεν εξαρτάται από βαριές βιβλιοθήκες (όπως Apache POI) και λειτουργεί άμεσα και γρήγορα.
 */
class XlsxWorkbookParser {

    data class Sheet(
        val id: String,
        val name: String,
        val target: String,
        val rows: Map<Int, Map<Int, String>> = emptyMap() // rowIndex (0-based) -> (colIndex 0-based -> text)
    )

    data class Workbook(
        val sheets: List<Sheet>
    )

    fun parse(inputStream: InputStream): Workbook {
        val zipFiles = mutableMapOf<String, ByteArray>()
        val zis = ZipInputStream(inputStream)
        var entry: ZipEntry? = zis.nextEntry
        while (entry != null) {
            val name = entry.name
            if (name.startsWith("xl/") || name.startsWith("xl\\") || name == "[Content_Types].xml") {
                zipFiles[name.replace('\\', '/')] = zis.readBytes()
            }
            zis.closeEntry()
            entry = zis.nextEntry
        }
        zis.close()

        // 1. Parse Shared Strings (xl/sharedStrings.xml)
        val sharedStrings = parseSharedStrings(zipFiles["xl/sharedStrings.xml"])

        // 2. Parse Relationships (xl/_rels/workbook.xml.rels)
        val rels = parseRelationships(zipFiles["xl/_rels/workbook.xml.rels"])

        // 3. Parse Workbook Sheets list (xl/workbook.xml)
        val rawSheets = parseWorkbookSheets(zipFiles["xl/workbook.xml"], rels)

        // 4. Parse Worksheets (xl/worksheets/sheet*.xml)
        val parsedSheets = rawSheets.map { sheet ->
            val targetPath = if (sheet.target.startsWith("/")) {
                sheet.target.substring(1)
            } else if (sheet.target.startsWith("worksheets/")) {
                "xl/${sheet.target}"
            } else {
                "xl/worksheets/${sheet.target.substringAfterLast('/')}"
            }
            val sheetBytes = zipFiles[targetPath] ?: zipFiles.entries.firstOrNull {
                it.key.endsWith(sheet.target.substringAfterLast('/'))
            }?.value

            val rows = if (sheetBytes != null) {
                parseSheetData(sheetBytes, sharedStrings)
            } else {
                emptyMap()
            }
            sheet.copy(rows = rows)
        }

        return Workbook(sheets = parsedSheets)
    }

    private fun parseSharedStrings(bytes: ByteArray?): List<String> {
        if (bytes == null) return emptyList()
        val list = mutableListOf<String>()
        val parser = Xml.newPullParser()
        parser.setInput(ByteArrayInputStream(bytes), "UTF-8")

        var eventType = parser.eventType
        var currentText: StringBuilder? = null
        var inTextTag = false

        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "si" -> currentText = StringBuilder()
                        "t" -> inTextTag = true
                    }
                }
                XmlPullParser.TEXT -> {
                    if (inTextTag && currentText != null) {
                        currentText.append(parser.text)
                    }
                }
                XmlPullParser.END_TAG -> {
                    when (parser.name) {
                        "t" -> inTextTag = false
                        "si" -> {
                            list.add(currentText?.toString() ?: "")
                            currentText = null
                        }
                    }
                }
            }
            eventType = parser.next()
        }
        return list
    }

    private fun parseRelationships(bytes: ByteArray?): Map<String, String> {
        if (bytes == null) return emptyMap()
        val rels = mutableMapOf<String, String>()
        val parser = Xml.newPullParser()
        parser.setInput(ByteArrayInputStream(bytes), "UTF-8")

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG && parser.name == "Relationship") {
                val id = parser.getAttributeValue(null, "Id")
                val target = parser.getAttributeValue(null, "Target")
                if (id != null && target != null) {
                    rels[id] = target
                }
            }
            eventType = parser.next()
        }
        return rels
    }

    private fun parseWorkbookSheets(bytes: ByteArray?, rels: Map<String, String>): List<Sheet> {
        if (bytes == null) return emptyList()
        val sheets = mutableListOf<Sheet>()
        val parser = Xml.newPullParser()
        parser.setInput(ByteArrayInputStream(bytes), "UTF-8")

        var eventType = parser.eventType
        var fallbackSheetNum = 1
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG && parser.name == "sheet") {
                val name = parser.getAttributeValue(null, "name") ?: "Sheet$fallbackSheetNum"
                val sheetId = parser.getAttributeValue(null, "sheetId") ?: "$fallbackSheetNum"
                val rId = parser.getAttributeValue("http://schemas.openxmlformats.org/officeDocument/2006/relationships", "id")
                    ?: parser.getAttributeValue(null, "r:id")
                    ?: "rId$fallbackSheetNum"
                val target = rels[rId] ?: "worksheets/sheet$fallbackSheetNum.xml"
                sheets.add(Sheet(id = sheetId, name = name, target = target))
                fallbackSheetNum++
            }
            eventType = parser.next()
        }
        return sheets
    }

    private fun parseSheetData(bytes: ByteArray, sharedStrings: List<String>): Map<Int, Map<Int, String>> {
        val rows = mutableMapOf<Int, MutableMap<Int, String>>()
        val parser = Xml.newPullParser()
        parser.setInput(ByteArrayInputStream(bytes), "UTF-8")

        var eventType = parser.eventType
        var currentRowIdx = -1
        var currentColIdx = -1
        var cellType: String? = null
        var inValueTag = false
        var inInlineTextTag = false
        var cellValueText = StringBuilder()

        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "row" -> {
                            val rAttr = parser.getAttributeValue(null, "r")
                            currentRowIdx = (rAttr?.toIntOrNull()?.minus(1)) ?: (currentRowIdx + 1)
                            if (!rows.containsKey(currentRowIdx)) {
                                rows[currentRowIdx] = mutableMapOf()
                            }
                        }
                        "c" -> {
                            val rRef = parser.getAttributeValue(null, "r")
                            cellType = parser.getAttributeValue(null, "t")
                            currentColIdx = if (rRef != null) {
                                colRefToIndex(rRef)
                            } else {
                                currentColIdx + 1
                            }
                            cellValueText.clear()
                        }
                        "v" -> inValueTag = true
                        "t" -> inInlineTextTag = true
                    }
                }
                XmlPullParser.TEXT -> {
                    if (inValueTag || inInlineTextTag) {
                        cellValueText.append(parser.text)
                    }
                }
                XmlPullParser.END_TAG -> {
                    when (parser.name) {
                        "v" -> inValueTag = false
                        "t" -> inInlineTextTag = false
                        "c" -> {
                            val rawVal = cellValueText.toString().trim()
                            val resolvedText = when (cellType) {
                                "s" -> { // Shared string index
                                    val idx = rawVal.toIntOrNull()
                                    if (idx != null && idx >= 0 && idx < sharedStrings.size) {
                                        sharedStrings[idx]
                                    } else {
                                        rawVal
                                    }
                                }
                                "inlineStr" -> rawVal
                                "b" -> if (rawVal == "1") "TRUE" else "FALSE"
                                else -> rawVal
                            }
                            if (currentRowIdx >= 0 && currentColIdx >= 0 && resolvedText.isNotEmpty()) {
                                val rowMap = rows.getOrPut(currentRowIdx) { mutableMapOf() }
                                rowMap[currentColIdx] = resolvedText
                            }
                        }
                        "row" -> {
                            currentColIdx = -1
                        }
                    }
                }
            }
            eventType = parser.next()
        }
        return rows
    }

    companion object {
        /**
         * Μετατρέπει αναφορά κελιού όπως "A1", "C4", "AA12" σε 0-based column index.
         * "A" -> 0, "B" -> 1, "Z" -> 25, "AA" -> 26
         */
        fun colRefToIndex(cellRef: String): Int {
            var col = 0
            for (ch in cellRef.uppercase()) {
                if (ch in 'A'..'Z') {
                    col = col * 26 + (ch - 'A' + 1)
                } else {
                    break
                }
            }
            return if (col > 0) col - 1 else 0
        }
    }
}
