package com.pratham.sentinelxstore

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.text.HtmlCompat
import androidx.lifecycle.lifecycleScope
import com.pratham.sentinelxstore.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.web3j.abi.FunctionEncoder
import org.web3j.abi.FunctionReturnDecoder
import org.web3j.abi.TypeReference
import org.web3j.abi.datatypes.Utf8String
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.DefaultBlockParameterName
import org.web3j.protocol.core.methods.request.Transaction
import org.web3j.protocol.http.HttpService
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.text.DecimalFormat

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val CONTRACT_ADDRESS = "0x41418ec13E70Ee833679cf0ad7e0ed53e6470C7e"
    private val RPC_URL = "https://ethereum-sepolia-rpc.publicnode.com"

    private var downloadedApkFile: File? = null
    private var verifiedChecksumFromChain: String? = null

    companion object {
        private const val REQUEST_INSTALL_PERMISSION = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        enableEdgeToEdge()

        binding.txtContract.text = "Contract: ${CONTRACT_ADDRESS.substring(0, 10)}...\nNetwork: Sepolia Testnet"

        binding.btnVerify.setOnClickListener { runPipeline() }

        binding.btnInstall.setOnClickListener {
            downloadedApkFile?.let { file -> checkPermissionAndInstall(file) }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_INSTALL_PERMISSION) {
            downloadedApkFile?.let { installApk(it) }
        }
    }

    // --- STEP 1: FETCH METADATA & CHECKSUM FROM CHAIN ---
    private fun runPipeline() {
        binding.btnVerify.isEnabled = false
        binding.btnVerify.alpha = 0.7f
        binding.btnVerify.text = "Verifying on Blockchain..."

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val web3j = Web3j.build(HttpService(RPC_URL))

                // UPDATED ABI: Returns (version, cid, checksum)
                val function = org.web3j.abi.datatypes.Function(
                    "getLatestRelease",
                    listOf(),
                    listOf(
                        object : TypeReference<Utf8String>() {}, // version
                        object : TypeReference<Utf8String>() {}, // cid
                        object : TypeReference<Utf8String>() {}  // checksum (NEW)
                    )
                )
                val encodedFunction = FunctionEncoder.encode(function)

                val response = web3j.ethCall(
                    Transaction.createEthCallTransaction(null, CONTRACT_ADDRESS, encodedFunction),
                    DefaultBlockParameterName.LATEST
                ).send()

                if (response.hasError()) throw Exception(response.error.message)

                val output = FunctionReturnDecoder.decode(response.value, function.outputParameters)

                // Parse 3 return values now
                val version = output[0].value as String
                val cid = output[1].value as String
                val checksum = output[2].value as String

                // Save the trusted checksum for later verification
                verifiedChecksumFromChain = checksum

                withContext(Dispatchers.Main) {
                    showVerifiedState(version, cid)
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    val errorMsg = if (e.message?.contains("empty") == true) "Contract not found" else e.message
                    Toast.makeText(this@MainActivity, "Error: $errorMsg", Toast.LENGTH_LONG).show()
                    binding.btnVerify.isEnabled = true
                    binding.btnVerify.alpha = 1.0f
                    binding.btnVerify.text = "Retry Verification"
                }
            }
        }
    }

    private fun showVerifiedState(version: String, cid: String) {
        binding.btnVerify.visibility = View.GONE
        binding.statusBox.visibility = View.VISIBLE
        binding.statusText.text = HtmlCompat.fromHtml("✅ <strong>VERIFIED</strong><br>Release: $version", HtmlCompat.FROM_HTML_MODE_LEGACY)
        binding.progressLayout.visibility = View.VISIBLE

        binding.progressBar.isIndeterminate = true
        downloadApkInternal(cid)
    }

    // --- STEP 2: DOWNLOAD FILE ---
    private fun downloadApkInternal(cid: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val url = "https://gateway.pinata.cloud/ipfs/$cid"
                val client = OkHttpClient()
                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()

                if (!response.isSuccessful) throw Exception("Download Failed: ${response.code}")

                val body = response.body ?: throw Exception("Empty Body")
                val totalSize = body.contentLength()

                val file = File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "SentinelX_Update.apk")
                val output = FileOutputStream(file)
                val input = body.byteStream()

                val buffer = ByteArray(8 * 1024)
                var bytesRead: Int
                var downloadedSize: Long = 0
                val df = DecimalFormat("0.0")

                withContext(Dispatchers.Main) {
                    if (totalSize > 0) {
                        binding.progressBar.isIndeterminate = false
                        binding.progressBar.max = 100
                    }
                }

                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    downloadedSize += bytesRead

                    withContext(Dispatchers.Main) {
                        if (totalSize > 0) {
                            val percent = ((downloadedSize * 100) / totalSize).toInt()
                            binding.progressBar.setProgressCompat(percent, true)
                            binding.txtPercent.text = "$percent%"
                            val currentMB = df.format(downloadedSize / 1024f / 1024f)
                            val totalMB = df.format(totalSize / 1024f / 1024f)
                            binding.txtProgressSize.text = "$currentMB MB / $totalMB MB"
                        } else {
                            val currentMB = df.format(downloadedSize / 1024f / 1024f)
                            binding.txtProgressSize.text = "$currentMB MB"
                        }
                    }
                }

                output.flush()
                output.close()
                input.close()

                // --- STEP 3: PERFORM INTEGRITY CHECK ---
                withContext(Dispatchers.Main) {
                    binding.txtProgressSize.text = "Verifying Digital Signature..."
                    binding.progressBar.isIndeterminate = true
                }

                val isValid = verifyFileIntegrity(file, verifiedChecksumFromChain)

                withContext(Dispatchers.Main) {
                    binding.progressBar.isIndeterminate = false
                    if (isValid) {
                        // SUCCESS: File matches Blockchain Record
                        downloadedApkFile = file
                        binding.progressLayout.visibility = View.GONE
                        binding.btnInstall.visibility = View.VISIBLE
                        binding.statusText.text = HtmlCompat.fromHtml("✅ <strong>VERIFIED & SECURE</strong><br>Integrity Check Passed", HtmlCompat.FROM_HTML_MODE_LEGACY)

                        // Proceed to Install
                        checkPermissionAndInstall(file)
                    } else {
                        // CRITICAL FAILURE: File Tampered
                        file.delete() // Security Kill Switch
                        binding.progressLayout.visibility = View.GONE
                        binding.statusBox.setBackgroundResource(android.R.color.holo_red_dark)
                        binding.statusText.setTextColor(resources.getColor(android.R.color.white, null))
                        binding.statusText.text = "⚠️ SECURITY ALERT: CHECKSUM MISMATCH\nThe downloaded file has been modified. Installation aborted."
                        Toast.makeText(this@MainActivity, "Security Alert: APK Checksum Mismatch!", Toast.LENGTH_LONG).show()
                    }
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // --- HELPER: HASH CALCULATION ---
    private fun verifyFileIntegrity(file: File, trustedChecksum: String?): Boolean {
        if (trustedChecksum.isNullOrEmpty()) return false

        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val fis = FileInputStream(file)
            val buffer = ByteArray(8192) // 8KB buffer for hashing
            var bytesRead: Int

            while (fis.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
            fis.close()

            // Convert byte array to Hex String
            val calculatedHash = digest.digest().joinToString("") { "%02x".format(it) }

            // Normalize both to lowercase and remove "0x" if present for comparison
            val cleanTrusted = trustedChecksum.lowercase().removePrefix("0x")
            val cleanCalculated = calculatedHash.lowercase()

            println("Blockchain Hash: $cleanTrusted")
            println("Local File Hash: $cleanCalculated")

            cleanTrusted == cleanCalculated
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun checkPermissionAndInstall(file: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!packageManager.canRequestPackageInstalls()) {
                Toast.makeText(this, "Please allow installation from Settings", Toast.LENGTH_LONG).show()
                val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                intent.data = Uri.parse("package:$packageName")
                @Suppress("DEPRECATION")
                startActivityForResult(intent, REQUEST_INSTALL_PERMISSION)
                return
            }
        }
        installApk(file)
    }

    private fun installApk(file: File) {
        try {
            val apkUri = FileProvider.getUriForFile(
                this,
                "${applicationContext.packageName}.provider",
                file
            )

            val intent = Intent(Intent.ACTION_VIEW)
            intent.setDataAndType(apkUri, "application/vnd.android.package-archive")
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Install Failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}