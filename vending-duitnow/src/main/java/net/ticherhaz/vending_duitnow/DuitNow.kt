package net.ticherhaz.vending_duitnow

import android.app.Activity
import android.app.Dialog
import android.content.pm.PackageManager
import android.util.Log
import android.view.View
import android.view.Window
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import cn.pedant.SweetAlert.SweetAlertDialog
import com.android.volley.DefaultRetryPolicy
import com.android.volley.Request
import com.android.volley.RequestQueue
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.google.gson.Gson
import com.squareup.picasso.Picasso
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.ticherhaz.vending_duitnow.model.CartListModel
import net.ticherhaz.vending_duitnow.model.CongifModel
import net.ticherhaz.vending_duitnow.model.DuitnowModel
import net.ticherhaz.vending_duitnow.model.TempTrans
import net.ticherhaz.vending_duitnow.model.UserObj
import org.json.JSONObject
import org.ksoap2.SoapEnvelope
import org.ksoap2.serialization.PropertyInfo
import org.ksoap2.serialization.SoapObject
import org.ksoap2.serialization.SoapSerializationEnvelope
import org.ksoap2.transport.HttpTransportSE
import java.lang.ref.WeakReference
import java.text.DecimalFormat
import java.util.Calendar
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class DuitNow(
    private val activity: Activity,
    private val chargingPrice: Double,
    private val userObj: UserObj,
    private val cartListModels: List<CartListModel>,
    private val callback: DuitNowCallback
) {

    /**
     * new DuitNow(
     *         TypeProfuctActivity.this,
     *         chargingprice,
     *         obj,
     *         cartListModels,
     *         new DuitNow.DuitNowCallback() {
     *             @Override
     *             public void enableAllUiAtTypeProductActivity() {
     *                 paymentInProgress = false;
     *                 getpaybuttonenable().setEnabled(true);
     *                 clearCustomDialogDispense();
     *                 setEnableaddproduct(true);
     *             }
     *             @Override
     *             public void onPrepareStartDispensePopup() {
     *                 DispensePopUpM5 dispensePopUpM5 = new DispensePopUpM5();
     *                 dispensePopUpM5.DispensePopUp(TypeProfuctActivity.this, obj, "success", "", requestQueue);
     *             }
     *         });
     */

    private val weakActivity = WeakReference(activity)
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    private var requestQueue: RequestQueue? = null
    private var customDialog: Dialog? = null
    private var configData: configdata? = null
    private var congifModel: CongifModel? = null
    private var productsIds = ""

    private val merchantCode get() = congifModel?.merchantcode ?: "M22515"
    private val merchantKey get() = congifModel?.merchantkey ?: "3ENiVsq71P"
    private val fid get() = congifModel?.fid ?: ""
    private val mid get() = congifModel?.mid ?: ""

    companion object {
        private const val TAG = "DuitNowKotlin"

        private const val BACKEND_URL = "https://vendingapi.azurewebsites.net/api/ipay88/backend"
        private const val SOAP_URL =
            "https://payment.ipay88.com.my/ePayment/WebService/MHGatewayService/GatewayService.svc"
        private const val SOAP_ACTION =
            "https://www.mobile88.com/IGatewayService/EntryPageFunctionality"
        private const val NAMESPACE = "https://www.mobile88.com"
        private const val METHOD_NAME = "EntryPageFunctionality"
        private const val X_FUNCTION_KEY =
            "9TfFiAB2OB9MaCp2DtkrlvoigxITDupIgm-JYXYUu9e4AzFuCv3K9g== "
    }

    init {
        initializeProducts()
        setupConfig()
        initShowDialog()
        scope.launch { callRegisterPayment() }
    }

    private fun initializeProducts() {
        productsIds = cartListModels.flatMap { model ->
            List(model.getProdid().toInt()) { model.getProdid() }
        }.joinToString(",")
    }

    private fun setupConfig() {
        configData = weakActivity.get()?.let { configdata(it) }
        configData?.getAllItems()?.firstOrNull()?.let {
            congifModel = it
        }
    }

    private fun initShowDialog() {
        weakActivity.get()?.let { activity ->
            customDialog = Dialog(activity).apply {
                requestWindowFeature(Window.FEATURE_NO_TITLE)
                setContentView(R.layout.dialog_duitnow)
                window?.setBackgroundDrawableResource(android.R.color.transparent)
                setCancelable(false)
                setCanceledOnTouchOutside(false)

                findViewById<ProgressBar>(R.id.progress_bar).visibility = View.VISIBLE
                findViewById<ImageView>(R.id.qr_code).visibility = View.GONE
                show()
            }
        }
    }

    private suspend fun callRegisterPayment() {
        val traceNo = UUID.randomUUID().toString().uppercase()
        when (val result = makeRegistrationRequest(traceNo)) {
            is Result.Success -> showQrCodeDialog(traceNo)
            is Result.Failure -> handleRegistrationError(result.exception)
        }
    }

    private suspend fun makeRegistrationRequest(traceNo: String): Result<String> {
        return try {
            val response = suspendCoroutine<String> { continuation ->
                val url = "https://vendingapi.azurewebsites.net/api/ipay88/register"
                val request = object : StringRequest(
                    Method.POST, url,
                    { response -> continuation.resume(response) },
                    { error -> continuation.resumeWithException(error) }
                ) {
                    override fun getBodyContentType() = "application/json; charset=utf-8"
                    override fun getBody() =
                        Gson().toJson(DuitnowModel(traceNo)).toByteArray(Charsets.UTF_8)

                    override fun getHeaders() = mapOf(
                        "x-functions-key" to X_FUNCTION_KEY
                    )
                }.apply {
                    retryPolicy =
                        DefaultRetryPolicy(20000, 0, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT)
                }
                Volley.newRequestQueue(weakActivity.get()).add(request)
            }
            Result.Success(response)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    private fun showQrCodeDialog(traceNo: String) {
        weakActivity.get()?.runOnUiThread {
            customDialog?.apply {
                findViewById<TextView>(R.id.pricetext).text =
                    "TOTAL : RM ${"%.2f".format(chargingPrice)}"

                findViewById<Button>(R.id.backbtn).setOnClickListener {
                    dismissDialog()
                    enableAllUiAtTypeProductActivity()
                    callback.enableAllUiAtTypeProductActivity()
                }
            }
        }

        scope.launch(Dispatchers.IO) {
            when (val result = merchantScanDuitNow(traceNo)) {
                is Result.Success -> handleQrCodeResult(result.value, traceNo)
                is Result.Failure -> handleQrCodeError(result.exception)
            }
        }
    }

    private fun handleQrCodeError(exception: Exception) {
        val title = "QR Failed (Merchant Code: $merchantCode)"
        val message = "Error: " + exception.message
        showSweetAlertDialog(title, message)
    }

    private fun handleRegistrationError(exception: Exception) {
        val title = "API Failed (Merchant Code: $merchantCode)"
        val message = "Error: " + exception.message
        showSweetAlertDialog(title, message)
    }

    private fun showSweetAlertDialog(title: String, message: String) {
        activity.runOnUiThread {
            val sweetAlertDialog = SweetAlertDialog(activity, SweetAlertDialog.WARNING_TYPE)

            sweetAlertDialog.setTitleText(title)
            sweetAlertDialog.setContentText(message)
            sweetAlertDialog.setConfirmButton("Exit") { theDialog -> theDialog?.dismissWithAnimation() }
            sweetAlertDialog.show()

            dismissDialog()

            //enableAllUiAtTypeProductActivity()
            callback.enableAllUiAtTypeProductActivity()
        }
    }

    private fun enableAllUiAtTypeProductActivity() {
        /*(activity as TypeProfuctActivity).paymentInProgress = false
        activity.getpaybuttonenable().isEnabled = true
        activity.clearCustomDialogDispense()
        activity.setEnableaddproduct(true)*/
    }

    private suspend fun merchantScanDuitNow(traceNo: String): Result<JSONObject> {
        return try {
            val amountFormatted = "%.2f".format(chargingPrice).replace(".", "")
            val signatureData = listOf(merchantKey, merchantCode, traceNo, amountFormatted, "MYR")
                .joinToString("")

            val signature = securityHmacSha512(signatureData, merchantKey)

            val params = listOf(
                currencyFormat(chargingPrice),
                "", // BarcodeNo
                traceNo,
                signature,
                "0"
            )

            val soapResponse = callWebServiceDuitNow(params)

            Result.Success(handleSoapResponse(soapResponse))
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    private suspend fun callWebServiceDuitNow(params: List<String>): SoapObject {
        return withContext(Dispatchers.IO) {
            val soapRequest = createSoapRequest(params)

            val envelope = SoapSerializationEnvelope(SoapEnvelope.VER11).apply {
                implicitTypes = true
                dotNet = true
                setOutputSoapObject(soapRequest)
            }

            HttpTransportSE(SOAP_URL, 60000).call(SOAP_ACTION, envelope)

            envelope.response as SoapObject
        }
    }

    private fun createSoapRequest(params: List<String>): SoapObject {
        val requestModel = createRequestModel(params)
        val soapObject = SoapObject(NAMESPACE, METHOD_NAME)
        soapObject.addSoapObject(requestModel)
        return soapObject
    }

    private fun createRequestModel(params: List<String>): SoapObject {
        val namespaceSchemas = "http://schemas.datacontract.org/2004/07/MHPHGatewayService.Model"
        val soapObject = SoapObject(NAMESPACE, "requestModelObj")

        // Helper function to add properties with namespace
        fun addPropertyWithNamespace(name: String, value: Any?, namespace: String) {
            val propertyInfo = PropertyInfo()
            propertyInfo.name = name
            propertyInfo.value = value
            propertyInfo.namespace = namespace
            soapObject.addProperty(propertyInfo)
        }

        addPropertyWithNamespace("Amount", params[0], namespaceSchemas)
        addPropertyWithNamespace("BackendURL", BACKEND_URL, namespaceSchemas)
        addPropertyWithNamespace("BarcodeNo", params[1], namespaceSchemas)
        addPropertyWithNamespace("Currency", "MYR", namespaceSchemas)
        addPropertyWithNamespace("MerchantCode", merchantCode, namespaceSchemas)
        addPropertyWithNamespace("PaymentId", 888, namespaceSchemas)
        addPropertyWithNamespace("ProdDesc", "RSKioskv2", namespaceSchemas)
        addPropertyWithNamespace("RefNo", params[2], namespaceSchemas)
        addPropertyWithNamespace("Remark", mid, namespaceSchemas)
        addPropertyWithNamespace("Signature", params[3], namespaceSchemas)
        addPropertyWithNamespace("SignatureType", "HMACSHA512", namespaceSchemas)
        addPropertyWithNamespace("TerminalID", "", namespaceSchemas)
        addPropertyWithNamespace("UserContact", "0193336711", namespaceSchemas)
        addPropertyWithNamespace("UserEmail", "rs@ratnar.com", namespaceSchemas)
        addPropertyWithNamespace("UserName", "Ratnar", namespaceSchemas)
        addPropertyWithNamespace("lang", "ISO-8859-1", namespaceSchemas)
        addPropertyWithNamespace("xField1", "", namespaceSchemas)
        addPropertyWithNamespace("xField2", "", namespaceSchemas)

        return soapObject
    }

    private fun handleSoapResponse(response: SoapObject): JSONObject {
        return JSONObject().apply {
            val status = response.getProperty("Status").toString()
            put("Status", status)

            if (status == "1") {
                put("AuthCode", response.getPropertySafe("AuthCode"))
                put("TransId", response.getPropertySafe("TransId"))
                put("QRCode", response.getPropertySafe("QRCode"))
                put("QRValue", response.getPropertySafe("QRValue"))
            } else {
                put("ErrDesc", response.getPropertySafe("ErrDesc"))
            }
        }
    }

    private fun SoapObject.getPropertySafe(name: String): String {
        return try {
            getProperty(name).toString()
        } catch (e: Exception) {
            ""
        }
    }

    private fun handleQrCodeResult(result: JSONObject, traceNo: String) {
        weakActivity.get()?.runOnUiThread {
            try {
                if (result.getString("Status") == "1") {
                    Picasso.get().load(result.getString("QRCode"))
                        .resize(300, 300)
                        .into(customDialog?.findViewById(R.id.qr_code))
                    showQrCode()

//                    startTransactionInquiry(
//                        referenceNo = traceNo,
//                        amount = "%.2f".format(chargingPrice),
//                        merchantCode = merchantCode
//                    )

                    startPaymentStatusCheck(traceNo)
                } else {
                    handleQrCodeError(Exception(result.optString("ErrDesc")))
                }
            } catch (e: Exception) {
                handleQrCodeError(e)
            }
        }
    }

    private fun startPaymentStatusCheck(traceNo: String) {
        scope.launch(Dispatchers.IO) {
            repeat(80) { attempt ->
                delay(1000)
                when (checkTransactionStatus(traceNo)) {
                    "1" -> {
                        handlePaymentSuccess(traceNo)
                        cancel()
                    }

                    else -> if (attempt == 79) finalCheck(traceNo)
                }
            }
        }
    }

    private suspend fun checkTransactionStatus(traceNo: String): String? {
        return try {
            val messageHere = "9TfFiAB2OB9MaCp2DtkrlvoigxITDupIgm-JYXYUu9e4AzFuCv3K9g== "
            val response = suspendCoroutine<String> { continuation ->
                val url = "https://vendingapi.azurewebsites.net/api/ipay88/$traceNo/status"
                val request = object : StringRequest(
                    Method.GET, url,
                    { response -> continuation.resume(response) },
                    { error -> continuation.resumeWithException(error) }
                ) {
                    override fun getHeaders() = mapOf(
                        "x-functions-key" to messageHere
                    )
                }
                Volley.newRequestQueue(weakActivity.get()).add(request)
            }
            Log.d("DuitNow", "transaction inquiry response 1-" + traceNo)
            Log.d(
                "DuitNow",
                "transaction inquiry response 2-" + JSONObject(response).optString("status")
            )
            JSONObject(response).optString("status")

        } catch (e: Exception) {
            null
        }
    }

    private fun handlePaymentSuccess(traceNo: String) {
        weakActivity.get()?.runOnUiThread {
            customDialog?.dismiss()
            updateUserTransaction(traceNo)
            triggerDispense()
            logTempTransaction(1, traceNo)
        }
    }

    private fun updateUserTransaction(transId: String) {
        weakActivity.get()?.let { activity ->
            try {
                val versionName = activity.packageManager
                    .getPackageInfo(activity.packageName, 0).versionName
                userObj.mtd = "${userObj.mtd} ($transId) $versionName"
            } catch (e: PackageManager.NameNotFoundException) {
                Log.e(TAG, "Version name not found", e)
            }
        }
    }

    private fun triggerDispense() {
        weakActivity.get()?.let { activity ->

            // Finalize the dispense process
            callback.onPrepareStartDispensePopup()

            /*val dispensePopUpM5 = DispensePopUpM5()
            dispensePopUpM5.DispensePopUp(
                activity,
                userObj,
                "success",
                "",
                requestQueue ?: Volley.newRequestQueue(activity)
            )*/
        }
    }

    private fun logTempTransaction(status: Int, refCode: String) {
        scope.launch(Dispatchers.IO) {
            try {
                val transaction = TempTrans()
                transaction.amount = chargingPrice
                transaction.transDate = Calendar.getInstance().time
                transaction.userID = userObj.getUserid()
                transaction.franID = fid
                transaction.machineID = mid
                transaction.productIDs = productsIds
                transaction.paymentType = userObj.mtd
                transaction.paymentMethod = userObj.getIpaytype()
                transaction.paymentStatus = status
                transaction.freePoints = ""
                transaction.promocode = userObj.getPromname()
                transaction.promoAmt = userObj.getPromoamt().toString()
                transaction.vouchers = ""
                transaction.paymentStatusDes = refCode


                val response = suspendCoroutine { continuation ->
                    val request = JsonObjectRequest(
                        Request.Method.POST,
                        "https://vendingappapi.azurewebsites.net/Api/TempTrans",
                        JSONObject(Gson().toJson(transaction)),
                        { response -> continuation.resume(response.toString()) },
                        { error -> continuation.resumeWithException(error) }
                    )
                    requestQueue?.add(request) ?: run {
                        Volley.newRequestQueue(weakActivity.get()).add(request)
                    }
                }
                Log.d(TAG, "Transaction logged: $response")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to log transaction", e)
            }
        }
    }

    private fun finalCheck(traceNo: String) {
        scope.launch {
            checkTransactionStatus(traceNo)
        }
    }

    private fun dismissDialog() {
        customDialog?.dismiss()
        customDialog = null
        scope.coroutineContext.cancelChildren()
    }

    private fun showQrCode() {
        customDialog?.apply {
            findViewById<ProgressBar>(R.id.progress_bar).visibility = View.GONE
            findViewById<ImageView>(R.id.qr_code).visibility = View.VISIBLE
        }
    }

    private fun currencyFormat(amount: Double): String {
        return DecimalFormat("###,###,##0.00").format(amount)
    }

    var retryCount = 0

    /*private fun startTransactionInquiry(referenceNo: String, amount: String, merchantCode: String) {
        scope.launch(Dispatchers.IO) {
            while (retryCount < 6) {
                try {
                    val result = executeTransactionInquiry(referenceNo, amount, merchantCode)
                    when (result.status) {
                        "1" -> handleTransactionSuccess(result.transId)
                    }
                } catch (e: Exception) {
                    handleTransactionError(e, retryCount, referenceNo, amount, merchantCode)
                }

                retryCount++
                delay(10000)
                if (retryCount < 6) {
                    startTransactionInquiry(referenceNo, amount, merchantCode)
                }
            }
        }
    }*/

    private suspend fun executeTransactionInquiry(
        referenceNo: String,
        amount: String,
        merchantCode: String
    ): TransactionResult {
        return withContext(Dispatchers.IO) {
            val envelope = SoapSerializationEnvelope(SoapEnvelope.VER11).apply {
                setOutputSoapObject(createInquirySoapRequest(referenceNo, amount, merchantCode))
                dotNet = true
            }

            val transport = HttpTransportSE(
                "https://payment.ipay88.com.my/ePayment/Webservice/TxInquiryCardDetails/TxDetailsInquiry.asmx",
                60000
            )

            transport.call(
                "https://www.mobile88.com/epayment/webservice/TxDetailsInquiryCardInfo",
                envelope
            )

            val response = envelope.response.toString()
            Log.d(TAG, "Transaction inquiry response: $response")

            val result = parseXmlTag(response, "TxDetailsInquiryCardInfoResult")
            val status = parseXmlTag(result, "Status")
            val transId = parseXmlTag(result, "TransId")

            TransactionResult(status, transId)
        }
    }

    private fun createInquirySoapRequest(
        referenceNo: String,
        amount: String,
        merchantCode: String
    ): SoapObject {
        return SoapObject(
            "https://www.mobile88.com/epayment/webservice",
            "TxDetailsInquiryCardInfo"
        ).apply {
            addProperty("MerchantCode", merchantCode)
            addProperty("ReferenceNo", referenceNo)
            addProperty("Amount", amount)
            addProperty("Version", "1.0")
        }
    }

    private fun parseXmlTag(xml: String, tag: String): String {
        val start = xml.indexOf("<$tag>") + tag.length + 2
        val end = xml.indexOf("</$tag>")
        return if (start >= 0 && end > start) xml.substring(start, end) else ""
    }

    private fun handleTransactionSuccess(transId: String) {
        retryCount == 6
        scope.launch(Dispatchers.Main) {
            weakActivity.get()?.let { activity ->
                try {
                    // Dismiss dialogs
                    customDialog?.takeIf { it.isShowing }?.dismiss()

                    // Update user object
                    val versionName = activity.packageManager
                        .getPackageInfo(activity.packageName, 0).versionName
                    userObj.mtd = "${userObj.mtd} ($transId) $versionName"

                    // Show success UI
                    /*DispensePopUpM5().apply {
                        DispensePopUp(
                            activity,
                            userObj,
                            "success",
                            "",
                            requestQueue ?: Volley.newRequestQueue(activity)
                        )
                    }*/

                    // Log transaction
                    logTempTransaction(1, transId)

                } catch (e: PackageManager.NameNotFoundException) {
                    Log.e(TAG, "Version name not found", e)
                }
            }
        }
    }

    private fun handleTransactionFailure(
        retryCount: Int,
        referenceNo: String,
        amount: String,
        merchantCode: String
    ) {
        if (retryCount == 5) {
            scope.launch {
                checkTransactionStatus(referenceNo)
            }
        }
    }

    private fun handleTransactionError(
        e: Exception,
        retryCount: Int,
        referenceNo: String,
        amount: String,
        merchantCode: String
    ) {
        Log.e(TAG, "Transaction inquiry failed", e)
        if (retryCount == 5) {
            scope.launch {
                checkTransactionStatus(referenceNo)
            }
        }
    }

    private fun securityHmacSha512(toEncrypt: String, key: String): String {
        // Convert key to byte array
        val keyBytes = key.toByteArray(charset("UTF-8"))

        // Create an instance of HMACSHA512 with the key
        val hmacSHA512 = Mac.getInstance("HmacSHA512")
        val secretKeySpec = SecretKeySpec(keyBytes, "HmacSHA512")
        hmacSHA512.init(secretKeySpec)

        // Compute the hash
        val hashBytes = hmacSHA512.doFinal(toEncrypt.toByteArray(charset("UTF-8")))

        // Convert the byte array to a hexadecimal string
        return byteArrayToHex(hashBytes)
    }

    private fun byteArrayToHex(bytes: ByteArray): String {
        val hexString = StringBuilder()
        for (b in bytes) {
            val hex = Integer.toHexString(0xff and b.toInt())
            if (hex.length == 1) hexString.append('0')
            hexString.append(hex)
        }
        return hexString.toString()
    }

    private data class TransactionResult(val status: String, val transId: String)

    sealed class Result<out T> {
        data class Success<out T>(val value: T) : Result<T>()
        data class Failure(val exception: Exception) : Result<Nothing>()
    }

    interface DuitNowCallback {
        fun onPrepareStartDispensePopup()
        fun enableAllUiAtTypeProductActivity()
    }
}