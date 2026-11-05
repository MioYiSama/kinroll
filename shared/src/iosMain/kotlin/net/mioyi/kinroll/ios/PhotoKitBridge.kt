package net.mioyi.kinroll.ios

import net.mioyi.kinroll.platform.PhotoAsset
import net.mioyi.kinroll.platform.PhotoDetails
import platform.Foundation.NSData

interface PhotoKitBridge {
    fun authorizationStatus(): Int
    fun requestAuthorization(completion: (Int) -> Unit)
    fun fetchAssets(): List<PhotoAsset>
    fun readDetails(id: String): PhotoDetails?
    fun loadPixels(id: String, maxSide: Int): BridgePixels?
    fun deleteAssets(ids: List<String>, completion: (Boolean) -> Unit)
}
class BridgePixels(val width: Int, val height: Int, val rgba: NSData)
