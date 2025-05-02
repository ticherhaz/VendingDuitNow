package net.ticherhaz.vending_duitnow

import android.app.Activity
import android.app.Dialog
import android.content.pm.PackageManager
import android.os.CountDownTimer
import android.util.Log
import android.view.View
import android.view.Window
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    private val productIds: String,
    private var congifModel: CongifModel?,
    private val title: String,
    private val description: String,
    private val total: String,
    private val titleTransactionCancel: String,
    private val descriptionTransactionCancel: String,
    private val titleTransactionFailed: String,
    private val descriptionTransactionFailed: String,
    private val callback: DuitNowCallback
) {
    private val weakActivity = WeakReference(activity)
    private val exceptionHandler = CoroutineExceptionHandler { _, exception ->
        activity.runOnUiThread {
            showErrorDialog("Coroutine Error", exception.localizedMessage ?: "Unknown error")
        }
    }
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob() + exceptionHandler)
    private var requestQueue: RequestQueue? = null
    private var customDialog: Dialog? = null
    private var countdownTimer: CountDownTimer? = null
    private val merchantCode get() = congifModel?.merchantcode ?: "M22515"
    private val merchantKey get() = congifModel?.merchantkey ?: "3ENiVsq71P"
    private val fid get() = congifModel?.fid ?: ""
    private val mid get() = congifModel?.mid ?: ""

    private var paymentAlreadyMadeAndSuccess = false
    private var paymentCheckJob: Job? = null

    companion object {
        private const val TAG = "DuitNow"
        private const val BACKEND_URL = "https://vendingapi.azurewebsites.net/api/ipay88/backend"
        private const val SOAP_URL =
            "https://payment.ipay88.com.my/ePayment/WebService/MHGatewayService/GatewayService.svc"
        private const val SOAP_ACTION =
            "https://www.mobile88.com/IGatewayService/EntryPageFunctionality"
        private const val NAMESPACE = "https://www.mobile88.com"
        private const val METHOD_NAME = "EntryPageFunctionality"
        private const val X_FUNCTION_KEY =
            "9TfFiAB2OB9MaCp2DtkrlvoigxITDupIgm-JYXYUu9e4AzFuCv3K9g== "
        private const val COUNTDOWN_TIME = 90 * 1000L // 90 seconds in milliseconds
        private const val PAYMENT_CHECK_INTERVAL = 2000L // 2 seconds
        private const val MAX_PAYMENT_CHECKS = 45 // 45 * 2 seconds = 90 seconds
    }

    init {
        initShowDialog()
        scope.launch { callRegisterPayment() }
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
                findViewById<ImageView>(R.id.iv_qr_code).visibility = View.GONE

                findViewById<TextView>(R.id.tv_title).text = title
                findViewById<TextView>(R.id.tv_description).text = description

                if (!isShowing) {
                    show()
                }
            }
        }
    }

    private suspend fun callRegisterPayment() {
        try {
            val traceNo = UUID.randomUUID().toString().uppercase()
            when (val result = makeRegistrationRequest(traceNo)) {
                is Result.Success -> showQrCodeDialog(traceNo)
                is Result.Failure -> handleRegistrationError(result.exception)
            }
        } catch (e: CancellationException) {
            Log.d(TAG, "Registration coroutine cancelled", e)
        } catch (e: Exception) {
            handleRegistrationError(e)
        }
    }

    private suspend fun makeRegistrationRequest(traceNo: String): Result<String> {
        return try {
            val response = withContext(Dispatchers.IO) {
                suspendCoroutine<String> { continuation ->
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
            }
            Result.Success(response)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    private fun showQrCodeDialog(traceNo: String) {
        weakActivity.get()?.runOnUiThread {
            if (scope.isActive && customDialog?.isShowing == true) {
                customDialog?.apply {
                    val priceMessage = total + " : RM ${"%.2f".format(chargingPrice)}"
                    findViewById<TextView>(R.id.tv_price).text = priceMessage

                    findViewById<ImageView>(R.id.iv_cancel).setOnClickListener {
                        handleImageViewCancelPressed()
                    }
                }

                paymentCheckJob = scope.launch(Dispatchers.IO) {
                    try {
                        when (val result = merchantScanDuitNow(traceNo)) {
                            is Result.Success -> handleQrCodeResult(result.value, traceNo)
                            is Result.Failure -> handleQrCodeError(result.exception)
                        }
                    } catch (e: CancellationException) {
                        Log.d(TAG, "QR code generation cancelled", e)
                    } catch (e: Exception) {
                        handleQrCodeError(e)
                    }
                }
            }
        }
    }

    private fun handleQrCodeError(exception: Exception) {
        weakActivity.get()?.runOnUiThread {
            if (scope.isActive && customDialog?.isShowing == true) {
                val title = "QR Failed (Merchant Code: $merchantCode)"
                val message = "Error: " + exception.localizedMessage
                showSweetAlertDialog(title, message)
                callback.onLoggingEverything("ERROR: handleQrCodeError: " + exception.localizedMessage)
            }
        }
    }

    private fun handleRegistrationError(exception: Exception) {
        weakActivity.get()?.runOnUiThread {
            if (scope.isActive && customDialog?.isShowing == true) {
                val title = "API Failed (Merchant Code: $merchantCode)"
                val message = "Error: " + exception.localizedMessage
                showSweetAlertDialog(title, message)
                callback.onLoggingEverything("ERROR: handleRegistrationError: " + exception.localizedMessage)
            }
        }
    }

    private fun showSweetAlertDialog(title: String, message: String) {
        weakActivity.get()?.runOnUiThread {
            try {
                val sweetAlertDialog = SweetAlertDialog(activity, SweetAlertDialog.WARNING_TYPE)
                sweetAlertDialog.apply {
                    setTitleText(title)
                    setContentText(message)
                    setConfirmButton("Exit") { theDialog ->
                        theDialog?.dismissWithAnimation()
                        dismissDialogDuitNow()
                        callback.enableAllUiAtTypeProductActivity()
                    }
                    if (!isShowing) {
                        show()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error showing alert dialog", e)
            }
        }
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
            callback.onLoggingEverything("ERROR: getPropertySafe: " + e.localizedMessage)
            ""
        }
    }

    private fun handleQrCodeResult(result: JSONObject, traceNo: String) {
        weakActivity.get()?.runOnUiThread {
            if (scope.isActive && customDialog?.isShowing == true) {
                try {
                    if (result.getString("Status") == "1") {
                        Picasso.get().load(result.getString("QRCode"))
                            .resize(300, 300)
                            .into(customDialog?.findViewById(R.id.iv_qr_code))
                        showQrCode()

                        startCountdown()
                        startPaymentStatusCheck(traceNo)
                    } else {
                        handleQrCodeError(Exception(result.optString("ErrDesc")))
                    }
                } catch (e: Exception) {
                    handleQrCodeError(e)
                }
            }
        }
    }

    private fun startCountdown() {
        weakActivity.get()?.runOnUiThread {
            if (scope.isActive && customDialog?.isShowing == true) {
                customDialog?.findViewById<TextView>(R.id.tv_countdown)?.visibility = View.VISIBLE
                countdownTimer = object : CountDownTimer(COUNTDOWN_TIME, 1000) {
                    override fun onTick(millisUntilFinished: Long) {
                        val secondsLeft = millisUntilFinished / 1000
                        val countDownMessage = "Processing in ($secondsLeft sec)"
                        customDialog?.findViewById<TextView>(R.id.tv_countdown)?.text =
                            countDownMessage
                    }

                    override fun onFinish() {
                        if (!paymentAlreadyMadeAndSuccess) {
                            logTempTransaction(0, "Transaction failed, exceed 90 seconds")
                            showTransactionFailedDialog()
                        }
                    }
                }.start()
            }
        }
    }

    private fun showTransactionFailedDialog() {
        weakActivity.get()?.runOnUiThread {
            if (scope.isActive) {
                try {
                    val sweetAlertDialog = SweetAlertDialog(activity, SweetAlertDialog.WARNING_TYPE)
                    sweetAlertDialog.apply {
                        setTitleText(titleTransactionFailed)
                        setContentText(descriptionTransactionFailed)
                        setCancelable(false)
                        setConfirmButton("Exit") { theDialog ->
                            theDialog?.dismissWithAnimation()
                            dismissDialogDuitNow()
                            callback.enableAllUiAtTypeProductActivity()
                        }
                        if (!isShowing) {
                            show()
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error showing transaction failed dialog", e)
                }
            }
        }
    }

    private fun handleImageViewCancelPressed() {
        weakActivity.get()?.let { activity ->
            if (scope.isActive) {
                try {
                    val sweetAlertDialog = SweetAlertDialog(activity, SweetAlertDialog.WARNING_TYPE)
                    sweetAlertDialog.apply {
                        setTitleText(titleTransactionCancel)
                        setContentText(descriptionTransactionCancel)
                        setCancelable(false)
                        setConfirmButton("Yes") { theDialog ->
                            theDialog?.dismissWithAnimation()
                            dismissDialogDuitNow()
                            logTempTransaction(0, "Customer cancel the transaction")
                            callback.enableAllUiAtTypeProductActivity()
                        }
                        setCancelButton("No") { theDialog ->
                            theDialog?.dismissWithAnimation()
                        }

                        if (!isShowing) {
                            show()
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error showing cancel dialog", e)
                }
            }
        }
    }

    private fun startPaymentStatusCheck(traceNo: String) {
        paymentCheckJob?.cancel() // Cancel any existing payment check
        paymentCheckJob = scope.launch(Dispatchers.IO) {
            try {
                repeat(MAX_PAYMENT_CHECKS) { attempt ->
                    if (!isActive) return@launch // Check if coroutine is still active

                    delay(PAYMENT_CHECK_INTERVAL)

                    when (checkTransactionStatus(traceNo)) {
                        "1" -> {
                            handlePaymentSuccess(traceNo)
                            return@launch
                        }

                        else -> {
                            if (attempt == MAX_PAYMENT_CHECKS - 1) {
                                // Last attempt
                                when (checkTransactionStatus(traceNo)) {
                                    "1" -> handlePaymentSuccess(traceNo)
                                    else -> {
                                        logTempTransaction(
                                            0,
                                            "Transaction failed, exceed 90 seconds"
                                        )
                                        showTransactionFailedDialog()
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: CancellationException) {
                Log.d(TAG, "Payment status check cancelled", e)
            } catch (e: Exception) {
                handleQrCodeError(e)
            }
        }
    }

    private suspend fun checkTransactionStatus(traceNo: String): String? {
        return try {
            val response = withContext(Dispatchers.IO) {
                suspendCoroutine<String> { continuation ->
                    val url = "https://vendingapi.azurewebsites.net/api/ipay88/$traceNo/status"
                    val request = object : StringRequest(
                        Method.GET, url,
                        { response -> continuation.resume(response) },
                        { error -> continuation.resumeWithException(error) }
                    ) {
                        override fun getHeaders() = mapOf(
                            "x-functions-key" to X_FUNCTION_KEY
                        )
                    }
                    Volley.newRequestQueue(weakActivity.get()).add(request)
                }
            }
            Log.d(TAG, "transaction inquiry response 1-$traceNo")
            Log.d(TAG, "transaction inquiry response 2-${JSONObject(response).optString("status")}")
            callback.onLoggingEverything("transaction inquiry response 1-$traceNo")
            callback.onLoggingEverything(
                "transaction inquiry response 2-${
                    JSONObject(response).optString(
                        "status"
                    )
                }"
            )
            JSONObject(response).optString("status")
        } catch (e: Exception) {
            Log.e(TAG, "checkTransactionStatus: ", e)
            callback.onLoggingEverything("ERROR: checkTransactionStatus: ${e.localizedMessage}")
            null
        }
    }

    private fun handlePaymentSuccess(traceNo: String) {
        paymentAlreadyMadeAndSuccess = true
        weakActivity.get()?.runOnUiThread {
            if (scope.isActive) {
                customDialog?.dismiss()
                updateUserTransaction(traceNo)
                triggerDispense(traceNo)
                logTempTransaction(1, traceNo)
            }
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
                callback.onLoggingEverything("ERROR: updateUserTransaction: ${e.localizedMessage}")
            }
        }
    }

    private fun triggerDispense(transactionId: String) {
        weakActivity.get()?.let { activity ->
            callback.onPrepareStartDispensePopup(transactionId)
        }
    }

    private fun logTempTransaction(status: Int, refCode: String) {
        scope.launch(Dispatchers.IO) {
            try {
                val transaction = TempTrans().apply {
                    amount = chargingPrice
                    transDate = Calendar.getInstance().time
                    userID = userObj.getUserid()
                    franID = fid
                    machineID = mid
                    productIDs = productIds
                    paymentType = userObj.mtd
                    paymentMethod = userObj.getIpaytype()
                    paymentStatus = status
                    freePoints = ""
                    promocode = userObj.getPromname()
                    promoAmt = userObj.getPromoamt().toString()
                    vouchers = ""
                    paymentStatusDes = refCode
                }

                val response = withContext(Dispatchers.IO) {
                    suspendCoroutine { continuation ->
                        val request = JsonObjectRequest(
                            Request.Method.POST,
                            "https://vendingappapi.azurewebsites.net/Api/TempTrans",
                            JSONObject(Gson().toJson(transaction)),
                            { response -> continuation.resume(response.toString()) },
                            { error ->
                                continuation.resumeWithException(error)
                                callback.onFailedLogTempTransaction("Failed Continuation TempTran: ${error.localizedMessage}")
                                callback.onLoggingEverything("Failed Continuation TempTran: ${error.localizedMessage}")
                            }
                        )
                        requestQueue?.add(request) ?: run {
                            Volley.newRequestQueue(weakActivity.get()).add(request)
                        }
                    }
                }
                Log.d(TAG, "Transaction logged: $response")
                callback.onLoggingEverything("Transaction logged: $response")
            } catch (e: CancellationException) {
                Log.d(TAG, "Temp transaction logging cancelled", e)
            } catch (e: Exception) {
                callback.onLoggingEverything("ERROR: Failed to log transaction: ${e.localizedMessage}")
                Log.e(TAG, "Failed to log transaction", e)
                callback.onFailedLogTempTransaction("Failed TempTran: ${e.localizedMessage}")
            }
        }
    }

    fun dismissDialogDuitNow() {
        weakActivity.get()?.runOnUiThread {
            countdownTimer?.cancel()
            countdownTimer = null
            paymentCheckJob?.cancel()
            paymentCheckJob = null
            customDialog?.dismiss()
            customDialog = null
            scope.coroutineContext.cancelChildren()
        }
    }

    private fun showQrCode() {
        weakActivity.get()?.runOnUiThread {
            if (scope.isActive && customDialog?.isShowing == true) {
                customDialog?.apply {
                    findViewById<ProgressBar>(R.id.progress_bar).visibility = View.GONE
                    findViewById<ImageView>(R.id.iv_qr_code).visibility = View.VISIBLE
                }
            }
        }
    }

    private fun currencyFormat(amount: Double): String {
        return DecimalFormat("###,###,##0.00").format(amount)
    }

    private fun securityHmacSha512(toEncrypt: String, key: String): String {
        val keyBytes = key.toByteArray(Charsets.UTF_8)
        val hmacSHA512 = Mac.getInstance("HmacSHA512")
        val secretKeySpec = SecretKeySpec(keyBytes, "HmacSHA512")
        hmacSHA512.init(secretKeySpec)
        val hashBytes = hmacSHA512.doFinal(toEncrypt.toByteArray(Charsets.UTF_8))
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

    private fun showErrorDialog(title: String, message: String) {
        weakActivity.get()?.runOnUiThread {
            try {
                val sweetAlertDialog = SweetAlertDialog(activity, SweetAlertDialog.ERROR_TYPE)
                sweetAlertDialog.apply {
                    setTitleText(title)
                    setContentText(message)
                    setConfirmButton("OK") { dialog -> dialog?.dismissWithAnimation() }
                    if (!isShowing) {
                        show()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error showing error dialog", e)
            }
        }
    }

    sealed class Result<out T> {
        data class Success<out T>(val value: T) : Result<T>()
        data class Failure(val exception: Exception) : Result<Nothing>()
    }

    interface DuitNowCallback {
        fun onPrepareStartDispensePopup(transactionId: String)
        fun enableAllUiAtTypeProductActivity()
        fun onFailedLogTempTransaction(message: String)
        fun onLoggingEverything(message: String)
    }
}