package com.pratham.sentinelxstore

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.View
import android.widget.Toast
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
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    // Configuration
    private val CONTRACT_ADDRESS = "0x6d5d41BbEb69924a23edc741477Af86bF2d872A2"
    private val RPC_URL = "https://ethereum-sepolia-rpc.publicnode.com"
    private var downloadedApkFile: File? = null

    companion object {
        private const val REQUEST_INSTALL_PERMISSION = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

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

    private fun runPipeline() {
        binding.btnVerify.isEnabled = false
        binding.btnVerify.text = "Verifying on Blockchain..."

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // 1. Connect to Blockchain
                val web3j = Web3j.build(HttpService(RPC_URL))
                val function = org.web3j.abi.datatypes.Function(
                    "getLatestRelease",
                    listOf(),
                    listOf(object : TypeReference<Utf8String>() {}, object : TypeReference<Utf8String>() {})
                )
                val encodedFunction = FunctionEncoder.encode(function)

                val response = web3j.ethCall(
                    Transaction.createEthCallTransaction(null, CONTRACT_ADDRESS, encodedFunction),
                    DefaultBlockParameterName.LATEST
                ).send()

                if (response.hasError()) throw Exception(response.error.message)

                val output = FunctionReturnDecoder.decode(response.value, function.outputParameters)
                val version = output[0].value as String
                val cid = output[1].value as String

                withContext(Dispatchers.Main) {
                    showVerifiedState(version, cid)
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    val errorMsg = if (e.message?.contains("empty") == true) "Contract not found/Empty response" else e.message
                    Toast.makeText(this@MainActivity, "Error: $errorMsg", Toast.LENGTH_LONG).show()
                    binding.btnVerify.isEnabled = true
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
        downloadApkInternal(cid)
    }

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

                val buffer = ByteArray(4096)
                var bytesRead: Int
                var downloadedSize: Long = 0

                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    downloadedSize += bytesRead

                    if (totalSize > 0) {
                        val percent = ((downloadedSize * 100) / totalSize).toInt()
                        withContext(Dispatchers.Main) {
                            binding.progressBar.progress = percent
                            binding.txtPercent.text = "$percent%"
                        }
                    }
                }

                output.flush()
                output.close()
                input.close()

                downloadedApkFile = file
                withContext(Dispatchers.Main) {
                    binding.progressLayout.visibility = View.GONE
                    binding.btnInstall.visibility = View.VISIBLE
                    binding.btnInstall.text = "Install Now"
                    checkPermissionAndInstall(file)
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Download Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
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