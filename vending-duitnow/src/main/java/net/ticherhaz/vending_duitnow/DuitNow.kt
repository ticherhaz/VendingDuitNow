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

        // For re-query
        const val SOAP_URL_QUERY_PAYMENT_CHECK =
            "https://payment.ipay88.com.my/ePayment/Webservice/TxInquiryCardDetails/TxDetailsInquiry.asmx"
        const val SOAP_ACTION_QUERY_PAYMENT_CHECK_URL =
            "https://www.mobile88.com/epayment/webservice/TxDetailsInquiryCardInfo"
        const val NAMESPACE_QUERY_PAYMENT_CHECK = "https://www.mobile88.com/epayment/webservice"
        const val METHOD_NAME_QUERY_PAYMENT_CHECK_URL = "TxDetailsInquiryCardInfo"

        private const val X_FUNCTION_KEY =
            "9TfFiAB2OB9MaCp2DtkrlvoigxITDupIgm-JYXYUu9e4AzFuCv3K9g== "
        private const val COUNTDOWN_TIME = 120 * 1000L // 120 seconds for countdown
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
                Log.e(TAG, "QR code error", exception)
                callback.onLoggingEverything("ERROR: handleQrCodeError: ${exception.localizedMessage}")
                val title = "QR Failed (Merchant Code: $merchantCode)"
                val message = "Error: ${exception.localizedMessage}"
                showSweetAlertDialog(title, message)
            }
        }
    }

    private fun handleRegistrationError(exception: Exception) {
        weakActivity.get()?.runOnUiThread {
            if (scope.isActive && customDialog?.isShowing == true) {
                Log.e(TAG, "Registration error", exception)
                callback.onLoggingEverything("ERROR: handleRegistrationError: ${exception.localizedMessage}")
                val title = "API Failed (Merchant Code: $merchantCode)"
                val message = "Error: ${exception.localizedMessage}"
                showSweetAlertDialog(title, message)
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
            Log.d(
                TAG,
                "Signature Inputs: merchantKey=$merchantKey, merchantCode=$merchantCode, traceNo=$traceNo, amountFormatted=$amountFormatted"
            )
            callback.onLoggingEverything("Signature Inputs: merchantKey=$merchantKey, merchantCode=$merchantCode, traceNo=$traceNo, amountFormatted=$amountFormatted")
            val signature = securityHmacSha512(signatureData, merchantKey)
            Log.d(TAG, "Generated Signature: $signature")
            callback.onLoggingEverything("Generated Signature: $signature")
            val params = listOf(
                currencyFormat(chargingPrice),
                "", // BarcodeNo
                traceNo,
                signature,
                "0"
            )
            Log.d(TAG, "SOAP Request Params: $params")
            callback.onLoggingEverything("SOAP Request Params: $params")
            val soapResponse = callWebServiceDuitNow(params)
            Log.d(TAG, "SOAP Response: $soapResponse")
            callback.onLoggingEverything("SOAP Response: $soapResponse")
            Result.Success(handleSoapResponse(soapResponse))
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    private suspend fun initDuitNowQueryPaymentChecking(traceNo: String): Result<Unit> {
        Log.d(TAG, "Query payment checking started for traceNo: $traceNo")
        callback.onLoggingEverything("Query payment checking started for traceNo: $traceNo")
        try {
            when (val result = merchantScanDuitNowQueryPaymentChecking(traceNo)) {
                is Result.Success -> {
                    Log.d(TAG, "Query payment checking successful for traceNo: $traceNo")
                    callback.onLoggingEverything("Query payment checking successful for traceNo: $traceNo")
                    handlePaymentSuccess(traceNo)
                    return Result.Success(Unit)
                }

                is Result.Failure -> {
                    Log.e(
                        TAG,
                        "Query payment checking failed for traceNo: $traceNo",
                        result.exception
                    )
                    callback.onLoggingEverything("ERROR: Query payment checking failed: ${result.exception.localizedMessage}")
                    return Result.Failure(result.exception)
                }
            }
        } catch (e: CancellationException) {
            Log.d(TAG, "Query payment checking cancelled", e)
            callback.onLoggingEverything("ERROR: Query payment checking cancelled: ${e.localizedMessage}")
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Query payment checking failed", e)
            callback.onLoggingEverything("ERROR: Query payment checking failed: ${e.localizedMessage}")
            return Result.Failure(e)
        }
    }

    private suspend fun merchantScanDuitNowQueryPaymentChecking(traceNo: String): Result<JSONObject> {
        return try {
            val params = listOf(
                merchantCode,
                traceNo,
                currencyFormat(chargingPrice)
            )
            Log.d(TAG, "Query SOAP Request Params: $params")
            callback.onLoggingEverything("Query SOAP Request Params: $params")
            val soapResponse = callWebServiceDuitNowQueryPaymentChecking(params)
            Log.d(TAG, "Query SOAP Response: $soapResponse")
            callback.onLoggingEverything("Query SOAP Response: $soapResponse")
            Result.Success(handleSoapResponseQueryPaymentChecking(soapResponse))
        } catch (e: Exception) {
            Log.e(TAG, "merchantScanDuitNowQueryPaymentChecking failed", e)
            callback.onLoggingEverything("ERROR: merchantScanDuitNowQueryPaymentChecking: ${e.localizedMessage}")
            Result.Failure(e)
        }
    }

    private suspend fun callWebServiceDuitNowQueryPaymentChecking(params: List<String>): SoapObject {
        return withContext(Dispatchers.IO) {
            val soapRequest = createSoapRequestQueryPaymentChecking(params)
            val envelope = SoapSerializationEnvelope(SoapEnvelope.VER11).apply {
                implicitTypes = true
                dotNet = true // Required for .NET services
                setOutputSoapObject(soapRequest)
            }
            HttpTransportSE(SOAP_URL_QUERY_PAYMENT_CHECK, 60000).apply {
                call(SOAP_ACTION_QUERY_PAYMENT_CHECK_URL, envelope)
            }

            // Handle response
            envelope.response as SoapObject
        }
    }

    private fun createSoapRequestQueryPaymentChecking(params: List<String>): SoapObject {
        val namespaceService =
            NAMESPACE_QUERY_PAYMENT_CHECK // Correct namespace: "https://www.mobile88.com/epayment/webservice"
        val methodName = METHOD_NAME_QUERY_PAYMENT_CHECK_URL // e.g., "TxDetailsInquiryCardInfo"

        val soapObject = SoapObject(namespaceService, methodName)

        // Helper to add properties in the correct namespace
        fun addProperty(name: String, value: Any?) {
            val property = PropertyInfo().apply {
                this.name = name
                this.value = value
                this.namespace = namespaceService // Correct namespace
            }
            soapObject.addProperty(property)
        }

        // Add parameters directly to the method element
        addProperty("MerchantCode", merchantCode)
        addProperty("ReferenceNo", params[1]) // traceNo
        addProperty("Amount", params[2])       // formatted amount
        addProperty("Version", "4")

        return soapObject
    }

    private fun handleSoapResponseQueryPaymentChecking(response: SoapObject): JSONObject {
        return JSONObject().apply {
            val status = response.getPropertySafe("Status")
            put("Status", status)
            if (status == "1") {
                val merchantCode = response.getPropertySafe("MerchantCode")
                val paymentId = response.getPropertySafe("PaymentId")
                val traceNo = response.getPropertySafe("RefNo")
                val authCode = response.getPropertySafe("AuthCode")
                val transId = response.getPropertySafe("TransId")
                put("MerchantCode", merchantCode)
                put("PaymentId", paymentId)
                put("RefNo", traceNo)
                put("AuthCode", authCode)
                put("TransId", transId)
                if (paymentId.isEmpty()) {
                    put("ErrDesc", "PaymentId is missing or empty")
                    Log.e(TAG, "PaymentId is missing in query SOAP response")
                    callback.onLoggingEverything("ERROR: PaymentId is missing in query SOAP response")
                }
            } else {
                put("ErrDesc", response.getPropertySafe("ErrDesc"))
            }
        }
    }

    private fun SoapObject.getPropertySafe(name: String): String {
        return try {
            val property = getProperty(name)
            property?.toString() ?: ""
        } catch (e: Exception) {
            callback.onLoggingEverything("ERROR: getPropertySafe: ${e.localizedMessage}")
            ""
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
            val transport = HttpTransportSE(SOAP_URL, 60000)
            transport.call(SOAP_ACTION, envelope)
            val response = envelope.response as SoapObject
            Log.d(TAG, "SOAP Response: $response")
            callback.onLoggingEverything("SOAP Response: $response")
            response
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
            val status = response.getPropertySafe("Status")
            put("Status", status)
            if (status == "1") {
                val authCode = response.getPropertySafe("AuthCode")
                val transId = response.getPropertySafe("TransId")
                val qrCode = response.getPropertySafe("QRCode")
                val qrValue = response.getPropertySafe("QRValue")
                put("AuthCode", authCode)
                put("TransId", transId)
                put("QRCode", qrCode)
                put("QRValue", qrValue)
                if (qrCode.isEmpty()) {
                    put("ErrDesc", "QRCode is missing or empty")
                    Log.e(TAG, "QRCode is missing in SOAP response")
                    callback.onLoggingEverything("ERROR: QRCode is missing in SOAP response")
                }
            } else {
                put("ErrDesc", response.getPropertySafe("ErrDesc"))
            }
        }
    }

    private fun handleQrCodeResult(result: JSONObject, traceNo: String) {
        weakActivity.get()?.runOnUiThread {
            if (scope.isActive && customDialog?.isShowing == true) {
                try {
                    if (result.getString("Status") == "1" && result.optString("QRCode")
                            .isNotEmpty()
                    ) {
                        Picasso.get().load(result.getString("QRCode"))
                            .resize(300, 300)
                            .into(customDialog?.findViewById(R.id.iv_qr_code))
                        showQrCode()
                        startCountdown()
                        startPaymentStatusCheck(traceNo)
                    } else {
                        val errorMessage = result.optString("ErrDesc", "Invalid or missing QRCode")
                        handleQrCodeError(Exception(errorMessage))
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
                            logTempTransaction(0, "Transaction failed, exceeded 120 seconds")
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
            val startTime = System.currentTimeMillis()
            try {
                Log.d(TAG, "Starting payment status check for traceNo: $traceNo at ${startTime}ms")
                callback.onLoggingEverything("Starting payment status check for traceNo: $traceNo at ${startTime}ms")

                // Step 1: Delay 7 seconds
                delay(7000L)
                val elapsed = (System.currentTimeMillis() - startTime) / 1000
                Log.d(TAG, "Step 1: 7-second delay completed at ${elapsed}s")
                callback.onLoggingEverything("Step 1: 7-second delay completed at ${elapsed}s")

                // Step 2: Check transaction status every 3 seconds for 10 times (7s to 37s)
                repeat(10) { attempt ->
                    if (!isActive) {
                        Log.d(TAG, "Coroutine cancelled during step 2, attempt $attempt")
                        return@launch
                    }
                    Log.d(
                        TAG,
                        "Step 2: Checking transaction status, attempt ${attempt + 1} at ${(System.currentTimeMillis() - startTime) / 1000}s"
                    )
                    val status = checkTransactionStatus(traceNo)
                    if (status == "1") {
                        Log.d(
                            TAG,
                            "Step 2: Transaction successful at ${(System.currentTimeMillis() - startTime) / 1000}s"
                        )
                        handlePaymentSuccess(traceNo)
                        return@launch
                    }
                    delay(3000L)
                }

                // Step 3: Call initDuitNowQueryPaymentChecking once at 40s
                if (!isActive) {
                    Log.d(TAG, "Coroutine cancelled before step 3")
                    return@launch
                }
                Log.d(
                    TAG,
                    "Step 3: Initiating query payment checking at ${(System.currentTimeMillis() - startTime) / 1000}s"
                )
                when (val result = initDuitNowQueryPaymentChecking(traceNo)) {
                    is Result.Success -> return@launch // Success already handled
                    is Result.Failure -> Log.d(
                        TAG,
                        "Step 3: Query failed, continuing: ${result.exception.localizedMessage}"
                    )
                }
                delay(3000L) // Delay to reach ~43s

                // Step 4: Check transaction status every 3 seconds for 10 times (43s to 73s)
                repeat(10) { attempt ->
                    if (!isActive) {
                        Log.d(TAG, "Coroutine cancelled during step 4, attempt $attempt")
                        return@launch
                    }
                    Log.d(
                        TAG,
                        "Step 4: Checking transaction status, attempt ${attempt + 1} at ${(System.currentTimeMillis() - startTime) / 1000}s"
                    )
                    val status = checkTransactionStatus(traceNo)
                    if (status == "1") {
                        Log.d(
                            TAG,
                            "Step 4: Transaction successful at ${(System.currentTimeMillis() - startTime) / 1000}s"
                        )
                        handlePaymentSuccess(traceNo)
                        return@launch
                    }
                    delay(3000L)
                }

                // Step 5: Call initDuitNowQueryPaymentChecking once at 73s
                if (!isActive) {
                    Log.d(TAG, "Coroutine cancelled before step 5")
                    return@launch
                }
                Log.d(
                    TAG,
                    "Step 5: Initiating query payment checking at ${(System.currentTimeMillis() - startTime) / 1000}s"
                )
                when (val result = initDuitNowQueryPaymentChecking(traceNo)) {
                    is Result.Success -> return@launch
                    is Result.Failure -> Log.d(
                        TAG,
                        "Step 5: Query failed, continuing: ${result.exception.localizedMessage}"
                    )
                }
                delay(3000L) // Delay to reach ~76s

                // Step 6: Check transaction status every 3 seconds for 5 times (76s to 91s)
                repeat(5) { attempt ->
                    if (!isActive) {
                        Log.d(TAG, "Coroutine cancelled during step 6, attempt $attempt")
                        return@launch
                    }
                    Log.d(
                        TAG,
                        "Step 6: Checking transaction status, attempt ${attempt + 1} at ${(System.currentTimeMillis() - startTime) / 1000}s"
                    )
                    val status = checkTransactionStatus(traceNo)
                    if (status == "1") {
                        Log.d(
                            TAG,
                            "Step 6: Transaction successful at ${(System.currentTimeMillis() - startTime) / 1000}s"
                        )
                        handlePaymentSuccess(traceNo)
                        return@launch
                    }
                    delay(3000L)
                }

                // Step 7: Call initDuitNowQueryPaymentChecking once at 91s
                if (!isActive) {
                    Log.d(TAG, "Coroutine cancelled before step 7")
                    return@launch
                }
                Log.d(
                    TAG,
                    "Step 7: Initiating query payment checking at ${(System.currentTimeMillis() - startTime) / 1000}s"
                )
                when (val result = initDuitNowQueryPaymentChecking(traceNo)) {
                    is Result.Success -> return@launch
                    is Result.Failure -> Log.d(
                        TAG,
                        "Step 7: Query failed, continuing: ${result.exception.localizedMessage}"
                    )
                }
                delay(3000L) // Delay to reach ~94s

                // Step 8: Check transaction status every 3 seconds for 5 times (94s to 106s)
                repeat(5) { attempt ->
                    if (!isActive) {
                        Log.d(TAG, "Coroutine cancelled during step 8, attempt $attempt")
                        return@launch
                    }
                    Log.d(
                        TAG,
                        "Step 8: Checking transaction status, attempt ${attempt + 1} at ${(System.currentTimeMillis() - startTime) / 1000}s"
                    )
                    val status = checkTransactionStatus(traceNo)
                    if (status == "1") {
                        Log.d(
                            TAG,
                            "Step 8: Transaction successful at ${(System.currentTimeMillis() - startTime) / 1000}s"
                        )
                        handlePaymentSuccess(traceNo)
                        return@launch
                    }
                    delay(3000L)
                }

                // Step 9: Call initDuitNowQueryPaymentChecking 4 times at 109s, 112s, 115s, 118s
                repeat(4) { attempt ->
                    if (!isActive) {
                        Log.d(TAG, "Coroutine cancelled during step 9, attempt $attempt")
                        return@launch
                    }
                    Log.d(
                        TAG,
                        "Step 9: Initiating query payment checking, attempt ${attempt + 1} at ${(System.currentTimeMillis() - startTime) / 1000}s"
                    )
                    when (val result = initDuitNowQueryPaymentChecking(traceNo)) {
                        is Result.Success -> return@launch
                        is Result.Failure -> Log.d(
                            TAG,
                            "Step 9: Query failed, continuing: ${result.exception.localizedMessage}"
                        )
                    }
                    delay(3000L)
                }

                // Step 10: Check if 120 seconds have passed and handle timeout
                if (!isActive || paymentAlreadyMadeAndSuccess) {
                    Log.d(
                        TAG,
                        "Exiting at ${(System.currentTimeMillis() - startTime) / 1000}s: ${if (paymentAlreadyMadeAndSuccess) "Success" else "Coroutine cancelled"}"
                    )
                    return@launch
                }
                val finalTime = (System.currentTimeMillis() - startTime) / 1000
                Log.d(TAG, "Step 10: Timeout reached at ${finalTime}s, transaction failed")
                callback.onLoggingEverything("Step 10: Timeout reached at ${finalTime}s, transaction failed")
                logTempTransaction(0, "Transaction failed, exceeded 120 seconds")
                showTransactionFailedDialog()
            } catch (e: CancellationException) {
                Log.d(
                    TAG,
                    "Payment status check cancelled at ${(System.currentTimeMillis() - startTime) / 1000}s",
                    e
                )
                callback.onLoggingEverything("Payment status check cancelled: ${e.localizedMessage}")
                throw e
            } catch (e: Exception) {
                Log.e(
                    TAG,
                    "Unexpected error in payment status check at ${(System.currentTimeMillis() - startTime) / 1000}s",
                    e
                )
                callback.onLoggingEverything("ERROR: Unexpected error in payment status check: ${e.localizedMessage}")
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
                    request.retryPolicy = DefaultRetryPolicy(5000, 1, 1.0f) // 5s timeout, 1 retry
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
        paymentCheckJob?.cancel() // Cancel the payment check job
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
            Log.d(TAG, "Dismissing dialog and cancelling payment check")
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