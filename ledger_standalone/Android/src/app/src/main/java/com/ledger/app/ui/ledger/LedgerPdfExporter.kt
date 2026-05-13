package com.ledger.app.ui.ledger

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object LedgerPdfExporter {

  private const val PAGE_WIDTH = 595f
  private const val PAGE_HEIGHT = 842f
  private const val MARGIN = 48f
  private const val LINE_HEIGHT = 16f

  fun export(context: Context, state: LedgerUiState): Uri {
    val document = PdfDocument()
    val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH.toInt(), PAGE_HEIGHT.toInt(), 1).create()
    val page = document.startPage(pageInfo)
    drawPage(page.canvas, state)
    document.finishPage(page)

    val dir = File(context.cacheDir, "reports").also { it.mkdirs() }
    val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    val file = File(dir, "ledger_report_$ts.pdf")
    file.outputStream().use { document.writeTo(it) }
    document.close()

    return FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
  }

  private fun drawPage(canvas: Canvas, state: LedgerUiState) {
    val titlePaint = paint(20f, Color.parseColor("#1565C0"), bold = true)
    val headingPaint = paint(13f, Color.parseColor("#212121"), bold = true)
    val bodyPaint = paint(11f, Color.parseColor("#424242"))
    val smallPaint = paint(9f, Color.parseColor("#616161"))
    val smallBoldPaint = paint(9f, Color.parseColor("#424242"), bold = true)
    val linePaint = Paint().apply { color = Color.parseColor("#BDBDBD"); strokeWidth = 0.8f }
    val greenPaint = paint(11f, Color.parseColor("#2E7D32"), bold = true)
    val redPaint = paint(11f, Color.parseColor("#C62828"), bold = true)
    val warnPaint = paint(9f, Color.parseColor("#E65100"), bold = true)
    val subPaint = paint(9f, Color.parseColor("#9E9E9E"))

    var y = 56f

    // Title + date
    canvas.drawText("Ledger — Daily Report", MARGIN, y, titlePaint)
    y += 18f
    val date = SimpleDateFormat("EEEE, d MMMM yyyy  HH:mm", Locale.US).format(Date())
    canvas.drawText(date, MARGIN, y, subPaint)
    y += 22f

    divider(canvas, y, linePaint); y += 18f

    // Summary
    canvas.drawText("Financial Summary", MARGIN, y, headingPaint); y += LINE_HEIGHT + 4f

    metricRow(canvas, y, "Revenue", fmt(state.revenue), bodyPaint, bodyPaint)
    y += LINE_HEIGHT
    metricRow(canvas, y, "Total Cost", fmt(state.totalCost), bodyPaint, bodyPaint)
    y += LINE_HEIGHT
    metricRow(canvas, y, "Net Profit", fmt(state.netProfit), bodyPaint, if (state.netProfit >= 0) greenPaint else redPaint)
    y += LINE_HEIGHT
    metricRow(canvas, y, "Transactions", state.transactionCount.toString(), bodyPaint, bodyPaint)
    y += LINE_HEIGHT + 14f

    divider(canvas, y, linePaint); y += 18f

    // Transactions table
    canvas.drawText("Recent Transactions", MARGIN, y, headingPaint); y += LINE_HEIGHT + 4f

    if (state.recentTransactions.isEmpty()) {
      canvas.drawText("No transactions recorded.", MARGIN, y, subPaint); y += LINE_HEIGHT
    } else {
      // Header
      tableHeader(canvas, y, smallBoldPaint, "Item", "Type", "Amount", "Qty", "CCY")
      y += 4f; divider(canvas, y, linePaint); y += 10f

      for (tx in state.recentTransactions.take(25)) {
        if (y > PAGE_HEIGHT - 180f) { canvas.drawText("…truncated", MARGIN, y, subPaint); y += LINE_HEIGHT; break }
        tableRow(canvas, y, smallPaint, tx.item.take(22), tx.transactionType, fmt(tx.amount), "${fmtQty(tx.quantity)} ${tx.unit}", tx.currency)
        y += LINE_HEIGHT
      }
    }
    y += 10f

    divider(canvas, y, linePaint); y += 18f

    // Inventory table
    canvas.drawText("Inventory", MARGIN, y, headingPaint)
    if (state.lowStockItems.isNotEmpty()) {
      canvas.drawText("  ${state.lowStockItems.size} item(s) low stock", MARGIN + 75f, y, warnPaint)
    }
    y += LINE_HEIGHT + 4f

    if (state.stockItemNames.isEmpty()) {
      canvas.drawText("No stock recorded.", MARGIN, y, subPaint); y += LINE_HEIGHT
    } else {
      // Header
      canvas.drawText("Item", MARGIN, y, smallBoldPaint)
      canvas.drawText("Qty", MARGIN + 200f, y, smallBoldPaint)
      canvas.drawText("Unit", MARGIN + 270f, y, smallBoldPaint)
      canvas.drawText("Status", MARGIN + 340f, y, smallBoldPaint)
      y += 4f; divider(canvas, y, linePaint); y += 10f

      for ((name, stock) in state.stockItemNames.zip(state.stockItems)) {
        if (y > PAGE_HEIGHT - 40f) { canvas.drawText("…truncated", MARGIN, y, subPaint); break }
        val isLow = name in state.lowStockItems
        canvas.drawText(name.take(28), MARGIN, y, smallPaint)
        canvas.drawText(fmtQty(stock.quantity), MARGIN + 200f, y, smallPaint)
        canvas.drawText(stock.unit, MARGIN + 270f, y, smallPaint)
        canvas.drawText(if (isLow) "LOW STOCK" else "OK", MARGIN + 340f, y, if (isLow) warnPaint else smallPaint)
        y += LINE_HEIGHT
      }
    }

    // Footer
    val footerPaint = paint(8f, Color.parseColor("#9E9E9E"))
    canvas.drawText("Generated by Ledger app", MARGIN, PAGE_HEIGHT - 24f, footerPaint)
  }

  private fun metricRow(canvas: Canvas, y: Float, label: String, value: String, labelPaint: Paint, valuePaint: Paint) {
    canvas.drawText(label, MARGIN, y, labelPaint)
    canvas.drawText(value, MARGIN + 180f, y, valuePaint)
  }

  private fun tableHeader(canvas: Canvas, y: Float, p: Paint, item: String, type: String, amount: String, qty: String, ccy: String) {
    canvas.drawText(item, MARGIN, y, p)
    canvas.drawText(type, MARGIN + 175f, y, p)
    canvas.drawText(amount, MARGIN + 280f, y, p)
    canvas.drawText(qty, MARGIN + 360f, y, p)
    canvas.drawText(ccy, MARGIN + 440f, y, p)
  }

  private fun tableRow(canvas: Canvas, y: Float, p: Paint, item: String, type: String, amount: String, qty: String, ccy: String) {
    canvas.drawText(item, MARGIN, y, p)
    canvas.drawText(type, MARGIN + 175f, y, p)
    canvas.drawText(amount, MARGIN + 280f, y, p)
    canvas.drawText(qty, MARGIN + 360f, y, p)
    canvas.drawText(ccy, MARGIN + 440f, y, p)
  }

  private fun divider(canvas: Canvas, y: Float, paint: Paint) {
    canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, paint)
  }

  private fun paint(size: Float, color: Int, bold: Boolean = false) = Paint().apply {
    this.color = color
    textSize = size
    typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    isAntiAlias = true
  }

  private fun fmt(v: Double): String = String.format(Locale.US, "%.2f", v)
  private fun fmtQty(v: Double): String =
    if (v == v.toLong().toDouble()) v.toLong().toString()
    else String.format(Locale.US, "%.1f", v)
}
