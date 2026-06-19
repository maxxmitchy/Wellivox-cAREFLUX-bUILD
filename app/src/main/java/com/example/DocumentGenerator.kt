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
        orderId: String = "ORD-${System.currentTimeMillis().toString().takeLast(6)}"
    ): Pair<Uri?, String?> {
        val width = 1400
        val headerHeight = 450
        val billToHeight = 250
        val tableHeaderHeight = 60
        val rowHeight = 80
        val itemsHeight = (cartItems.size * rowHeight) + tableHeaderHeight
        val totalsHeight = 350
        val paymentHeight = if (isInvoice) 250 else 0
        val footerHeight = 250
        
        val height = headerHeight + billToHeight + itemsHeight + totalsHeight + paymentHeight + footerHeight + 100
        
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
        paintDarkBlueText.textSize = 64f
        canvas.drawText("CAREFLUX", margin + 100f, yPos, paintDarkBlueText)
        
        paintLightBlueText.typeface = typeRegular
        paintLightBlueText.textSize = 48f
        canvas.drawText("P H A R M A C Y", margin + 100f, yPos + 60f, paintLightBlueText)
        
        paintGrayText.typeface = typeRegular
        paintGrayText.textSize = 28f
        // canvas.drawText("Your Health. Our Priority.", margin + 100f, yPos + 110f, paintGrayText)

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
        canvas.drawText("hello@carefluxpharmacy.com", margin + 40f, yPos + 140f, paintDarkText)
        canvas.drawText("www.carefluxpharmacy.com", margin + 40f, yPos + 190f, paintDarkText)
        // canvas.drawText("RC: 1670123    |    CAC: BN 3027198", margin + 40f, yPos + 240f, paintGrayText)

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
        val halfW = (width - (margin * 2) - 40f) / 2f
        val billRect = RectF(margin, yPos, margin + halfW, yPos + 220f)
        val delRect = RectF(margin + halfW + 40f, yPos, width - margin, yPos + 220f)
        
        val boxBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorBgLight }
        val boxBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorBorder; style = Paint.Style.STROKE; strokeWidth = 2f }
        
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

        canvas.drawText(deliveryAddress.takeIf { it.isNotBlank() } ?: "Pickup", delRect.left + 50f, delRect.top + 100f, paintDarkText)
        
        // --- TABLE --- //
        yPos += 260f
        val tabHeaderRect = RectF(margin, yPos, width - margin, yPos + tableHeaderHeight)
        canvas.drawRoundRect(tabHeaderRect, 12f, 12f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorDarkBlue })
        
        val thPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorWhite; textSize = 22f; typeface = typeBold }
        val cx1 = margin + 30f    // #
        val cx2 = margin + 100f   // PRODUCT
        val cx3 = margin + 500f   // STRENGTH
        val cx4 = margin + 700f   // FORM / UNIT
        val cx5 = margin + 900f   // QTY
        val cx6 = margin + 1000f  // UNIT PRICE
        val cx7 = width - margin - 30f // TOTAL (Right aligned)
        
        val thY = yPos + 40f
        canvas.drawText("#", cx1, thY, thPaint)
        canvas.drawText("PRODUCT", cx2, thY, thPaint)
        canvas.drawText("STRENGTH", cx3, thY, thPaint)
        canvas.drawText("FORM / UNIT", cx4, thY, thPaint)
        canvas.drawText("QTY", cx5, thY, thPaint)
        canvas.drawText("UNIT PRICE", cx6, thY, thPaint)
        
        val thPaintRight = Paint(thPaint).apply { textAlign = Paint.Align.RIGHT }
        canvas.drawText("TOTAL", cx7, thY, thPaintRight)

        var tY = yPos + tableHeaderHeight
        paintDarkText.textSize = 22f
        val idxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorTextDark; textSize = 22f; typeface = typeBold }
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
            
            canvas.drawText("${item.quantity}", cx5, rowMid, idxPaint)
            
            val priceStr = "₦%,.2f".format(item.inventoryItem.price)
            val totalStr = "₦%,.2f".format(item.inventoryItem.price * item.quantity)
            
            canvas.drawText(priceStr, cx6, rowMid, paintDarkText)
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
            canvas.drawText("• This invoice is valid for 72 hours.", leftRect.left + 30f, nY, paintDarkText)
            
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
            canvas.drawText("Order ID: ", piLeft, by, pLbl)
            canvas.drawText(orderId, piLeft + 100f, by, Paint(pLbl).apply { color = colorDarkBlue })
            
            yPos += 140f
        } else {
            yPos += 240f
        }
        
        // --- FOOTER INFO --- //
        val footerRect = RectF(margin, yPos, width - margin, yPos + 180f)
        canvas.drawRoundRect(footerRect, 16f, 16f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#F8FAFD"); style = Paint.Style.FILL })
        canvas.drawRoundRect(footerRect, 16f, 16f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorLightBlue; style = Paint.Style.STROKE; strokeWidth = 2f })
        
        val fY = yPos + 60f
        
        // Left - Pharmacist details
        paintDarkBlueText.textSize = 22f
        canvas.drawText("PRESCRIPTION VERIFIED", footerRect.left + 30f, fY, paintDarkBlueText)
        paintDarkText.textSize = 18f
        canvas.drawText("Pharmacist: Pharm. Olawale A.", footerRect.left + 30f, fY + 40f, paintDarkText)
        // canvas.drawText("PCN Number: PCN-0054321", footerRect.left + 30f, fY + 70f, paintDarkText)

        // Middle - Need help
        val mx = footerRect.left + 450f
        canvas.drawText("NEED HELP?", mx, fY, paintDarkBlueText)
        canvas.drawText("WhatsApp: +234 814 757 8314", mx, fY + 40f, paintDarkText)
        canvas.drawText("Email: support@carefluxpharmacy.com", mx, fY + 70f, paintDarkText)

        // Right - Features
        val rx = footerRect.right - 350f
        canvas.drawText("✓ Genuine Medicines", rx, fY, paintDarkText)
        canvas.drawText("✓ Pharmacist Verified", rx, fY + 35f, paintDarkText)
        canvas.drawText("✓ Secure Payments", rx, fY + 70f, paintDarkText)
        canvas.drawText("✓ Fast Delivery", rx, fY + 105f, paintDarkText)

        // End message
        yPos += 240f
        paintDarkBlueText.textSize = 28f
        canvas.drawText("Thank you for choosing Careflux Pharmacy.", margin, yPos, paintDarkBlueText)
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
}
