package com.pratham.sentinelxstore

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.text.HtmlCompat
import androidx.lifecycle.lifecycleScope
import com.pratham.sentinelxstore.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.web3j.abi.FunctionEncoder
import org.web3j.abi.FunctionReturnDecoder
import org.web3j.abi.TypeReference
import org.web3j.abi.datatypes.Utf8String
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.DefaultBlockParameterName
import org.web3j.protocol.core.methods.request.Transaction
import org.web3j.protocol.http.HttpService

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    // Configuration
    private val CONTRACT_ADDRESS = "0x6d5d41BbEb69924a23edc741477Af86bF2d872A2"
    private val RPC_URL = "https://ethereum-sepolia-rpc.publicnode.com"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)


        // Set initial footer text
        binding.txtContract.text = "Contract: ${CONTRACT_ADDRESS.substring(0, 10)}...\nNetwork: Ethereum Sepolia Testnet"

        binding.btnVerify.setOnClickListener {
            runPipeline()
        }
    }

    private fun runPipeline() {
        // UI: Loading State
        binding.btnVerify.isEnabled = false
        binding.btnVerify.text = "Connecting to Sepolia..."

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // 1. Connect to Blockchain
                val web3j = Web3j.build(HttpService(RPC_URL))

                // 2. Prepare Contract Call (getLatestRelease)
                // Solidity: function getLatestRelease() public view returns (string version, string cid)
                val function = org.web3j.abi.datatypes.Function(
                    "getLatestRelease",
                    listOf(), // Input parameters
                    listOf(
                        object : TypeReference<Utf8String>() {}, // Returns version
                        object : TypeReference<Utf8String>() {}  // Returns CID
                    )
                )

                val encodedFunction = FunctionEncoder.encode(function)

                // 3. Execute Call
                val response = web3j.ethCall(
                    Transaction.createEthCallTransaction(null, CONTRACT_ADDRESS, encodedFunction),
                    DefaultBlockParameterName.LATEST
                ).send()

                if (response.hasError()) {
                    throw Exception(response.error.message)
                }

                // 4. Decode Response
                val output = FunctionReturnDecoder.decode(response.value, function.outputParameters)
                val version = output[0].value as String
                val cid = output[1].value as String

                // 5. Success UI (Switch to Main Thread)
                withContext(Dispatchers.Main) {
                    showVerifiedState(version, cid)
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                    binding.btnVerify.isEnabled = true
                    binding.btnVerify.text = "Retry Verification"
                }
            }
        }
    }

    private fun showVerifiedState(version: String, cid: String) {
        // Hide Verify Button
        binding.btnVerify.visibility = View.GONE

        // Show Success Box
        binding.statusBox.visibility = View.VISIBLE
        val successHtml = "✅ <strong>VERIFIED</strong><br>Release: $version<br>IPFS: ${cid.substring(0, 12)}..."
        binding.statusText.text = HtmlCompat.fromHtml(successHtml, HtmlCompat.FROM_HTML_MODE_LEGACY)

        // Start Download Simulation
        binding.progressLayout.visibility = View.VISIBLE
        simulateDownload(cid)
    }

    private fun simulateDownload(cid: String) {
        lifecycleScope.launch {
            var progress = 0
            while (progress < 100) {
                // Random increment to look like real network traffic
                val increment = (Math.random() * 15).toInt()
                progress += increment
                if (progress > 100) progress = 100

                binding.progressBar.progress = progress
                binding.txtPercent.text = "$progress%"

                delay(400) // Speed of simulation
            }

            // Download Complete
            binding.progressLayout.visibility = View.GONE
            binding.btnInstall.visibility = View.VISIBLE

            // Set up real download link
            binding.btnInstall.setOnClickListener {
                val url = "https://gateway.pinata.cloud/ipfs/$cid"
                val intent = Intent(Intent.ACTION_VIEW)
                intent.data = Uri.parse(url)
                startActivity(intent)
            }
        }
    }
}