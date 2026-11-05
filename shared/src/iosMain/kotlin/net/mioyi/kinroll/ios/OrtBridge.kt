package net.mioyi.kinroll.ios

import platform.Foundation.NSData

interface OrtBridge {
    fun openSession(modelPath: String): OrtBridgeSession?
    fun lastError(): String?
}
interface OrtBridgeSession {
    fun inputNames(): List<String>
    fun outputNames(): List<String>
    fun run(names: List<String>, datas: List<NSData>, shapes: List<List<Long>>, types: List<Int>, outputNames: List<String>): List<OrtBridgeOutput>?
    fun close()
}
class OrtBridgeOutput(val data: NSData, val shape: List<Long>)
