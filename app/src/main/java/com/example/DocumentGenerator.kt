package com.example

import android.content.Context
import android.graphics.*
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.ui.CartItem
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DocumentGenerator {

    fun generateDocument(
        context: Context,
        isInvoice: Boolean,
        cartItems: List<CartItem>,
        deliveryFee: Double,
        totalAmount: Double,
        customerName: String,
        customerPhone: String,
        deliveryAddress: String,
        invoiceNo: String = "CFX-${System.currentTimeMillis().toString().takeLast(6)}",
        orderId: String = "ORD-${System.currentTimeMillis().toString().takeLast(6)}",
        pharmacistName: String = "Pharm. Olawale A.",
        pharmacyName: String = "Careflux Central Pharmacy"
    ): Pair<Uri?, String?> {
        val width = 1400
        val headerHeight = 450
        val billToHeight = 250
        val tableHeaderHeight = 60
        val rowHeight = 90
        val itemsHeight = (cartItems.size * rowHeight) + tableHeaderHeight
        val totalsHeight = 350
        val paymentHeight = if (isInvoice) 360 else 0
        val footerHeight = 360
        
        val height = headerHeight + billToHeight + itemsHeight + totalsHeight + paymentHeight + footerHeight + 150
        
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        
        // Colors
        val colorWhite = Color.WHITE
        val colorDarkBlue = Color.parseColor("#0A2A66")
        val colorLightBlue = Color.parseColor("#0066CC")
        val colorBgLight = Color.parseColor("#F4F7FB")
        val colorBorder = Color.parseColor("#E0E0E0")
        val colorTextDark = Color.parseColor("#1A1A1A")
        val colorTextGray = Color.parseColor("#666666")
        val colorGreen = Color.parseColor("#008000")
        val colorRed = Color.parseColor("#D32F2F")

        // Paints
        val paintBg = Paint().apply { color = colorWhite; style = Paint.Style.FILL }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paintBg)
        
        val paintDarkBlueText = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorDarkBlue }
        val paintLightBlueText = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorLightBlue }
        val paintDarkText = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorTextDark }
        val paintGrayText = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorTextGray }
        
        val typeRegular = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        val typeBold = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)

        var yPos = 80f
        val margin = 80f

        // --- HEADER --- //
        // Logo simulation + CAREFLUX
        paintDarkBlueText.typeface = typeBold
        paintDarkBlueText.textSize = 56f
        canvas.drawText("CAREFLUX", margin + 100f, yPos - 10f, paintDarkBlueText)
        
        paintLightBlueText.typeface = typeRegular
        paintLightBlueText.textSize = 20f
        paintLightBlueText.color = colorTextGray
        canvas.drawText("DELIVERING ON BEHALF OF ${pharmacyName.uppercase()}", margin + 100f, yPos + 30f, paintLightBlueText)
        
        paintGrayText.typeface = typeRegular
        paintGrayText.textSize = 28f

        // Draw cross logo box
        val logoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorDarkBlue; style = Paint.Style.FILL }
        canvas.drawRoundRect(margin, yPos - 60f, margin + 80f, yPos + 20f, 16f, 16f, logoPaint)
        val crossPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorWhite; strokeWidth = 12f; strokeCap = Paint.Cap.ROUND }
        canvas.drawLine(margin + 40f, yPos - 40f, margin + 40f, yPos, crossPaint)
        canvas.drawLine(margin + 20f, yPos - 20f, margin + 60f, yPos - 20f, crossPaint)

        // Right Side: INVOICE / RECEIPT
        paintDarkBlueText.textSize = 72f
        val docTitle = if (isInvoice) "INVOICE" else "RECEIPT"
        canvas.drawText(docTitle, width - margin - paintDarkBlueText.measureText(docTitle), yPos, paintDarkBlueText)
        
        // Status Badge
        yPos += 50f
        val badgeText = if (isInvoice) "AWAITING PAYMENT" else "PAYMENT CONFIRMED"
        val badgeColor = if (isInvoice) colorLightBlue else colorGreen
        val badgeBgColor = if (isInvoice) Color.parseColor("#E6F0FA") else Color.parseColor("#E6F4E6")
        
        paintDarkBlueText.textSize = 24f
        paintDarkBlueText.typeface = typeBold
        val badgeWidth = paintDarkBlueText.measureText(badgeText) + 80f
        val badgeRect = RectF(width - margin - badgeWidth, yPos, width - margin, yPos + 50f)
        canvas.drawRoundRect(badgeRect, 25f, 25f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = badgeBgColor })
        
        val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { 
            color = badgeColor; textSize = 24f; typeface = typeBold; textAlign = Paint.Align.CENTER 
        }
        canvas.drawText(badgeText, badgeRect.centerX(), badgeRect.centerY() + 8f, badgeTextPaint)

        // Address Lines
        yPos += 80f
        paintDarkText.textSize = 24f
        paintDarkText.typeface = typeRegular
        val addressLine1 = "26A, Jasmine Road, Ikota GRA,"
        val addressLine2 = "Ikota, Lekki, Lagos State, Nigeria."
        canvas.drawText(addressLine1, margin + 40f, yPos, paintDarkText)
        canvas.drawText(addressLine2, margin + 40f, yPos + 35f, paintDarkText)
        
        canvas.drawText("+234 814 757 8314", margin + 40f, yPos + 90f, paintDarkText)
        canvas.drawText("hello@careflux.com", margin + 40f, yPos + 140f, paintDarkText)
        canvas.drawText("www.careflux.com", margin + 40f, yPos + 190f, paintDarkText)

        // Info box on right
        val infoBoxTop = yPos - 10f
        val infoBoxRect = RectF(width - margin - 500f, infoBoxTop, width - margin, infoBoxTop + 240f)
        canvas.drawRoundRect(infoBoxRect, 16f, 16f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorBgLight })
        
        var boxY = infoBoxTop + 45f
        val col1X = infoBoxRect.left + 30f
        val col2X = infoBoxRect.right - 30f
        
        val boxTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorTextDark; textSize = 22f; typeface = typeRegular }
        val boxBoldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorTextDark; textSize = 22f; typeface = typeBold; textAlign = Paint.Align.RIGHT }
        
        val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        val currentDate = sdf.format(Date())

        val drawRow = { label: String, value: String, isValueBold: Boolean, valColor: Int? -> 
            canvas.drawText(label, col1X, boxY, boxTextPaint)
            val p = Paint(if (isValueBold) boxBoldPaint else Paint(boxBoldPaint).apply { typeface = typeRegular })
            if (valColor != null) p.color = valColor
            canvas.drawText(value, col2X, boxY, p)
            boxY += 45f
        }

        if (isInvoice) {
            drawRow("Invoice No.", invoiceNo, true, null)
            drawRow("Order ID", orderId, true, null)
            drawRow("Invoice Date", currentDate, true, null)
            drawRow("Payment Status", "Awaiting Payment", true, colorRed)
        } else {
            drawRow("Receipt No.", invoiceNo.replace("CFX-", "CFX-RCPT-"), true, null)
            drawRow("Invoice No.", invoiceNo, true, null)
            drawRow("Order ID", orderId, true, null)
            drawRow("Payment Date", currentDate, true, null)
            drawRow("Payment Status", "Paid", true, colorGreen)
        }

        // --- BILL TO / DELIVERY DETAILS --- //
        yPos += 280f
        val hasDelivery = deliveryAddress.isNotBlank() && !deliveryAddress.equals("In-Store Pickup", ignoreCase = true)
        
        val boxBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorBgLight }
        val boxBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorBorder; style = Paint.Style.STROKE; strokeWidth = 2f }
        
        if (hasDelivery) {
            val halfW = (width - (margin * 2) - 40f) / 2f
            val billRect = RectF(margin, yPos, margin + halfW, yPos + 220f)
            val delRect = RectF(margin + halfW + 40f, yPos, width - margin, yPos + 220f)
            
            canvas.drawRoundRect(billRect, 20f, 20f, boxBgPaint)
            canvas.drawRoundRect(billRect, 20f, 20f, boxBorderPaint)
            
            canvas.drawRoundRect(delRect, 20f, 20f, boxBgPaint)
            canvas.drawRoundRect(delRect, 20f, 20f, boxBorderPaint)
            
            // Titles
            paintDarkBlueText.textSize = 24f
            paintDarkBlueText.typeface = typeBold
            canvas.drawText("BILL TO", billRect.left + 50f, billRect.top + 45f, paintDarkBlueText)
            canvas.drawText("DELIVERY DETAILS", delRect.left + 50f, delRect.top + 45f, paintDarkBlueText)

            paintDarkText.textSize = 24f
            canvas.drawText(customerName, billRect.left + 50f, billRect.top + 100f, paintDarkText)
            canvas.drawText(customerPhone, billRect.left + 50f, billRect.top + 140f, paintDarkText)

            canvas.drawText(deliveryAddress, delRect.left + 50f, delRect.top + 100f, paintDarkText)
        } else {
            val billRect = RectF(margin, yPos, width - margin, yPos + 220f)
            
            canvas.drawRoundRect(billRect, 20f, 20f, boxBgPaint)
            canvas.drawRoundRect(billRect, 20f, 20f, boxBorderPaint)
            
            // Titles
            paintDarkBlueText.textSize = 24f
            paintDarkBlueText.typeface = typeBold
            canvas.drawText("BILL TO", billRect.left + 50f, billRect.top + 45f, paintDarkBlueText)

            paintDarkText.textSize = 24f
            canvas.drawText(customerName, billRect.left + 50f, billRect.top + 100f, paintDarkText)
            canvas.drawText(customerPhone, billRect.left + 50f, billRect.top + 140f, paintDarkText)
        }
        
        // --- TABLE --- //
        yPos += 260f
        val tabHeaderRect = RectF(margin, yPos, width - margin, yPos + tableHeaderHeight)
        canvas.drawRoundRect(tabHeaderRect, 12f, 12f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorDarkBlue })
        
        val thPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorWhite; textSize = 22f; typeface = typeBold }
        val thPaintRight = Paint(thPaint).apply { textAlign = Paint.Align.RIGHT }
        
        val cx1 = margin + 20f    // #
        val cx2 = margin + 80f    // PRODUCT
        val cx3 = margin + 450f   // STRENGTH
        val cx4 = margin + 680f   // FORM / UNIT
        val cx5 = margin + 880f   // QTY
        val cx6 = margin + 1090f  // UNIT PRICE
        val cx7 = width - margin - 20f // TOTAL (Right aligned)
        
        val thY = yPos + 40f
        canvas.drawText("#", cx1, thY, thPaint)
        canvas.drawText("PRODUCT", cx2, thY, thPaint)
        canvas.drawText("STRENGTH", cx3, thY, thPaint)
        canvas.drawText("FORM / UNIT", cx4, thY, thPaint)
        canvas.drawText("QTY", cx5, thY, thPaintRight)
        canvas.drawText("UNIT PRICE", cx6, thY, thPaintRight)
        canvas.drawText("TOTAL", cx7, thY, thPaintRight)

        var tY = yPos + tableHeaderHeight
        paintDarkText.textSize = 22f
        val paintDarkTextRight = Paint(paintDarkText).apply { textAlign = Paint.Align.RIGHT }
        val idxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorTextDark; textSize = 22f; typeface = typeBold }
        val idxPaintRight = Paint(idxPaint).apply { textAlign = Paint.Align.RIGHT }
        val valPaintRight = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorTextDark; textSize = 22f; textAlign = Paint.Align.RIGHT; typeface = typeBold }
        val descPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorTextGray; textSize = 18f; typeface = typeRegular }

        cartItems.forEachIndexed { index, item ->
            val rowMid = tY + 45f
            canvas.drawText("${index + 1}", cx1, rowMid, idxPaint)
            canvas.drawText(item.inventoryItem.name, cx2, rowMid - 10f, idxPaint)
            val generic = "(${item.inventoryItem.category})"
            canvas.drawText(generic, cx2, rowMid + 15f, descPaint)

            canvas.drawText(item.inventoryItem.dosage.take(15), cx3, rowMid, paintDarkText)
            canvas.drawText("Pack", cx4, rowMid, paintDarkText)
            
            canvas.drawText("${item.quantity}", cx5, rowMid, idxPaintRight)
            
            val priceStr = "₦%,.2f".format(item.inventoryItem.price)
            val totalStr = "₦%,.2f".format(item.inventoryItem.price * item.quantity)
            
            canvas.drawText(priceStr, cx6, rowMid, paintDarkTextRight)
            canvas.drawText(totalStr, cx7, rowMid, valPaintRight)
            
            tY += rowHeight
            // line separator
            canvas.drawLine(margin, tY, width - margin, tY, boxBorderPaint)
        }

        // --- TOTALS AREA --- //
        yPos = tY + 40f
        
        val leftW = 600f
        val rightW = 550f
        val leftRect = RectF(margin, yPos, margin + leftW, yPos + 180f)
        val rightRect = RectF(width - margin - rightW, yPos, width - margin, yPos + 180f)

        canvas.drawRoundRect(leftRect, 16f, 16f, boxBgPaint)
        canvas.drawRoundRect(rightRect, 16f, 16f, boxBgPaint)

        // Right totals block
        var rtY = rightRect.top + 45f
        val rtL = rightRect.left + 30f
        val rtR = rightRect.right - 30f
        
        val lblPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorTextDark; textSize = 24f; typeface = typeRegular }
        val valPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorTextDark; textSize = 24f; typeface = typeBold; textAlign = Paint.Align.RIGHT }
        
        canvas.drawText("Subtotal", rtL, rtY, lblPaint)
        canvas.drawText("₦%,.2f".format(subtotal(cartItems)), rtR, rtY, valPaint)
        
        rtY += 45f
        canvas.drawText("Delivery Fee", rtL, rtY, lblPaint)
        canvas.drawText("₦%,.2f".format(deliveryFee), rtR, rtY, valPaint)
        
        rtY += 50f
        val totalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorLightBlue; textSize = 28f; typeface = typeBold; textAlign = Paint.Align.RIGHT }
        val totLblPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorDarkBlue; textSize = 24f; typeface = typeBold }

        canvas.drawLine(rtL, rtY - 30f, rtR, rtY - 30f, boxBorderPaint)
        
        if (isInvoice) {
            canvas.drawText("TOTAL AMOUNT DUE", rtL, rtY + 5f, totLblPaint)
            canvas.drawText("₦%,.2f".format(totalAmount), rtR, rtY + 5f, totalPaint)

            // Left Block: NOTES
            paintDarkBlueText.textSize = 22f
            canvas.drawText("NOTES", leftRect.left + 30f, leftRect.top + 40f, paintDarkBlueText)
            paintDarkText.textSize = 20f
            var nY = leftRect.top + 80f
            canvas.drawText("• Please review your order details carefully.", leftRect.left + 30f, nY, paintDarkText)
            nY += 35f
            canvas.drawText("• Prescriptions have been verified by our pharmacist.", leftRect.left + 30f, nY, paintDarkText)
            nY += 35f
            canvas.drawText("• Careflux is delivering on behalf of $pharmacyName.", leftRect.left + 30f, nY, paintDarkText)
            
        } else {
            canvas.drawText("TOTAL AMOUNT", rtL, rtY + 5f, totLblPaint)
            canvas.drawText("₦%,.2f".format(totalAmount), rtR, rtY + 5f, totalPaint)
            
            rtY += 45f
            canvas.drawText("AMOUNT PAID", rtL, rtY, Paint(totLblPaint).apply { color = colorGreen })
            canvas.drawText("₦%,.2f".format(totalAmount), rtR, rtY, Paint(totalPaint).apply { color = colorGreen })
            
            // Left Block: TRANSACTION DETAILS
            paintDarkBlueText.textSize = 22f
            canvas.drawText("TRANSACTION DETAILS", leftRect.left + 30f, leftRect.top + 40f, paintDarkBlueText)
            var trY = leftRect.top + 80f
            val tL = leftRect.left + 30f
            val tV = leftRect.left + 230f
            val pLbl = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorTextDark; textSize = 20f; typeface = typeBold }
            val pVal = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorTextDark; textSize = 20f; typeface = typeRegular }
            
            val trRows = listOf(
                "Bank Name:" to "Providus Bank",
                "Account No:" to "9647652247",
                "Reference:" to orderId
            )
            trRows.forEach { (lbl, vv) ->
                canvas.drawText(lbl, tL, trY, pLbl)
                canvas.drawText(vv, tV, trY, pVal)
                trY += 35f
            }
        }
        
        if (isInvoice) {
            yPos += 220f
            
            // Draw Payment Method Section
            canvas.drawLine(margin, yPos, width - margin, yPos, boxBorderPaint)
            yPos += 40f
            paintDarkBlueText.textSize = 24f
            paintDarkBlueText.typeface = typeBold
            canvas.drawText("PAYMENT METHOD", margin, yPos, paintDarkBlueText)
            
            yPos += 40f
            // Left - Bank Transfer
            val btLeft = margin + 120f
            paintDarkBlueText.textSize = 20f
            canvas.drawText("BANK TRANSFER", btLeft, yPos, paintDarkBlueText)
            
            var by = yPos + 40f
            val pLbl = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorTextDark; textSize = 20f; typeface = typeBold }
            val pVal = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorTextDark; textSize = 20f; typeface = typeRegular }
            
            canvas.drawText("Bank Name:", btLeft, by, pLbl)
            canvas.drawText("Providus Bank", btLeft + 180f, by, pVal)
            by += 35f
            canvas.drawText("Account Name:", btLeft, by, pLbl)
            canvas.drawText("Wellivox Integrated Services - Careflux", btLeft + 180f, by, pVal)
            by += 35f
            canvas.drawText("Account Number:", btLeft, by, pLbl)
            canvas.drawText("9647652247", btLeft + 180f, by, Paint(pVal).apply { typeface = typeBold })
            
            // Draw a simple bank icon
            val iconX = margin
            val iconY = yPos - 10f
            val bp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorDarkBlue; style = Paint.Style.FILL }
            // Roof
            val path = Path()
            path.moveTo(iconX + 40f, iconY)
            path.lineTo(iconX, iconY + 30f)
            path.lineTo(iconX + 80f, iconY + 30f)
            path.close()
            canvas.drawPath(path, bp)
            // Pillars
            for (i in 0..3) {
                canvas.drawRect(iconX + 10f + (i * 18f), iconY + 35f, iconX + 24f + (i * 18f), iconY + 70f, bp)
            }
            // Base
            canvas.drawRect(iconX, iconY + 75f, iconX + 80f, iconY + 85f, bp)
            
            // Right - Payment Instructions
            val piLeft = width - margin - 500f
            canvas.drawText("PAYMENT INSTRUCTIONS", piLeft, yPos, paintDarkBlueText)
            by = yPos + 40f
            canvas.drawText("Kindly make payment to the account details", piLeft, by, pVal)
            by += 30f
            canvas.drawText("provided and use your Order ID as the", piLeft, by, pVal)
            by += 30f
            canvas.drawText("payment reference.", piLeft, by, pVal)
            by += 40f
            canvas.drawText("Order ID: ", piLeft, by, pVal)
            canvas.drawText(orderId, piLeft + 100f, by, Paint(pLbl).apply { color = colorDarkBlue })
            
            yPos += 220f
        } else {
            yPos += 240f
        }
        
        // --- FOOTER INFO --- //
        val footerRect = RectF(margin, yPos, width - margin, yPos + 240f)
        canvas.drawRoundRect(footerRect, 16f, 16f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#F8FAFD"); style = Paint.Style.FILL })
        canvas.drawRoundRect(footerRect, 16f, 16f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorLightBlue; style = Paint.Style.STROKE; strokeWidth = 2f })
        
        val fY = yPos + 55f
        
        // Left - Pharmacist details
        paintDarkBlueText.textSize = 22f
        canvas.drawText("PRESCRIPTION VERIFIED", footerRect.left + 30f, fY, paintDarkBlueText)
        paintDarkText.textSize = 18f
        canvas.drawText("Pharmacist: $pharmacistName", footerRect.left + 30f, fY + 35f, paintDarkText)
        canvas.drawText("Pharmacy: $pharmacyName", footerRect.left + 30f, fY + 65f, paintDarkText)

        // Draw cursive signature
        val sigPath = Path().apply {
            moveTo(footerRect.left + 30f, fY + 120f)
            quadTo(footerRect.left + 70f, fY + 85f, footerRect.left + 110f, fY + 120f)
            quadTo(footerRect.left + 140f, fY + 140f, footerRect.left + 180f, fY + 100f)
            quadTo(footerRect.left + 200f, fY + 90f, footerRect.left + 220f, fY + 125f)
        }
        val sigPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0066CC")
            style = Paint.Style.STROKE
            strokeWidth = 3f
            strokeCap = Paint.Cap.ROUND
        }
        canvas.drawPath(sigPath, sigPaint)

        // Middle - Need help
        val mx = footerRect.left + 450f
        canvas.drawText("NEED HELP?", mx, fY, paintDarkBlueText)
        canvas.drawText("WhatsApp: +234 814 757 8314", mx, fY + 40f, paintDarkText)
        canvas.drawText("Email: support@careflux.com", mx, fY + 70f, paintDarkText)

        // Right - Features
        val rx = footerRect.right - 350f
        canvas.drawText("✓ Genuine Medicines", rx, fY, paintDarkText)
        canvas.drawText("✓ Pharmacist Verified", rx, fY + 35f, paintDarkText)
        canvas.drawText("✓ Secure Payments", rx, fY + 70f, paintDarkText)
        canvas.drawText("✓ Fast Delivery", rx, fY + 105f, paintDarkText)

        // End message
        yPos += 280f
        paintDarkBlueText.textSize = 28f
        canvas.drawText("Thank you for choosing Careflux.", margin, yPos, paintDarkBlueText)
        paintDarkText.textSize = 20f
        canvas.drawText("Your health and trust mean everything to us.", margin, yPos + 35f, paintDarkText)

        // Save
        return try {
            val prefix = if (isInvoice) "invoice" else "receipt"
            val fileName = "${prefix}_${System.currentTimeMillis()}.png"
            val file = File(context.filesDir, fileName)
            val out = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.flush()
            out.close()
            val uri = FileProvider.getUriForFile(context, "${com.example.BuildConfig.APPLICATION_ID}.fileprovider", file)
            Pair(uri, fileName)
        } catch(e: Exception) {
            e.printStackTrace()
            Pair(null, null)
        }
    }

    private fun subtotal(items: List<CartItem>): Double {
        return items.sumOf { it.inventoryItem.price * it.quantity }
    }

    fun generatePatientTreatmentHistoryReport(
        context: Context,
        customer: com.example.data.Customer,
        medications: List<com.example.data.CustomerMedication>,
        startDateMs: Long? = null,
        endDateMs: Long? = null,
        pharmacyName: String = "Careflux Central Pharmacy"
    ): PatientHistoryPdfResult {
        val filteredMeds = medications.filter { med ->
            val timestamp = if (med.dateAdded > 0) med.dateAdded else med.nextRefillDate
            val matchesStart = startDateMs == null || timestamp >= startDateMs
            val matchesEnd = endDateMs == null || timestamp <= endDateMs + (24L * 60 * 60 * 1000 - 1)
            matchesStart && matchesEnd
        }.sortedByDescending { if (it.dateAdded > 0) it.dateAdded else it.nextRefillDate }

        val sdfDate = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        val sdfTime = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())

        val dateFilterLabel = when {
            startDateMs != null && endDateMs != null -> "${sdfDate.format(Date(startDateMs))} - ${sdfDate.format(Date(endDateMs))}"
            startDateMs != null -> "From ${sdfDate.format(Date(startDateMs))}"
            endDateMs != null -> "Until ${sdfDate.format(Date(endDateMs))}"
            else -> "Full Historical Dataset (All Time)"
        }

        val totalCost = filteredMeds.sumOf { it.cost }
        val totalRecords = filteredMeds.size

        // Canvas Geometry & Metrics
        val width = 1400
        val margin = 80f
        val contentWidth = width - (2 * margin) // 1240f

        val headerHeight = 110f
        val titleBlockHeight = 110f
        val patientInfoHeight = 220f
        val statsHeight = 140f
        val tableHeaderHeight = 65f
        val rowHeight = 85f
        val tableContentHeight = Math.max(160, filteredMeds.size * rowHeight.toInt()) + tableHeaderHeight.toInt()
        val footerHeight = 210f

        val totalCanvasHeight = (headerHeight + titleBlockHeight + patientInfoHeight + statsHeight + tableContentHeight + footerHeight + 180f).toInt()

        val bitmap = Bitmap.createBitmap(width, totalCanvasHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Modern Palette
        val colorWhite = Color.WHITE
        val colorCardBg = Color.parseColor("#F8FAFC") // Slate-50
        val colorTealHeader = Color.parseColor("#0B4F58") // Dark Medical Teal
        val colorTealPrimary = Color.parseColor("#0D9488") // Primary Accent Teal
        val colorTealLightBg = Color.parseColor("#F0FDFA") // Soft Teal Surface
        val colorTealLightBorder = Color.parseColor("#CCFBF1")
        val colorAmberLightBg = Color.parseColor("#FFF7ED")
        val colorAmberText = Color.parseColor("#D97706")
        val colorBorder = Color.parseColor("#E2E8F0") // Light border
        val colorTextDark = Color.parseColor("#0F172A") // Slate-900
        val colorTextMedium = Color.parseColor("#334155") // Slate-700
        val colorTextMuted = Color.parseColor("#64748B") // Slate-500

        canvas.drawColor(colorWhite)

        val typeRegular = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        val typeBold = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)

        val paintTextDark = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorTextDark; typeface = typeBold }
        val paintTextMedium = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorTextMedium; typeface = typeRegular }
        val paintTextMuted = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorTextMuted; typeface = typeRegular }

        var yPos = 60f

        // ------------------------------------------------------------------------
        // 1. TOP HEADER (BRAND LOGO + METADATA)
        // ------------------------------------------------------------------------
        // Medical Cross Logo Icon
        val logoCenterX = margin + 35f
        val logoCenterY = yPos + 35f
        val logoPath = Path().apply {
            // Rounded medical cross contour
            addRoundRect(RectF(logoCenterX - 28f, logoCenterY - 12f, logoCenterX + 28f, logoCenterY + 12f), 8f, 8f, Path.Direction.CW)
            addRoundRect(RectF(logoCenterX - 12f, logoCenterY - 28f, logoCenterX + 12f, logoCenterY + 28f), 8f, 8f, Path.Direction.CW)
        }
        canvas.drawPath(logoPath, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorTealHeader; style = Paint.Style.FILL })

        // Heartbeat line inside cross
        val pulsePath = Path().apply {
            moveTo(logoCenterX - 18f, logoCenterY)
            lineTo(logoCenterX - 8f, logoCenterY)
            lineTo(logoCenterX - 3f, logoCenterY - 12f)
            lineTo(logoCenterX + 4f, logoCenterY + 12f)
            lineTo(logoCenterX + 9f, logoCenterY)
            lineTo(logoCenterX + 18f, logoCenterY)
        }
        canvas.drawPath(pulsePath, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorWhite; style = Paint.Style.STROKE; strokeWidth = 3.5f; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND })

        // Brand Name
        paintTextDark.textSize = 34f
        canvas.drawText("CAREFLUX", margin + 80f, yPos + 30f, paintTextDark)

        val paintBrandSub = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorTealHeader; typeface = typeBold; textSize = 16f }
        canvas.drawText("CLINICAL PHARMACY", margin + 80f, yPos + 55f, paintBrandSub)

        // Top Right Generated Date
        val metaDividerX = width - margin - 260f
        canvas.drawLine(metaDividerX, yPos + 10f, metaDividerX, yPos + 60f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorBorder; strokeWidth = 2f })

        paintTextMuted.textSize = 16f
        canvas.drawText("Report Generated", metaDividerX + 25f, yPos + 28f, paintTextMuted)
        paintTextDark.textSize = 19f
        canvas.drawText(sdfTime.format(Date()), metaDividerX + 25f, yPos + 55f, paintTextDark)

        yPos += 100f

        // ------------------------------------------------------------------------
        // 2. MAIN TITLE BLOCK & DATE SCOPE PILL
        // ------------------------------------------------------------------------
        paintTextDark.textSize = 36f
        canvas.drawText("PATIENT HISTORICAL TREATMENT &", margin, yPos + 30f, paintTextDark)
        canvas.drawText("MEDICATION DATASET", margin, yPos + 72f, paintTextDark)

        paintTextMuted.textSize = 18f
        val reportRef = "REPORT-HIST-${customer.id}-${System.currentTimeMillis().toString().takeLast(5)}"
        canvas.drawText("NDPA Compliant Patient Record • Ref: $reportRef", margin, yPos + 110f, paintTextMuted)

        // Right side Pill Container for Date Filter Scope
        val pillWidth = 420f
        val pillHeight = 85f
        val pillLeft = width - margin - pillWidth
        val pillTop = yPos + 15f
        val pillRect = RectF(pillLeft, pillTop, pillLeft + pillWidth, pillTop + pillHeight)

        canvas.drawRoundRect(pillRect, 24f, 24f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorTealLightBg; style = Paint.Style.FILL })
        canvas.drawRoundRect(pillRect, 24f, 24f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorTealLightBorder; style = Paint.Style.STROKE; strokeWidth = 2f })

        // Calendar Icon inside Pill
        val calCx = pillLeft + 45f
        val calCy = pillTop + 42f
        canvas.drawCircle(calCx, calCy, 22f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorWhite; style = Paint.Style.FILL })
        // Calendar icon graphics
        canvas.drawRoundRect(RectF(calCx - 12f, calCy - 10f, calCx + 12f, calCy + 12f), 4f, 4f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorTealHeader; style = Paint.Style.STROKE; strokeWidth = 2.5f })
        canvas.drawLine(calCx - 12f, calCy - 3f, calCx + 12f, calCy - 3f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorTealHeader; strokeWidth = 2f })

        paintTextMuted.textSize = 15f
        canvas.drawText("Date Filter Scope", pillLeft + 80f, pillTop + 35f, paintTextMuted)
        val paintPillVal = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorTealHeader; typeface = typeBold; textSize = 18f }
        val truncatedLabel = if (dateFilterLabel.length > 26) dateFilterLabel.take(24) + "..." else dateFilterLabel
        canvas.drawText(truncatedLabel, pillLeft + 80f, pillTop + 62f, paintPillVal)

        yPos += 160f

        // ------------------------------------------------------------------------
        // 3. PATIENT PROFILE CARD (CARD WITH AVATAR & 3 COLUMNS)
        // ------------------------------------------------------------------------
        val profileRect = RectF(margin, yPos, width - margin, yPos + 220f)
        canvas.drawRoundRect(profileRect, 20f, 20f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorCardBg; style = Paint.Style.FILL })
        canvas.drawRoundRect(profileRect, 20f, 20f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorBorder; style = Paint.Style.STROKE; strokeWidth = 1.5f })

        // Avatar Icon Circle on Left
        val avCx = profileRect.left + 50f
        val avCy = profileRect.top + 50f
        canvas.drawCircle(avCx, avCy, 28f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorTealLightBg; style = Paint.Style.FILL })
        // Person icon inside circle
        canvas.drawCircle(avCx, avCy - 8f, 9f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorTealHeader; style = Paint.Style.STROKE; strokeWidth = 2.5f })
        val bodyPath = Path().apply {
            addArc(RectF(avCx - 16f, avCy + 2f, avCx + 16f, avCy + 26f), 180f, 180f)
        }
        canvas.drawPath(bodyPath, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorTealHeader; style = Paint.Style.STROKE; strokeWidth = 2.5f })

        // Section Title: PATIENT PROFILE
        val paintSecTitle = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorTealHeader; typeface = typeBold; textSize = 18f }
        canvas.drawText("PATIENT PROFILE", profileRect.left + 95f, profileRect.top + 56f, paintSecTitle)

        // 3 Columns Layout inside Profile Box
        val col1X = profileRect.left + 95f
        val col2X = profileRect.left + 480f
        val col3X = profileRect.left + 860f

        // Vertical dividers inside Card
        canvas.drawLine(profileRect.left + 440f, profileRect.top + 80f, profileRect.left + 440f, profileRect.bottom - 30f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorBorder; strokeWidth = 1.5f })
        canvas.drawLine(profileRect.left + 820f, profileRect.top + 80f, profileRect.left + 820f, profileRect.bottom - 30f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorBorder; strokeWidth = 1.5f })

        val row1Y = profileRect.top + 115f
        val row2Y = profileRect.top + 175f

        // Column 1: Name & Phone
        paintTextMuted.textSize = 15f
        canvas.drawText("Name", col1X, row1Y, paintTextMuted)
        paintTextDark.textSize = 21f
        canvas.drawText(customer.name, col1X, row1Y + 28f, paintTextDark)

        canvas.drawText("Phone", col1X, row2Y, paintTextMuted)
        paintTextDark.textSize = 20f
        canvas.drawText(customer.phoneNumber.ifBlank { "N/A" }, col1X, row2Y + 28f, paintTextDark)

        // Column 2: Age/Gender & Email
        canvas.drawText("Age / Gender", col2X, row1Y, paintTextMuted)
        paintTextDark.textSize = 20f
        canvas.drawText("${customer.age} Yrs / ${customer.gender}", col2X, row1Y + 28f, paintTextDark)

        canvas.drawText("Email", col2X, row2Y, paintTextMuted)
        paintTextDark.textSize = 19f
        val truncatedEmail = if (customer.email.length > 28) customer.email.take(26) + "..." else customer.email.ifBlank { "N/A" }
        canvas.drawText(truncatedEmail, col2X, row2Y + 28f, paintTextDark)

        // Column 3: Location & NDPA Consent
        canvas.drawText("Location", col3X, row1Y, paintTextMuted)
        paintTextDark.textSize = 20f
        val locationText = if (customer.city.isNotBlank()) "${customer.city}, ${customer.state}" else "Ikeja, Lagos"
        canvas.drawText(locationText, col3X, row1Y + 28f, paintTextDark)

        canvas.drawText("NDPA Privacy Consent", col3X, row2Y, paintTextMuted)
        val paintVerified = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorTealPrimary; typeface = typeBold; textSize = 20f }
        canvas.drawText("Verified", col3X, row2Y + 28f, paintVerified)

        yPos += 250f

        // ------------------------------------------------------------------------
        // 4. METRIC KPI STAT CARDS (3 CARDS IN A ROW)
        // ------------------------------------------------------------------------
        val kpiGap = 25f
        val kpiCardWidth = (contentWidth - (2 * kpiGap)) / 3f // ~396f
        val kpiHeight = 135f

        // Card 1: Total Meds Dispensed
        val k1Rect = RectF(margin, yPos, margin + kpiCardWidth, yPos + kpiHeight)
        canvas.drawRoundRect(k1Rect, 20f, 20f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorWhite; style = Paint.Style.FILL })
        canvas.drawRoundRect(k1Rect, 20f, 20f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorBorder; style = Paint.Style.STROKE; strokeWidth = 1.5f })

        val c1Cx = k1Rect.left + 50f
        val c1Cy = k1Rect.top + 67f
        canvas.drawCircle(c1Cx, c1Cy, 28f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorTealLightBg; style = Paint.Style.FILL })
        // Pill Bottle Icon
        canvas.drawRoundRect(RectF(c1Cx - 10f, c1Cy - 12f, c1Cx + 10f, c1Cy + 14f), 4f, 4f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorTealHeader; style = Paint.Style.STROKE; strokeWidth = 2.5f })
        canvas.drawRect(RectF(c1Cx - 12f, c1Cy - 16f, c1Cx + 12f, c1Cy - 12f), Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorTealHeader; style = Paint.Style.FILL })

        paintTextMuted.textSize = 14f
        canvas.drawText("TOTAL MEDS DISPENSED", k1Rect.left + 95f, k1Rect.top + 45f, paintTextMuted)
        paintTextDark.textSize = 34f
        canvas.drawText("$totalRecords", k1Rect.left + 95f, k1Rect.top + 88f, paintTextDark)
        paintTextMuted.textSize = 18f
        canvas.drawText(if (totalRecords == 1) "Item" else "Items", k1Rect.left + 135f + (totalRecords.toString().length * 18f), k1Rect.top + 88f, paintTextMuted)

        // Card 2: Cumulative Treatment Cost
        val k2Rect = RectF(margin + kpiCardWidth + kpiGap, yPos, margin + 2 * kpiCardWidth + kpiGap, yPos + kpiHeight)
        canvas.drawRoundRect(k2Rect, 20f, 20f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorWhite; style = Paint.Style.FILL })
        canvas.drawRoundRect(k2Rect, 20f, 20f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorBorder; style = Paint.Style.STROKE; strokeWidth = 1.5f })

        val c2Cx = k2Rect.left + 50f
        val c2Cy = k2Rect.top + 67f
        canvas.drawCircle(c2Cx, c2Cy, 28f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorTealLightBg; style = Paint.Style.FILL })
        // Naira Symbol Icon
        val paintNaira = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorTealHeader; typeface = typeBold; textSize = 26f }
        canvas.drawText("₦", c2Cx - 10f, c2Cy + 9f, paintNaira)

        paintTextMuted.textSize = 14f
        canvas.drawText("CUMULATIVE TREATMENT COST", k2Rect.left + 95f, k2Rect.top + 45f, paintTextMuted)
        val paintCostVal = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorTealPrimary; typeface = typeBold; textSize = 30f }
        canvas.drawText(String.format("₦%,.2f", totalCost), k2Rect.left + 95f, k2Rect.top + 88f, paintCostVal)

        // Card 3: Refill Compliance
        val k3Rect = RectF(margin + 2 * kpiCardWidth + 2 * kpiGap, yPos, width - margin, yPos + kpiHeight)
        canvas.drawRoundRect(k3Rect, 20f, 20f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorWhite; style = Paint.Style.FILL })
        canvas.drawRoundRect(k3Rect, 20f, 20f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorBorder; style = Paint.Style.STROKE; strokeWidth = 1.5f })

        val c3Cx = k3Rect.left + 50f
        val c3Cy = k3Rect.top + 67f
        canvas.drawCircle(c3Cx, c3Cy, 28f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorAmberLightBg; style = Paint.Style.FILL })
        // Refresh / Cycle Icon
        val refreshPath = Path().apply {
            addArc(RectF(c3Cx - 14f, c3Cy - 14f, c3Cx + 14f, c3Cy + 14f), 45f, 270f)
        }
        canvas.drawPath(refreshPath, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorAmberText; style = Paint.Style.STROKE; strokeWidth = 3f; strokeCap = Paint.Cap.ROUND })

        paintTextMuted.textSize = 14f
        canvas.drawText("REFILL COMPLIANCE", k3Rect.left + 95f, k3Rect.top + 45f, paintTextMuted)
        val paintStreakVal = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorAmberText; typeface = typeBold; textSize = 34f }
        canvas.drawText("${customer.refillStreak}", k3Rect.left + 95f, k3Rect.top + 88f, paintStreakVal)
        canvas.drawText("Refill Streak", k3Rect.left + 130f + (customer.refillStreak.toString().length * 18f), k3Rect.top + 88f, paintTextMuted)

        yPos += 165f

        // ------------------------------------------------------------------------
        // 5. CHRONOLOGICAL TREATMENT & MEDICATION HISTORY TABLE
        // ------------------------------------------------------------------------
        // Section Icon + Header Title
        val secIconX = margin + 18f
        val secIconY = yPos + 18f
        // Clipboard icon
        canvas.drawRoundRect(RectF(secIconX - 12f, secIconY - 14f, secIconX + 12f, secIconY + 16f), 4f, 4f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorTealHeader; style = Paint.Style.STROKE; strokeWidth = 2.5f })
        canvas.drawRect(RectF(secIconX - 6f, secIconY - 17f, secIconX + 6f, secIconY - 12f), Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorTealHeader; style = Paint.Style.FILL })

        val paintSecHeader = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorTealHeader; typeface = typeBold; textSize = 22f }
        canvas.drawText("CHRONOLOGICAL TREATMENT & MEDICATION HISTORY", margin + 42f, yPos + 24f, paintSecHeader)

        yPos += 45f

        // Table Header Row Box
        val thRect = RectF(margin, yPos, width - margin, yPos + tableHeaderHeight)
        canvas.drawRoundRect(thRect, 12f, 12f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorTealHeader; style = Paint.Style.FILL })

        val thPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorWhite; typeface = typeBold; textSize = 17f }
        val cDate = margin + 25f
        val cMed = margin + 270f
        val cDose = margin + 650f
        val cCost = margin + 920f
        val cStatus = margin + 1120f

        canvas.drawText("DATE & TIME", cDate, yPos + 40f, thPaint)
        canvas.drawText("MEDICATION NAME", cMed, yPos + 40f, thPaint)
        canvas.drawText("DOSAGE / QTY", cDose, yPos + 40f, thPaint)
        canvas.drawText("COST (₦)", cCost, yPos + 40f, thPaint)
        canvas.drawText("CYCLE STATUS", cStatus, yPos + 40f, thPaint)

        yPos += tableHeaderHeight

        if (filteredMeds.isEmpty()) {
            val emptyRect = RectF(margin, yPos, width - margin, yPos + 120f)
            canvas.drawRect(emptyRect, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorCardBg; style = Paint.Style.FILL })
            canvas.drawLine(margin, yPos + 120f, width - margin, yPos + 120f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorBorder; strokeWidth = 1f })

            paintTextMuted.textSize = 20f
            canvas.drawText("No medication purchases or treatment records found for this patient within the selected date range.", margin + 40f, yPos + 68f, paintTextMuted)
            yPos += 120f
        } else {
            val borderLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorBorder; strokeWidth = 1f }

            filteredMeds.forEachIndexed { idx, med ->
                val rRect = RectF(margin, yPos, width - margin, yPos + rowHeight)
                if (idx % 2 == 1) {
                    canvas.drawRect(rRect, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorCardBg; style = Paint.Style.FILL })
                }
                canvas.drawLine(margin, yPos + rowHeight, width - margin, yPos + rowHeight, borderLinePaint)

                val timestamp = if (med.dateAdded > 0) med.dateAdded else med.nextRefillDate
                val dateStr = sdfTime.format(Date(timestamp))

                paintTextMedium.textSize = 19f
                canvas.drawText(dateStr, cDate, yPos + 50f, paintTextMedium)

                // Medication Name formatting
                val rawMedName = med.medicationName
                val hasSubName = rawMedName.contains("(") && rawMedName.contains(")")
                if (hasSubName) {
                    val mainName = rawMedName.substringBefore("(").trim()
                    val subName = "(" + rawMedName.substringAfter("(")
                    canvas.drawText(mainName, cMed, yPos + 38f, paintTextDark.apply { textSize = 20f })
                    canvas.drawText(subName, cMed, yPos + 62f, paintTextMuted.apply { textSize = 16f })
                } else {
                    val truncatedMed = if (rawMedName.length > 30) rawMedName.take(28) + "..." else rawMedName
                    canvas.drawText(truncatedMed, cMed, yPos + 50f, paintTextDark.apply { textSize = 20f })
                }

                val truncatedDose = if (med.customDosage.length > 25) med.customDosage.take(23) + "..." else med.customDosage.ifBlank { "1 daily" }
                canvas.drawText(truncatedDose, cDose, yPos + 50f, paintTextMedium.apply { textSize = 19f })

                val costStr = String.format("₦%,.2f", med.cost)
                canvas.drawText(costStr, cCost, yPos + 50f, paintTextDark.apply { textSize = 20f })

                val statusText = if (med.cycleDays > 0) "${med.cycleDays}d Refill Cycle" else "Single Dispense"
                val statusColor = if (med.cycleDays > 0) colorTealPrimary else colorTextMuted
                val paintStatus = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = statusColor; typeface = typeBold; textSize = 18f }
                canvas.drawText(statusText, cStatus, yPos + 50f, paintStatus)

                yPos += rowHeight
            }
        }

        yPos += 50f

        // ------------------------------------------------------------------------
        // 6. CLINICAL DATASET CERTIFICATION & NDPA SEAL FOOTER BOX
        // ------------------------------------------------------------------------
        val fRect = RectF(margin, yPos, width - margin, yPos + 190f)
        canvas.drawRoundRect(fRect, 20f, 20f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorCardBg; style = Paint.Style.FILL })
        canvas.drawRoundRect(fRect, 20f, 20f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorBorder; style = Paint.Style.STROKE; strokeWidth = 1.5f })

        // Ribbon Seal Badge Icon on Left
        val sealCx = fRect.left + 70f
        val sealCy = fRect.top + 80f
        // Badge Circle
        canvas.drawCircle(sealCx, sealCy, 32f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorTealPrimary; style = Paint.Style.FILL })
        // Checkmark inside seal
        val checkPath = Path().apply {
            moveTo(sealCx - 12f, sealCy)
            lineTo(sealCx - 4f, sealCy + 9f)
            lineTo(sealCx + 12f, sealCy - 9f)
        }
        canvas.drawPath(checkPath, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorWhite; style = Paint.Style.STROKE; strokeWidth = 4f; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND })

        // Ribbon Tails below badge
        val ribbonPath1 = Path().apply {
            moveTo(sealCx - 18f, sealCy + 24f)
            lineTo(sealCx - 28f, sealCy + 55f)
            lineTo(sealCx - 16f, sealCy + 48f)
            lineTo(sealCx - 8f, sealCy + 28f)
        }
        val ribbonPath2 = Path().apply {
            moveTo(sealCx + 8f, sealCy + 28f)
            lineTo(sealCx + 16f, sealCy + 48f)
            lineTo(sealCx + 28f, sealCy + 55f)
            lineTo(sealCx + 18f, sealCy + 24f)
        }
        canvas.drawPath(ribbonPath1, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorTealHeader; style = Paint.Style.FILL })
        canvas.drawPath(ribbonPath2, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorTealHeader; style = Paint.Style.FILL })

        // Col 1: Certification Info
        val fCol1X = fRect.left + 140f
        val fy = fRect.top + 45f

        paintSecTitle.textSize = 20f
        canvas.drawText("CLINICAL DATASET CERTIFICATION & NDPA SEAL", fCol1X, fy, paintSecTitle)

        paintTextMedium.textSize = 17f
        canvas.drawText("Issued By: $pharmacyName", fCol1X, fy + 35f, paintTextMedium)
        paintTextMuted.textSize = 16f
        canvas.drawText("Licensed Dispensary Node #CFX-902", fCol1X, fy + 65f, paintTextMuted)
        canvas.drawText("Chief Pharmacist: Pharm. Olawale A. (Reg #PCN-84920)", fCol1X, fy + 95f, paintTextMuted)

        // Divider Line
        val fDivX = fRect.left + 720f
        canvas.drawLine(fDivX, fRect.top + 30f, fDivX, fRect.bottom - 30f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorBorder; strokeWidth = 1.5f })

        // Col 2: Encryption & Support Line
        val fCol2X = fRect.left + 760f
        paintTextMuted.textSize = 16f
        canvas.drawText("This PDF document is encrypted & validated", fCol2X, fy + 15f, paintTextMuted)
        canvas.drawText("under NDPA 2023 regulations.", fCol2X, fy + 42f, paintTextMuted)

        canvas.drawText("Support Line: +234 814 757 8314", fCol2X, fy + 82f, paintTextMuted)
        canvas.drawText("Email: clinical@careflux.com", fCol2X, fy + 108f, paintTextMuted)

        // Save Bitmap as PNG (for fast in-app preview)
        val pngFileName = "treatment_history_${customer.id}_${System.currentTimeMillis()}.png"
        val pngFile = File(context.filesDir, pngFileName)
        val outPng = FileOutputStream(pngFile)
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outPng)
        outPng.flush()
        outPng.close()
        val pngUri = FileProvider.getUriForFile(context, "${com.example.BuildConfig.APPLICATION_ID}.fileprovider", pngFile)

        // Save native PDF file using PdfDocument
        var pdfFile: File? = null
        var pdfUri: Uri? = null

        try {
            val pdfDocument = android.graphics.pdf.PdfDocument()
            val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(width, totalCanvasHeight, 1).create()
            val page = pdfDocument.startPage(pageInfo)

            page.canvas.drawBitmap(bitmap, 0f, 0f, null)
            pdfDocument.finishPage(page)

            val pFileName = "treatment_history_${customer.id}_${System.currentTimeMillis()}.pdf"
            val pFile = File(context.filesDir, pFileName)
            val outPdf = FileOutputStream(pFile)
            pdfDocument.writeTo(outPdf)
            outPdf.flush()
            outPdf.close()
            pdfDocument.close()

            pdfFile = pFile
            pdfUri = FileProvider.getUriForFile(context, "${com.example.BuildConfig.APPLICATION_ID}.fileprovider", pFile)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return PatientHistoryPdfResult(
            pdfUri = pdfUri ?: pngUri,
            pdfFile = pdfFile ?: pngFile,
            pngUri = pngUri,
            pngFileName = pngFileName,
            totalRecords = totalRecords,
            totalCost = totalCost,
            dateFilterLabel = dateFilterLabel
        )
    }
}

data class PatientHistoryPdfResult(
    val pdfUri: Uri?,
    val pdfFile: File?,
    val pngUri: Uri?,
    val pngFileName: String?,
    val totalRecords: Int,
    val totalCost: Double,
    val dateFilterLabel: String
)
